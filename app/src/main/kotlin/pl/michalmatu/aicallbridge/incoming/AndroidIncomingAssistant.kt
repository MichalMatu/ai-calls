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
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder
import pl.michalmatu.aicallbridge.textagent.*

/** Adapts exact incoming Call objects to the shared runtime. Never disconnects a Call. */
internal class AndroidIncomingAssistant(
    private val context: Context,
    private val calls: () -> List<Call>,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val suspended = AtomicBoolean(false)
    private val preparations = IdentityHashMap<Call, ConversationRunConfiguration.ModelDirect>()
    private val store by lazy { IncomingAssistantPolicyStore(context) }
    private class AudioSlot(val call: Call, val generation: Long) {
        val cancelled=AtomicBoolean(false)
        val audio=AtomicReference<AndroidConversationRuntime.SessionControl?>(null)
    }
    private val slots=ConcurrentHashMap<Long, AudioSlot>()
    @Volatile var status: String = "MANUAL"
        private set
    private val coordinator=IncomingAssistantCoordinator(object : IncomingAssistantPort<Call> {
        override fun readinessFailure(call: Call): String? = this@AndroidIncomingAssistant.readinessFailure(call)
        override fun answer(call: Call) { call.answer(0) }
        override fun startAudio(call: Call, policy: IncomingAssistantPolicy, generation: Long) = prepare(call,policy,generation)
        override fun stopAudio(call: Call, generation: Long) {
            slots[generation]?.takeIf { it.call === call }?.let { slot ->
                slot.cancelled.set(true)
                slot.audio.getAndSet(null)?.stopAudio()
                slots.remove(generation,slot)
            }
        }
        override fun changed(call: Call, state: IncomingAssistantState, reason: String?) {
            status=state.name + (reason?.let { ":$it" } ?: "")
        }
    })

    fun observe(call: Call) {
        val state=call.state
        val incoming=state==Call.STATE_RINGING || call.details?.callDirection==Call.Details.DIRECTION_INCOMING || coordinator.snapshot(call)!=null
        if (!incoming) return
        val phase=when(state) {
            Call.STATE_RINGING -> IncomingCallPhase.RINGING
            Call.STATE_ACTIVE -> IncomingCallPhase.ACTIVE
            Call.STATE_HOLDING -> IncomingCallPhase.HELD
            Call.STATE_DISCONNECTED,Call.STATE_DISCONNECTING -> IncomingCallPhase.ENDED
            else -> return
        }
        coordinator.observe(call,phase,call.details?.accountHandle?.let(IncomingAssistantPolicyStore::accountKey),
            true,exclusive(call),loadPolicy())
    }
    fun removed(call: Call) { preparations.remove(call); coordinator.forget(call) }
    fun takeOver(call: Call) = coordinator.takeOver(call)
    fun resume(call: Call): Boolean = coordinator.resume(call,loadPolicy())
    fun snapshot(call: Call) = coordinator.snapshot(call)
    fun policyChanged() { suspended.set(false); coordinator.policyChanged(loadPolicy()) }
    fun stopAll() {
        suspended.set(true)
        coordinator.policyChanged(IncomingAssistantPolicy("stopped",AnsweringMode.MANUAL,null,false))
    }

    private fun loadPolicy(): IncomingAssistantPolicy = try {
        if (suspended.get()) IncomingAssistantPolicy("stopped",AnsweringMode.MANUAL,null,false) else store.load()
    } catch (_: Exception) {
        status="MANUAL:incoming_policy_unavailable"
        IncomingAssistantPolicy("unavailable",AnsweringMode.MANUAL,null,false)
    }
    private fun exclusive(call: Call): Boolean = calls().none { other ->
        other !== call && other.state in setOf(Call.STATE_ACTIVE,Call.STATE_HOLDING,Call.STATE_DIALING,Call.STATE_CONNECTING)
    }
    private fun authorized(slot: AudioSlot): Boolean = !closed.get() && !slot.cancelled.get() &&
        slot.call.state==Call.STATE_ACTIVE && exclusive(slot.call) &&
        coordinator.isAuthorized(slot.call,slot.generation) &&
        loadPolicy().let { it.mode==AnsweringMode.AI && it.accountKey==slot.call.details?.accountHandle?.let(IncomingAssistantPolicyStore::accountKey) }

    private fun readinessFailure(call: Call): String? {
        if (context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage!=context.packageName) return "dialer_role_missing"
        if (listOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_PHONE_STATE).any {
                context.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }) return "incoming_android_permission_missing"
        val readiness=AndroidLiveCallReadiness.evaluate(context)
        if (!readiness.ready) return readiness.failure?.reportValue ?: "incoming_media_not_ready"
        val profile=AudioEngineProfileStore(context).load() ?: return "audio_profile_invalid"
        AudioEngineRegistry.incompatibility(profile)?.let { return it }
        AudioEngineReadinessProbe.inputAndModelFailure(context, profile)?.let { return it }
        val preparation=AndroidConversationPreparationStore(context).load(ConversationPreparation.INCOMING_ID)
        preparations[call]=ConversationRunConfiguration.ModelDirect(profile,
            ConversationInstructionBuilder.build(preparation,IncomingSecretaryScenario.GOAL),preparation)
        return null
    }

    private fun prepare(call: Call, policy: IncomingAssistantPolicy, generation: Long) {
        val configuration=checkNotNull(preparations.remove(call)) { "incoming_preparation_missing" }
        val slot=AudioSlot(call,generation)
        slots[generation]=slot
        handler.post { waitForMedia(slot,policy,configuration) }
    }

    private fun waitForMedia(slot: AudioSlot, policy: IncomingAssistantPolicy, configuration: ConversationRunConfiguration.ModelDirect) {
        if (!authorized(slot)) { finish(slot,"incoming_authority_or_call_ended"); return }
        val mode=context.getSystemService(AudioManager::class.java)?.mode
        if (mode==AudioManager.MODE_IN_COMMUNICATION) { finish(slot,"wifi_calling_media_unsupported"); return }
        if (mode!=AudioManager.MODE_IN_CALL) {
            // Telecom ACTIVE and audio-mode callbacks can arrive in either order.
            // Wait only while the same actual Call and accepted policy remain active.
            handler.postDelayed({ waitForMedia(slot,policy,configuration) },250L)
            return
        }
        val recorder=if(policy.recordEvidence) CallSessionEvidenceRecorder.create(context) else null
        try {
            val audio=AndroidConversationRuntime.run(context,
                "incoming-${UUID.randomUUID()}",AndroidConversationRuntime.TASK_SCOPED_TURNS,
                IncomingSecretaryScenario(context,configuration),liveAuthorization={authorized(slot)},
                callStillActive={slot.call.state==Call.STATE_ACTIVE},evidenceRecorder=recorder,
                runConfiguration=configuration) { report ->
                val reason=report.lineSequence().firstOrNull { it.startsWith("failure_reason=") }?.substringAfter('=')
                finish(slot,reason)
            }
            slot.audio.set(audio)
            if (!authorized(slot)) slot.audio.getAndSet(null)?.stopAudio()
            else if (audio!=null) coordinator.audioStarted(slot.call,slot.generation)
        } catch (failure: Exception) {
            recorder?.finish("incoming_runtime_${failure.javaClass.simpleName}")
            finish(slot,"incoming_runtime_${failure.javaClass.simpleName}")
        }
    }
    private fun finish(slot: AudioSlot, reason: String?) {
        slot.cancelled.set(true)
        slot.audio.getAndSet(null)?.stopAudio()
        slots.remove(slot.generation,slot)
        coordinator.audioFinished(slot.call,slot.generation,reason)
    }
    override fun close() {
        closed.set(true)
        coordinator.close()
        handler.removeCallbacksAndMessages(null)
        preparations.clear()
    }
}
