package pl.michalmatu.aicallbridge.conversationcore

import java.util.IdentityHashMap

enum class AnsweringMode { MANUAL, AI }
enum class IncomingCallPhase { RINGING, ACTIVE, HELD, ENDED }
enum class IncomingAssistantState { RINGING, ANSWERING, STARTING, AI, ENDING, MANUAL, ENDED }

data class IncomingAssistantPolicy(
    val id: String,
    val mode: AnsweringMode,
    val accountKey: String?,
    val recordEvidence: Boolean,
) {
    init {
        require(id.isNotBlank()) { "incoming_policy_id_missing" }
        require(mode != AnsweringMode.AI || !accountKey.isNullOrBlank()) { "incoming_account_scope_missing" }
    }
    override fun toString() = "IncomingAssistantPolicy(id=$id, mode=$mode, scope=REDACTED)"
}

data class IncomingAssistantSnapshot(val state: IncomingAssistantState, val generation: Long, val reason: String?)

interface IncomingAssistantPort<C : Any> {
    fun readinessFailure(call: C): String?
    fun answer(call: C)
    fun startAudio(call: C, policy: IncomingAssistantPolicy, generation: Long)
    fun stopAudio(call: C, generation: Long, reason: ConversationStopReason)
    fun disconnect(call: C)
    fun changed(call: C, state: IncomingAssistantState, reason: String?)
}

/** Serialized by its owner (Android uses the main looper). No Telecom, model or audio dependencies. */
class IncomingAssistantCoordinator<C : Any>(private val port: IncomingAssistantPort<C>) {
    private class Entry(val initialPolicy: IncomingAssistantPolicy, var phase: IncomingCallPhase) {
        var policy = initialPolicy
        var observedPolicy = initialPolicy
        var account: String? = null
        var exclusive = true
        var state = if (phase == IncomingCallPhase.RINGING && initialPolicy.mode == AnsweringMode.AI)
            IncomingAssistantState.RINGING else IncomingAssistantState.MANUAL
        var generation = 0L
        var reason: String? = null
        var revision = 0L
    }
    private val entries = IdentityHashMap<C, Entry>()
    private var sequence = 0L

    @Synchronized fun observe(call: C, phase: IncomingCallPhase, accountKey: String?, incoming: Boolean,
                exclusive: Boolean, policy: IncomingAssistantPolicy) {
        if (!incoming) return
        val entry = entries[call] ?: Entry(policy, phase).also { entries[call] = it }
        // Readiness/authorization ports may reenter observe without a state transition.
        // Treat authority changes as a new revision even while this Call remains MANUAL.
        if (entry.phase != phase || entry.account != accountKey ||
            entry.exclusive != exclusive || entry.observedPolicy != policy) {
            entry.revision++
        }
        entry.phase = phase
        entry.account = accountKey
        entry.exclusive = exclusive
        entry.observedPolicy = policy
        when (phase) {
            IncomingCallPhase.ENDED -> { manual(call, entry, "call_ended", ConversationStopReason.CALL_ENDED, IncomingAssistantState.ENDED); return }
            IncomingCallPhase.HELD -> { manual(call, entry, "call_held", ConversationStopReason.CALL_HELD); return }
            else -> Unit
        }
        if (ownsAudio(entry) && policy != entry.policy) {
            manual(call, entry, "incoming_policy_changed", ConversationStopReason.POLICY_REVOKED); return
        }
        if (ownsAudio(entry) && entry.account != entry.policy.accountKey) {
            manual(call, entry, "incoming_account_changed", ConversationStopReason.OWNERSHIP_CHANGED); return
        }
        if (ownsAudio(entry) && !entry.exclusive) {
            manual(call, entry, "another_call_active", ConversationStopReason.OWNERSHIP_CHANGED); return
        }
        if (entry.state == IncomingAssistantState.RINGING && phase == IncomingCallPhase.RINGING) {
            if (accountKey == null) return // Telecom may publish the account after onCallAdded.
            if (policy.mode != AnsweringMode.AI || entry.initialPolicy != policy) {
                manual(call, entry, "incoming_policy_changed", ConversationStopReason.POLICY_REVOKED); return
            }
            claim(call, entry, policy)
        } else if (entry.state == IncomingAssistantState.ANSWERING && phase == IncomingCallPhase.ACTIVE) {
            start(call, entry)
        } else if (entry.state == IncomingAssistantState.RINGING && phase == IncomingCallPhase.ACTIVE) {
            manual(call, entry, "answered_manually", ConversationStopReason.HUMAN_TAKEOVER)
        }
    }

    /** Explicit human request; enabling a setting alone never seizes an already active call. */
    @Synchronized fun resume(call: C, policy: IncomingAssistantPolicy): Boolean {
        val entry = entries[call] ?: return false
        if (entry.state != IncomingAssistantState.MANUAL && entry.state != IncomingAssistantState.RINGING) return false
        if (entry.phase != IncomingCallPhase.ACTIVE && entry.phase != IncomingCallPhase.RINGING) return false
        return claim(call, entry, policy)
    }

    @Synchronized fun takeOver(call: C) {
        entries[call]?.takeUnless { it.state == IncomingAssistantState.ENDED }?.let {
            manual(call, it, "human_takeover", ConversationStopReason.HUMAN_TAKEOVER)
        }
    }

    @Synchronized fun policyChanged(policy: IncomingAssistantPolicy, reason: ConversationStopReason) {
        entries.entries.toList().forEach { (call, entry) ->
            if (entry.observedPolicy != policy) {
                entry.observedPolicy = policy
                entry.revision++
            }
            if ((ownsAudio(entry) || entry.state == IncomingAssistantState.RINGING || entry.state == IncomingAssistantState.ENDING) &&
                policy != entry.policy) {
                manual(call, entry, "incoming_policy_revoked", reason)
            }
        }
    }

    @Synchronized fun audioStarted(call: C, generation: Long) {
        val entry = entries[call] ?: return
        if (entry.generation == generation && entry.state == IncomingAssistantState.STARTING) {
            change(call, entry, IncomingAssistantState.AI, null)
        }
    }

    @Synchronized fun audioFinished(call: C, generation: Long, reason: String?) {
        val entry = entries[call] ?: return
        if (entry.generation == generation && ownsAudio(entry)) {
            change(call, entry, IncomingAssistantState.MANUAL, reason ?: "audio_finished")
        }
    }

    /** Called only after the final approved utterance has finished playback. */
    @Synchronized fun completeConversation(call: C, generation: Long, policy: IncomingAssistantPolicy): Boolean {
        val entry = entries[call] ?: return false
        fun mayClose() = entry.generation == generation && entry.phase == IncomingCallPhase.ACTIVE &&
            entry.exclusive && entry.account == entry.policy.accountKey &&
            policy == entry.policy && policy.mode == AnsweringMode.AI
        if (!ownsAudio(entry) || !mayClose()) return false
        change(call, entry, IncomingAssistantState.ENDING, "conversation_completed")
        // A reentrant STOP/takeover or Android callback can invalidate the proposal.
        if (entry.state != IncomingAssistantState.ENDING || !mayClose()) return false
        return try { port.disconnect(call); true } catch (_: Exception) {
            manual(call, entry, "incoming_disconnect_failed", ConversationStopReason.SESSION_CANCELLED)
            false
        }
    }

    @Synchronized fun isAuthorized(call: C, generation: Long): Boolean = entries[call]?.let {
        it.generation == generation && ownsAudio(it) &&
            it.exclusive && it.account == it.policy.accountKey && it.phase == IncomingCallPhase.ACTIVE
    } == true

    @Synchronized fun snapshot(call: C): IncomingAssistantSnapshot? = entries[call]?.let {
        IncomingAssistantSnapshot(it.state, it.generation, it.reason)
    }

    @Synchronized fun forget(call: C) {
        entries[call]?.let { manual(call, it, "call_removed", ConversationStopReason.CALL_ENDED, IncomingAssistantState.ENDED) }
        entries.remove(call)
    }

    @Synchronized fun close() {
        entries.entries.toList().forEach { (call, entry) ->
            manual(call, entry, "conversation_service_destroyed", ConversationStopReason.SERVICE_DESTROYED)
        }
        entries.clear()
    }

    private fun claim(call: C, entry: Entry, policy: IncomingAssistantPolicy): Boolean {
        // An old UI/Telecom resume request may arrive after policyChanged() revoked
        // its permission. Do not restore previous consent on the same SIM.
        if (entry.observedPolicy != policy) return false
        val beforeReadiness = entry.revision
        val failure = when {
            policy.mode != AnsweringMode.AI -> "incoming_manual_mode"
            entry.account != policy.accountKey -> "incoming_account_mismatch"
            !entry.exclusive || entries.any { (other, value) -> other !== call && ownsAudio(value) } -> "another_call_active"
            else -> try { port.readinessFailure(call) } catch (_: Exception) { "incoming_readiness_failed" }
        }
        // Readiness can reenter via an Android callback (STOP, manual answer, removal).
        // Never claim a call whose lifecycle changed while the adapter was checking it.
        if (entry.revision != beforeReadiness || entry.phase == IncomingCallPhase.ENDED ||
            entry.phase == IncomingCallPhase.HELD || entries[call] !== entry ||
            entry.observedPolicy != policy ||
            entry.account != policy.accountKey || !entry.exclusive ||
            entries.any { (other, value) -> other !== call && ownsAudio(value) }) return false
        if (failure != null) { manual(call, entry, failure, ConversationStopReason.SESSION_CANCELLED); return false }
        entry.policy = policy
        entry.generation = ++sequence
        if (entry.phase == IncomingCallPhase.RINGING) {
            change(call, entry, IncomingAssistantState.ANSWERING, null)
            if (entry.state != IncomingAssistantState.ANSWERING) return false
            try { port.answer(call) } catch (_: Exception) { manual(call, entry, "incoming_answer_failed", ConversationStopReason.SESSION_CANCELLED); return false }
        } else start(call, entry)
        return ownsAudio(entry)
    }

    private fun start(call: C, entry: Entry) {
        change(call, entry, IncomingAssistantState.STARTING, null)
        if (entry.state != IncomingAssistantState.STARTING) return
        try { port.startAudio(call, entry.policy, entry.generation) }
        catch (_: Exception) { manual(call, entry, "incoming_audio_start_failed", ConversationStopReason.SESSION_CANCELLED) }
    }

    private fun ownsAudio(entry: Entry) = entry.state in setOf(
        IncomingAssistantState.ANSWERING, IncomingAssistantState.STARTING, IncomingAssistantState.AI,
    )

    private fun manual(call: C, entry: Entry, reason: String, stopReason: ConversationStopReason, state: IncomingAssistantState = IncomingAssistantState.MANUAL) {
        val stop = ownsAudio(entry) || entry.state == IncomingAssistantState.ENDING
        // ENDING has no AI authority, but can still own runtime resources until
        // Telecom disconnect completes. Human STOP/takeover must stop those too.
        // Revoke before invoking the adapter: even a synchronous callback sees no authority.
        change(call, entry, state, reason)
        // Audio was already revoked; a failed adapter cleanup may not crash human
        // TAKE OVER or block explicit resume on the same still-connected Call.
        if (stop) try { port.stopAudio(call, entry.generation, stopReason) }
        catch (_: Exception) { /* fail closed: no AI authority remains */ }
    }

    private fun change(call: C, entry: Entry, state: IncomingAssistantState, reason: String?) {
        entry.state = state; entry.reason = reason; entry.revision++
        port.changed(call, state, reason)
    }
}
