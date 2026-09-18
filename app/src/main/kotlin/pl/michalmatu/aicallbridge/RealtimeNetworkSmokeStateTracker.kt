package pl.michalmatu.aicallbridge

import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorState

/**
 * Pure state gate for the ADB-only off-call Realtime smoke.
 *
 * Reaching STARTING_MEDIA proves the credential fetch and Realtime connection completed. The
 * production Samsung media path must then reject start because no cellular call is active. Reaching
 * ACTIVE while off-call is always a safety failure.
 */
class RealtimeNetworkSmokeStateTracker {
    private val states = mutableListOf<CallRealtimeSessionOrchestratorState>()
    private var terminal: RealtimeNetworkSmokeResult? = null

    @Synchronized
    fun observe(snapshot: CallRealtimeSessionOrchestratorSnapshot): RealtimeNetworkSmokeResult? {
        terminal?.let { return it }
        append(snapshot.state)

        val result = when (snapshot.state) {
            CallRealtimeSessionOrchestratorState.ACTIVE ->
                failure("unexpected_active_off_call", snapshot.failureReason)

            CallRealtimeSessionOrchestratorState.FAILED -> {
                if (!states.contains(CallRealtimeSessionOrchestratorState.STARTING_MEDIA)) {
                    failure("realtime_connect_failed", snapshot.failureReason)
                } else if (snapshot.failureReason?.contains(EXPECTED_OFF_CALL_FAILURE) == true) {
                    success("realtime_connected_off_call_media_rejected", snapshot.failureReason)
                } else {
                    failure("unexpected_off_call_failure", snapshot.failureReason)
                }
            }

            else -> null
        }
        if (result != null) terminal = result
        return result
    }

    @Synchronized
    fun timeout(): RealtimeNetworkSmokeResult {
        terminal?.let { return it }
        return failure("timeout", null).also { terminal = it }
    }

    @Synchronized
    fun currentStates(): List<CallRealtimeSessionOrchestratorState> = states.toList()

    private fun append(state: CallRealtimeSessionOrchestratorState) {
        if (state == CallRealtimeSessionOrchestratorState.IDLE) return
        if (states.lastOrNull() != state) states += state
    }

    private fun success(reason: String, detail: String?): RealtimeNetworkSmokeResult =
        RealtimeNetworkSmokeResult("PASS", reason, detail, states.toList())

    private fun failure(reason: String, detail: String?): RealtimeNetworkSmokeResult =
        RealtimeNetworkSmokeResult("FAIL", reason, detail, states.toList())

    private companion object {
        const val EXPECTED_OFF_CALL_FAILURE = "cellular call is not active"
    }
}

class RealtimeNetworkSmokeResult(
    val status: String,
    val reason: String,
    val detail: String?,
    val states: List<CallRealtimeSessionOrchestratorState>,
) {
    fun render(): String = buildString {
        append("realtime_network_off_call_smoke=").append(status)
        append('\n').append("reason=").append(reason)
        append('\n').append("states=")
        if (states.isEmpty()) {
            append("none")
        } else {
            append(states.joinToString(">") { it.name })
        }
        if (!detail.isNullOrBlank()) {
            append('\n').append("detail=")
            append(detail.replace('\n', ' ').replace('\r', ' '))
        }
    }

    override fun toString(): String = render()
}
