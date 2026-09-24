package pl.michalmatu.aicallbridge.session

/**
 * Tracks function calls that are awaiting exactly one application response.
 *
 * Session generation, transport identity and orchestrator state remain owned by
 * [CallRealtimeSessionOrchestrator]. The orchestrator serializes access under its existing lock;
 * this class only owns the pending-call collection semantics.
 */
internal class CallRealtimeFunctionCallTracker {
    private val pendingCallIds = mutableSetOf<String>()

    fun register(callId: String): Boolean = pendingCallIds.add(callId)

    fun consume(callId: String): Boolean = pendingCallIds.remove(callId)

    fun discard(callId: String) {
        pendingCallIds.remove(callId)
    }

    fun clear() {
        pendingCallIds.clear()
    }
}
