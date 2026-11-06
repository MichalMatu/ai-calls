package pl.michalmatu.aicallbridge.conversation

/**
 * Measures only active listening intervals, not inference or TTS playback.
 * Failed/no-match STT retries preserve the same deadline; actual voice resets it.
 * All times are monotonic elapsedRealtime values supplied by the caller.
 */
internal class IncomingIdleTimeout(
    private val silenceMs: Long,
    private val graceMs: Long,
) {
    enum class Action { WARN, CLOSE }
    private var listeningStartedAtMs: Long? = null
    private var warned = false

    init {
        require(silenceMs > 0L && graceMs > 0L)
    }

    @Synchronized fun remainingMs(nowMs: Long): Long {
        val start = listeningStartedAtMs ?: nowMs.also { listeningStartedAtMs = it }
        val duration = if (warned) graceMs else silenceMs
        return (duration - (nowMs - start).coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    @Synchronized fun expire(nowMs: Long): Action? {
        if (remainingMs(nowMs) > 0L) return null
        // Deadline only resumes on the next listening window, after approved TX.
        listeningStartedAtMs = null
        if (warned) {
            warned = false
            return Action.CLOSE
        }
        warned = true
        return Action.WARN
    }

    @Synchronized fun heardVoice() {
        listeningStartedAtMs = null
        warned = false
    }
}
