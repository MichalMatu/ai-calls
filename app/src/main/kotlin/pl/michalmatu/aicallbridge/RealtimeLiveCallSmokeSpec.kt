package pl.michalmatu.aicallbridge

import pl.michalmatu.aicallbridge.agent.CallRealtimeAgentRuntime
import pl.michalmatu.aicallbridge.session.CallRealtimeOutputApprovalPolicy
import pl.michalmatu.aicallbridge.session.CallRealtimeOutputDecision
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionRequest

/** Fixed safety envelope for the first controlled cellular Realtime audio-path probe. */
object RealtimeLiveCallSmokeSpec {
    const val DEFAULT_DURATION_MS = 10_000L
    const val MIN_DURATION_MS = 3_000L
    const val MAX_DURATION_MS = 30_000L
    const val STARTUP_TIMEOUT_MS = 30_000L
    const val CLEANUP_TIMEOUT_MS = 5_000L

    val outputApprovalPolicy = CallRealtimeOutputApprovalPolicy { _, _ ->
        CallRealtimeOutputDecision.RELEASE
    }

    fun validateDurationMs(durationMs: Long): Long {
        require(durationMs in MIN_DURATION_MS..MAX_DURATION_MS) {
            "live Realtime probe duration must be $MIN_DURATION_MS..$MAX_DURATION_MS ms"
        }
        return durationMs
    }

    fun request(): CallRealtimeSessionRequest = CallRealtimeSessionRequest(
        sessionEndpoint = CallRealtimeAgentRuntime.DEFAULT_SESSION_ENDPOINT,
        model = CallRealtimeAgentRuntime.DEFAULT_MODEL,
        instructions = INSTRUCTIONS,
        sampleRateHz = 16_000,
        tools = emptyList(),
    )

    private const val INSTRUCTIONS =
        "Controlled audio-path validation only. Respond briefly and neutrally to the remote speaker. " +
            "Do not make commitments, promises, bookings, purchases, or request sensitive data. " +
            "No tools are available."
}
