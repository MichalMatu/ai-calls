package pl.michalmatu.aicallbridge

import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorState

/** Pure state/evidence gate for one bounded controlled cellular Realtime probe. */
class RealtimeLiveCallSmokeStateTracker {
    private val states = mutableListOf<CallRealtimeSessionOrchestratorState>()
    private var activeSeen = false
    private var controlledStopRequested = false
    private var terminal: RealtimeLiveCallSmokeResult? = null

    @Synchronized
    fun observe(snapshot: CallRealtimeSessionOrchestratorSnapshot): RealtimeLiveCallSmokeResult? {
        terminal?.let { return it }
        append(snapshot.state)
        if (snapshot.state == CallRealtimeSessionOrchestratorState.ACTIVE) {
            activeSeen = true
        }

        val result = when (snapshot.state) {
            CallRealtimeSessionOrchestratorState.FAILED ->
                failure("realtime_live_session_failed", snapshot.failureReason)

            CallRealtimeSessionOrchestratorState.STOPPING ->
                if (controlledStopRequested) null else failure("unexpected_stopping", snapshot.failureReason)

            CallRealtimeSessionOrchestratorState.TAKEN_OVER -> when {
                !activeSeen -> failure("takeover_before_active", snapshot.failureReason)
                !controlledStopRequested -> failure("unexpected_takeover", snapshot.failureReason)
                else -> success("controlled_live_session_completed", snapshot.failureReason)
            }

            else -> null
        }
        if (result != null) terminal = result
        return result
    }

    @Synchronized
    fun markControlledStopRequested() {
        if (terminal == null) controlledStopRequested = true
    }

    @Synchronized
    fun timeout(): RealtimeLiveCallSmokeResult {
        terminal?.let { return it }
        val reason = if (controlledStopRequested) "controlled_stop_timeout" else "startup_timeout"
        return failure(reason, null).also { terminal = it }
    }

    @Synchronized
    fun currentStates(): List<CallRealtimeSessionOrchestratorState> = states.toList()

    private fun append(state: CallRealtimeSessionOrchestratorState) {
        if (state == CallRealtimeSessionOrchestratorState.IDLE) return
        if (states.lastOrNull() != state) states += state
    }

    private fun success(reason: String, detail: String?): RealtimeLiveCallSmokeResult =
        RealtimeLiveCallSmokeResult("PASS", reason, detail, states.toList())

    private fun failure(reason: String, detail: String?): RealtimeLiveCallSmokeResult =
        RealtimeLiveCallSmokeResult("FAIL", reason, detail, states.toList())
}

class RealtimeLiveCallSmokeResult(
    val status: String,
    val reason: String,
    val detail: String?,
    val states: List<CallRealtimeSessionOrchestratorState>,
    val trace: String? = null,
) {
    fun withTrace(renderedTrace: String?): RealtimeLiveCallSmokeResult = RealtimeLiveCallSmokeResult(
        status = status,
        reason = reason,
        detail = detail,
        states = states,
        trace = renderedTrace?.replace('\n', ' ')?.replace('\r', ' ')?.takeIf { it.isNotBlank() },
    )

    fun render(): String = buildString {
        append("realtime_live_call_smoke=").append(status)
        append('\n').append("reason=").append(reason)
        append('\n').append("states=")
        if (states.isEmpty()) append("none") else append(states.joinToString(">") { it.name })
        if (!detail.isNullOrBlank()) {
            append('\n').append("detail=")
            append(detail.replace('\n', ' ').replace('\r', ' ').take(MAX_DETAIL_CHARS))
        }
        if (!trace.isNullOrBlank()) append('\n').append("trace=").append(trace)
    }

    override fun toString(): String = render()

    private companion object {
        const val MAX_DETAIL_CHARS = 256
    }
}
