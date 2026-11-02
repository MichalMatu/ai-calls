package pl.michalmatu.aicallbridge.campaign

/**
 * Pure, deterministic state policy for a single user-triggered support-call test.
 * The platform service owns every side effect; this policy cannot dial or end calls.
 */
internal object OneTapOrangeTestPolicy {
    const val TARGET = "510100100"
    const val CONNECT_TIMEOUT_MS = 30_000L
    const val ROUTE_TIMEOUT_MS = 9_000L
    const val MAX_CALL_MS = 80_000L
    const val POLL_MS = 300L

    enum class Readiness {
        WAITING_FOR_CALL,
        WAITING_FOR_AUDIO_ROUTE,
        MEDIA_READY,
        TIMEOUT,
        CALL_LOST,
    }

    fun readiness(
        elapsedMs: Long,
        lastActiveAtMs: Long?,
        inCall: Boolean,
        modeInCall: Boolean,
    ): Readiness = when {
        elapsedMs >= MAX_CALL_MS -> Readiness.TIMEOUT
        !inCall && lastActiveAtMs != null -> Readiness.CALL_LOST
        !inCall && elapsedMs >= CONNECT_TIMEOUT_MS -> Readiness.TIMEOUT
        !inCall -> Readiness.WAITING_FOR_CALL
        modeInCall -> Readiness.MEDIA_READY
        lastActiveAtMs != null &&
            elapsedMs - lastActiveAtMs >= ROUTE_TIMEOUT_MS ->
            Readiness.TIMEOUT
        else -> Readiness.WAITING_FOR_AUDIO_ROUTE
    }

    /** Never terminate a call seen only as ringing or already idle. */
    fun mayRequestGlobalEnd(
        userStartedThisAttempt: Boolean,
        callStillActive: Boolean,
        callWasSeenAfterDial: Boolean,
        elapsedMs: Long,
    ): Boolean =
        userStartedThisAttempt &&
            callWasSeenAfterDial &&
            callStillActive &&
            elapsedMs in 0L..(MAX_CALL_MS + 10_000L)
}
