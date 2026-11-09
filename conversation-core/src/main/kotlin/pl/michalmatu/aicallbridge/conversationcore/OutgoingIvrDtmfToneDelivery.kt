package pl.michalmatu.aicallbridge.conversationcore

/**
 * Hardware boundary for one DTMF tone. Android Telecom requires every start to be
 * paired with stop before another tone is played. The device belongs to an exact Call.
 */
interface OutgoingIvrToneHardware {
    fun play(digit: Char)
    fun stop()
}

fun interface OutgoingIvrToneStopScheduler {
    /** Return false if the stop cannot be scheduled. Actions must run serially with play/close. */
    fun schedule(delayMs: Long, action: () -> Unit): Boolean
}

/**
 * A single-tone, fail-closed delivery port. The caller supplies *current* Call/grant
 * checks; this class never dials, selects a SIM or interprets a model transcript.
 *
 * dispatch=true means a tone was started and its stop scheduled, NOT that an IVR
 * heard it. An uncertain dispatch is terminal for the same gate step (no replay).
 */
class OutgoingIvrDtmfToneDelivery(
    private val active: () -> Boolean,
    private val authorized: (OutgoingIvrDtmfRequest) -> Boolean,
    private val hardware: OutgoingIvrToneHardware,
    private val scheduler: OutgoingIvrToneStopScheduler,
    private val toneDurationMs: Long = 150L,
) : OutgoingIvrDigitPort, AutoCloseable {
    private var closed = false
    private var playing = false
    private var serial = 0L

    init { require(toneDurationMs > 0L) }

    @Synchronized override fun dispatch(request: OutgoingIvrDtmfRequest): Boolean {
        if (closed || playing || request.digit !in "0123456789*#") return false
        // Check again at the actual hardware boundary, after the gate's two checks.
        if (!runCatching { active() && authorized(request) }.getOrDefault(false)) return false
        val token = ++serial
        playing = true // Reserve BEFORE calling an external, possibly reentrant hardware adapter.
        return try {
            hardware.play(request.digit)
            // STOP, takeover or a revoked grant may happen during play().
            if (closed || !playing ||
                !runCatching { active() && authorized(request) }.getOrDefault(false)) {
                stopCurrent(token)
                false
            } else if (!scheduler.schedule(toneDurationMs) { stopCurrent(token) }) {
                stopCurrent(token)
                false
            } else {
                playing && !closed // synchronous scheduler callbacks cannot report success.
            }
        } catch (_: Exception) {
            stopCurrent(token) // play() can fail after starting the physical tone.
            false
        }
    }

    @Synchronized private fun stopCurrent(token: Long) {
        if (!playing || token != serial) return
        playing = false // Exactly one stop attempt, even with late timer / duplicate close.
        runCatching { hardware.stop() }
    }

    @Synchronized override fun close() {
        closed = true
        stopCurrent(serial)
    }
}
