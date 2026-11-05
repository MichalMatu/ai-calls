package pl.michalmatu.aicallbridge.conversationcore

import java.util.IdentityHashMap

enum class AnsweringMode { MANUAL, AI }
enum class IncomingCallPhase { RINGING, ACTIVE, HELD, ENDED }
enum class IncomingAssistantState { RINGING, ANSWERING, STARTING, AI, MANUAL, ENDED }

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
    fun stopAudio(call: C, generation: Long)
    fun changed(call: C, state: IncomingAssistantState, reason: String?)
}

/** Serialized by its owner (Android uses the main looper). No Telecom, model or audio dependencies. */
class IncomingAssistantCoordinator<C : Any>(private val port: IncomingAssistantPort<C>) {
    private class Entry(val initialPolicy: IncomingAssistantPolicy, var phase: IncomingCallPhase) {
        var policy = initialPolicy
        var account: String? = null
        var exclusive = true
        var state = if (phase == IncomingCallPhase.RINGING && initialPolicy.mode == AnsweringMode.AI)
            IncomingAssistantState.RINGING else IncomingAssistantState.MANUAL
        var generation = 0L
        var reason: String? = null
    }
    private val entries = IdentityHashMap<C, Entry>()
    private var sequence = 0L

    @Synchronized fun observe(call: C, phase: IncomingCallPhase, accountKey: String?, incoming: Boolean,
                exclusive: Boolean, policy: IncomingAssistantPolicy) {
        if (!incoming) return
        val entry = entries[call] ?: Entry(policy, phase).also { entries[call] = it }
        entry.phase = phase
        entry.account = accountKey
        entry.exclusive = exclusive
        when (phase) {
            IncomingCallPhase.ENDED -> { manual(call, entry, "call_ended", IncomingAssistantState.ENDED); return }
            IncomingCallPhase.HELD -> { manual(call, entry, "call_held"); return }
            else -> Unit
        }
        if (ownsAudio(entry) && entry.account != entry.policy.accountKey) {
            manual(call, entry, "incoming_account_changed"); return
        }
        if (entry.state == IncomingAssistantState.RINGING && phase == IncomingCallPhase.RINGING) {
            if (accountKey == null) return // Telecom may publish the account after onCallAdded.
            if (policy.mode != AnsweringMode.AI || entry.initialPolicy.id != policy.id) {
                manual(call, entry, "incoming_policy_changed"); return
            }
            claim(call, entry, policy)
        } else if (entry.state == IncomingAssistantState.ANSWERING && phase == IncomingCallPhase.ACTIVE) {
            start(call, entry)
        } else if (entry.state == IncomingAssistantState.RINGING && phase == IncomingCallPhase.ACTIVE) {
            manual(call, entry, "answered_manually")
        }
    }

    /** Explicit human request; enabling a setting alone never seizes an already active call. */
    @Synchronized fun resume(call: C, policy: IncomingAssistantPolicy): Boolean {
        val entry = entries[call] ?: return false
        if (entry.state != IncomingAssistantState.MANUAL && entry.state != IncomingAssistantState.RINGING) return false
        if (entry.phase != IncomingCallPhase.ACTIVE && entry.phase != IncomingCallPhase.RINGING) return false
        return claim(call, entry, policy)
    }

    @Synchronized fun takeOver(call: C) { entries[call]?.let { manual(call, it, "human_takeover") } }

    @Synchronized fun policyChanged(policy: IncomingAssistantPolicy) {
        entries.entries.toList().forEach { (call, entry) ->
            if ((ownsAudio(entry) || entry.state == IncomingAssistantState.RINGING) &&
                (policy.mode != AnsweringMode.AI || policy.accountKey != entry.policy.accountKey)) {
                manual(call, entry, "incoming_policy_revoked")
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

    @Synchronized fun isAuthorized(call: C, generation: Long): Boolean = entries[call]?.let {
        it.generation == generation && ownsAudio(it) &&
            it.account == it.policy.accountKey && it.phase != IncomingCallPhase.HELD && it.phase != IncomingCallPhase.ENDED
    } == true

    @Synchronized fun snapshot(call: C): IncomingAssistantSnapshot? = entries[call]?.let {
        IncomingAssistantSnapshot(it.state, it.generation, it.reason)
    }

    @Synchronized fun forget(call: C) {
        entries[call]?.let { manual(call, it, "call_removed", IncomingAssistantState.ENDED) }
        entries.remove(call)
    }

    @Synchronized fun close() { entries.keys.toList().forEach(::forget) }

    private fun claim(call: C, entry: Entry, policy: IncomingAssistantPolicy): Boolean {
        val failure = when {
            policy.mode != AnsweringMode.AI -> "incoming_manual_mode"
            entry.account != policy.accountKey -> "incoming_account_mismatch"
            !entry.exclusive || entries.any { (other, value) -> other !== call && ownsAudio(value) } -> "another_call_active"
            else -> try { port.readinessFailure(call) } catch (_: Exception) { "incoming_readiness_failed" }
        }
        if (failure != null) { manual(call, entry, failure); return false }
        entry.policy = policy
        entry.generation = ++sequence
        if (entry.phase == IncomingCallPhase.RINGING) {
            change(call, entry, IncomingAssistantState.ANSWERING, null)
            try { port.answer(call) } catch (_: Exception) { manual(call, entry, "incoming_answer_failed"); return false }
        } else start(call, entry)
        return true
    }

    private fun start(call: C, entry: Entry) {
        change(call, entry, IncomingAssistantState.STARTING, null)
        try { port.startAudio(call, entry.policy, entry.generation) }
        catch (_: Exception) { manual(call, entry, "incoming_audio_start_failed") }
    }

    private fun ownsAudio(entry: Entry) = entry.state in setOf(
        IncomingAssistantState.ANSWERING, IncomingAssistantState.STARTING, IncomingAssistantState.AI,
    )

    private fun manual(call: C, entry: Entry, reason: String, state: IncomingAssistantState = IncomingAssistantState.MANUAL) {
        val stop = ownsAudio(entry)
        // Revoke before invoking the adapter: even a synchronous callback sees no authority.
        change(call, entry, state, reason)
        if (stop) port.stopAudio(call, entry.generation)
    }

    private fun change(call: C, entry: Entry, state: IncomingAssistantState, reason: String?) {
        entry.state = state; entry.reason = reason
        port.changed(call, state, reason)
    }
}
