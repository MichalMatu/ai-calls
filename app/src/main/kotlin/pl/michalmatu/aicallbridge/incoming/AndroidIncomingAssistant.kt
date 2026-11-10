package pl.michalmatu.aicallbridge.incoming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.TelecomManager
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.AndroidLiveCallReadiness
import pl.michalmatu.aicallbridge.audioengines.*
import pl.michalmatu.aicallbridge.conversation.*
import pl.michalmatu.aicallbridge.conversationcore.*
import pl.michalmatu.aicallbridge.conversationtools.*
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder
import pl.michalmatu.aicallbridge.tools.*

/** Owns exact incoming Call objects; end_call can close only their current authorized AI generation. */
internal class AndroidIncomingAssistant(
    private val context: Context,
    private val calls: () -> List<Call>,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val suspended = AtomicBoolean(false)
    private data class Prepared(val run: ConversationRunConfiguration.ModelDirect, val tools: Set<ConversationToolId>)
    private val preparations = IdentityHashMap<Call, Prepared>()
    private val store by lazy { IncomingAssistantPolicyStore(context) }
    private val toolSettings by lazy { ConversationToolSettingsStore(context) }
    private class AudioSlot(val call: Call, val generation: Long, val policy: IncomingAssistantPolicy) {
        val notebookSessionId = "incoming-" + UUID.randomUUID().toString()
        val cancelled = AtomicBoolean(false)
        val audio = AtomicReference<AndroidConversationRuntime.SessionControl?>(null)
        var recorder: CallSessionEvidenceRecorder? = null
        var endToolTurn: Int? = null
        val toolFinalized = AtomicBoolean(false)
        val archiveGate = IncomingEndToolArchiveGate()
        val disconnectFence = IncomingEndToolDisconnectFence()
        var chatToken = 0L
        val pendingChatTools = mutableListOf<ConversationToolEvent>()
    }
    private val slots = ConcurrentHashMap<Long, AudioSlot>()
    @Volatile var status: String = "MANUAL"
        private set
    private val coordinator: IncomingAssistantCoordinator<Call> = IncomingAssistantCoordinator(object : IncomingAssistantPort<Call> {
        override fun readinessFailure(call: Call): String? = this@AndroidIncomingAssistant.readinessFailure(call)
        override fun answer(call: Call) { call.answer(0) }
        override fun startAudio(call: Call, policy: IncomingAssistantPolicy, generation: Long) = prepare(call, policy, generation)
        override fun stopAudio(call: Call, generation: Long, reason: ConversationStopReason) {
            slots[generation]?.takeIf { it.call === call }?.let { slot ->
                slot.cancelled.set(true)
                val audio = slot.audio.getAndSet(null)
                if (audio == null) {
                    // A runtime may have started before publishing SessionControl.
                    // Its completion callback owns final RX drain and archive END.
                    if (slot.archiveGate.runtimePending()) return@let
                    // If the runtime never started, there is no completion callback.
                    // Preserve a pending end_call on CALL_ENDED for resolveEndedCall().
                    if (slot.endToolTurn != null && reason == ConversationStopReason.CALL_ENDED) return@let
                    if (slot.endToolTurn != null) finalizeToolEvidence(slot, ConversationToolStage.UNKNOWN)
                    else slot.recorder?.finish(reason.code)
                    slots.remove(generation, slot)
                } else {
                    audio.stopAudio(reason)
                    if (slot.endToolTurn == null) slots.remove(generation, slot)
                }
            }
        }
        override fun disconnect(call: Call) {
            val policy = loadPolicy()
            val snapshot: IncomingAssistantSnapshot? = this@AndroidIncomingAssistant.snapshot(call)
            val slot = snapshot?.let { slots[it.generation] }
            check(!closed.get() && calls().any { it === call } &&
                call.state == Call.STATE_ACTIVE && exclusive(call))
            check(slot != null && IncomingEndCallAuthority.permits(
                state = snapshot.state,
                endingGeneration = snapshot.generation,
                slotGeneration = slot.generation,
                slotStillOwned = slot.call === call,
                slotCancelled = slot.cancelled.get(),
                endToolRequested = slot.endToolTurn != null,
                slotPolicy = slot.policy,
                currentPolicy = policy,
            ))
            check(policy.accountKey == call.details?.accountHandle?.let(IncomingAssistantPolicyStore::accountKey))
            // Revalidate against preferences immediately before irreversible Telecom action.
            check(loadPolicy() == slot.policy)
            call.disconnect()
        }
        override fun changed(call: Call, state: IncomingAssistantState, reason: String?) {
            status = state.name + (reason?.let { ":$it" } ?: "")
        }
    })

    fun observe(call: Call) {
        val state = call.state
        val incoming = state == Call.STATE_RINGING || call.details?.callDirection == Call.Details.DIRECTION_INCOMING || coordinator.snapshot(call) != null
        if (!incoming) return
        val phase = when (state) {
            Call.STATE_RINGING -> IncomingCallPhase.RINGING
            Call.STATE_ACTIVE -> IncomingCallPhase.ACTIVE
            Call.STATE_HOLDING -> IncomingCallPhase.HELD
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> IncomingCallPhase.ENDED
            else -> return
        }
        // Capture the owner before observe(ENDED) revokes it. A disconnected Call
        // after human takeover is NOT proof that AI's earlier end_call succeeded.
        val endingAtDisconnect = coordinator.snapshot(call)?.state == IncomingAssistantState.ENDING
        if (state == Call.STATE_DISCONNECTING) {
            slots.values.filter { it.call === call && it.endToolTurn != null }.forEach {
                it.disconnectFence.noteDisconnecting(endingAtDisconnect)
            }
        }
        coordinator.observe(call, phase, call.details?.accountHandle?.let(IncomingAssistantPolicyStore::accountKey), true, exclusive(call), loadPolicy())
        if (state == Call.STATE_DISCONNECTED) resolveEndedCall(call, endingAtDisconnect)
    }
    private fun resolveEndedCall(call: Call, endingAtDisconnect: Boolean) {
        slots.values.filter { it.call === call && it.endToolTurn != null }.forEach { slot ->
            val disconnected = call.state == Call.STATE_DISCONNECTED
            val outcome = if (disconnected) IncomingEndToolEvidencePolicy.decide(
                true, slot.disconnectFence.ownedByEnding(endingAtDisconnect),
            ) else IncomingEndToolOutcome.UNKNOWN // removal before definitive disconnect
            // Do not finalize recorder while a live Run is still draining RX WAV.
            slot.archiveGate.onTelecomEnd(outcome)?.let { settled ->
                finalizeToolEvidence(slot, if (settled == IncomingEndToolOutcome.CONFIRMED)
                    ConversationToolStage.CONFIRMED else ConversationToolStage.UNKNOWN)
                slots.remove(slot.generation, slot)
            }
        }
    }
    fun removed(call: Call) {
        val ending = coordinator.snapshot(call)?.state == IncomingAssistantState.ENDING
        resolveEndedCall(call, ending)
        preparations.remove(call)
        coordinator.forget(call)
    }
    fun takeOver(call: Call) = coordinator.takeOver(call)
    fun resume(call: Call): Boolean = coordinator.resume(call, loadPolicy())
    fun snapshot(call: Call): IncomingAssistantSnapshot? = coordinator.snapshot(call)
    fun policyChanged() { suspended.set(false); coordinator.policyChanged(loadPolicy(), ConversationStopReason.POLICY_REVOKED) }
    fun stopAll() {
        suspended.set(true)
        coordinator.policyChanged(IncomingAssistantPolicy("stopped", AnsweringMode.MANUAL, null, false), ConversationStopReason.OPERATOR_STOP)
    }
    private fun loadPolicy(): IncomingAssistantPolicy = try {
        if (suspended.get()) IncomingAssistantPolicy("stopped", AnsweringMode.MANUAL, null, false) else store.load()
    } catch (_: Exception) {
        status = "MANUAL:incoming_policy_unavailable"
        IncomingAssistantPolicy("unavailable", AnsweringMode.MANUAL, null, false)
    }
    private fun exclusive(call: Call): Boolean = calls().none { other ->
        other !== call && other.state in setOf(Call.STATE_ACTIVE, Call.STATE_HOLDING, Call.STATE_DIALING, Call.STATE_CONNECTING)
    }
    private fun authorized(slot: AudioSlot): Boolean = !closed.get() && !slot.cancelled.get() &&
        slot.call.state == Call.STATE_ACTIVE && exclusive(slot.call) && coordinator.isAuthorized(slot.call, slot.generation) &&
        loadPolicy().let { it == slot.policy && it.mode == AnsweringMode.AI &&
            it.accountKey == slot.call.details?.accountHandle?.let(IncomingAssistantPolicyStore::accountKey) }

    private fun readinessFailure(call: Call): String? {
        if (context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage != context.packageName) return "dialer_role_missing"
        if (listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE).any {
                context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) return "incoming_android_permission_missing"
        val readiness = AndroidLiveCallReadiness.evaluate(context)
        if (!readiness.ready) return readiness.failure?.reportValue ?: "incoming_media_not_ready"
        val profile = AudioEngineProfileStore(context).load() ?: return "audio_profile_invalid"
        AudioEngineRegistry.incompatibility(profile)?.let { return it }
        AudioEngineReadinessProbe.inputAndModelFailure(context, profile)?.let { return it }
        val preparation = AndroidConversationPreparationStore(context).load(ConversationPreparation.INCOMING_ID)
        preparations[call] = Prepared(ConversationRunConfiguration.ModelDirect(profile,
            ConversationInstructionBuilder.build(preparation, IncomingSecretaryScenario.GOAL, ConversationResponseFormat.TOOL_REPLY), preparation), toolSettings.enabled())
        return null
    }
    private fun prepare(call: Call, policy: IncomingAssistantPolicy, generation: Long) {
        val prepared = checkNotNull(preparations.remove(call)) { "incoming_preparation_missing" }
        val slot = AudioSlot(call, generation, policy)
        slots[generation] = slot
        handler.post { waitForMedia(slot, policy, prepared) }
    }
    private fun waitForMedia(slot: AudioSlot, policy: IncomingAssistantPolicy, prepared: Prepared) {
        if (!authorized(slot)) {
            abandon(slot, if (slot.call.state != Call.STATE_ACTIVE) ConversationStopReason.CALL_ENDED else ConversationStopReason.POLICY_REVOKED)
            return
        }
        val mode = context.getSystemService(AudioManager::class.java)?.mode
        if (mode == AudioManager.MODE_IN_COMMUNICATION) { failBeforeRuntime(slot, "wifi_calling_media_unsupported"); return }
        if (mode != AudioManager.MODE_IN_CALL) {
            handler.postDelayed({ waitForMedia(slot, policy, prepared) }, 250L)
            return
        }
        slot.recorder = if (policy.recordEvidence) CallSessionEvidenceRecorder.create(context) else null
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = tool in toolSettings.enabled()
            override fun authorized(tool: ConversationToolId) = this@AndroidIncomingAssistant.authorized(slot)
            override fun requestEndCall(turn: Int): Boolean {
                slot.endToolTurn = turn
                val accepted = coordinator.completeConversation(slot.call, slot.generation, loadPolicy())
                if (!accepted) slot.endToolTurn = null
                return accepted
            }
            override fun saveNote(turn: Int, proposal: ConversationNoteProposal): Boolean {
                if (!authorized(slot)) return false
                return AndroidConversationPreparationStore(context).appendNote(
                    slot.notebookSessionId, "INCOMING", turn, proposal)
            }
        })
        try {
            slot.archiveGate.onRuntimeStart()
            val audio = AndroidConversationRuntime.run(context, slot.notebookSessionId, AndroidConversationRuntime.TASK_SCOPED_TURNS,
                IncomingSecretaryScenario(context, prepared.run, tools, prepared.tools), liveAuthorization = { authorized(slot) },
                callStillActive = { slot.call.state == Call.STATE_ACTIVE }, evidenceRecorder = slot.recorder,
                runConfiguration = prepared.run) { result -> finished(slot, result) }
            slot.audio.set(audio)
            if (!authorized(slot)) slot.audio.getAndSet(null)?.stopAudio(
                if (slot.call.state != Call.STATE_ACTIVE) ConversationStopReason.CALL_ENDED else ConversationStopReason.POLICY_REVOKED)
            else if (audio != null) coordinator.audioStarted(slot.call, slot.generation)
        } catch (failure: Exception) { failBeforeRuntime(slot, "incoming_runtime_${failure.javaClass.simpleName}") }
    }
    private fun finished(slot: AudioSlot, result: ConversationRunResult) {
        slot.cancelled.set(true)
        slot.audio.set(null)
        slot.chatToken = result.chatToken
        slot.pendingChatTools.forEach { ConversationChatFeed.tool(slot.chatToken, it) }
        slot.pendingChatTools.clear()
        val terminalAfterDrain = slot.archiveGate.onRuntimeFinished()
        if (terminalAfterDrain != null && slot.endToolTurn != null) {
            finalizeToolEvidence(slot, if (terminalAfterDrain == IncomingEndToolOutcome.CONFIRMED)
                ConversationToolStage.CONFIRMED else ConversationToolStage.UNKNOWN)
            slots.remove(slot.generation, slot)
        } else if (result.evidenceAwaitingTool && slot.endToolTurn != null) {
            val ownerStillEnding = slot.disconnectFence.ownedByEnding(
                coordinator.snapshot(slot.call)?.state == IncomingAssistantState.ENDING,
            )
            when (IncomingEndToolEvidencePolicy.decide(
                slot.call.state == Call.STATE_DISCONNECTED, ownerStillEnding,
            )) {
                IncomingEndToolOutcome.CONFIRMED -> {
                    finalizeToolEvidence(slot, ConversationToolStage.CONFIRMED)
                    slots.remove(slot.generation, slot)
                }
                IncomingEndToolOutcome.UNKNOWN -> {
                    finalizeToolEvidence(slot, ConversationToolStage.UNKNOWN)
                    slots.remove(slot.generation, slot)
                }
                IncomingEndToolOutcome.PENDING -> Unit // Await exact Telecom lifecycle.
            }
        } else {
            slot.recorder?.finish(result.termination.code)
            slots.remove(slot.generation, slot)
        }
        coordinator.audioFinished(slot.call, slot.generation, result.termination.code)
    }
    private fun finalizeToolEvidence(slot: AudioSlot, stage: ConversationToolStage) {
        val turn = slot.endToolTurn ?: return
        if (!slot.toolFinalized.compareAndSet(false, true)) return
        val event = ConversationToolEvent(turn, ConversationToolId.END_CALL, stage, null)
        slot.recorder?.recordToolEvent(event)
        ConversationToolUi.record(event)
        if (slot.chatToken != 0L) ConversationChatFeed.tool(slot.chatToken, event) else slot.pendingChatTools += event
        slot.recorder?.finish(if (stage == ConversationToolStage.CONFIRMED) "owned_call_ended" else "tool_result_unknown")
    }
    private fun abandon(slot: AudioSlot, reason: ConversationStopReason) {
        slot.cancelled.set(true)
        val audio = slot.audio.getAndSet(null)
        if (audio == null) {
            // TAKE OVER while waiting for MODE_IN_CALL can leave a private recorder
            // allocated, but no Run will ever call finish() for that recorder.
            slot.recorder?.finish(reason.code)
        } else {
            audio.stopAudio(reason)
        }
        coordinator.audioFinished(slot.call, slot.generation, reason.code)
        slots.remove(slot.generation, slot)
    }
    private fun failBeforeRuntime(slot: AudioSlot, code: String) {
        slot.cancelled.set(true)
        slot.audio.getAndSet(null)?.stopAudio(ConversationStopReason.SESSION_CANCELLED)
        slot.recorder?.finish(code)
        coordinator.audioFinished(slot.call, slot.generation, code)
        slots.remove(slot.generation, slot)
        status = "MANUAL:$code"
    }
    override fun close() {
        closed.set(true)
        coordinator.close()
        // Service teardown is not proof that END_CALL succeeded. Do not write
        // archive END/COMPLETE until the runtime has drained the last RX window.
        slots.values.toList().forEach { slot ->
            if (slot.endToolTurn != null) {
                slot.archiveGate.onTelecomEnd(IncomingEndToolOutcome.UNKNOWN)?.let { outcome ->
                    finalizeToolEvidence(slot, if (outcome == IncomingEndToolOutcome.CONFIRMED)
                        ConversationToolStage.CONFIRMED else ConversationToolStage.UNKNOWN)
                    slots.remove(slot.generation, slot)
                }
            } else if (!slot.archiveGate.runtimePending()) {
                slot.recorder?.finish(ConversationStopReason.SERVICE_DESTROYED.code)
                slots.remove(slot.generation, slot)
            }
            // Otherwise finished(slot) still owns the pending recorder and callback.
        }
        handler.removeCallbacksAndMessages(null)
        preparations.clear()
    }
}
