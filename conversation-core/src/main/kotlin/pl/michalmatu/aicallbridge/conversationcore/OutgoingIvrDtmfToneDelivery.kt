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
    // A failed stop is an uncertain hardware effect, never permission to start another tone.
    private var stopUncertain = false
    private var serial = 0L

    init { require(toneDurationMs > 0L) }

    @Synchronized override fun dispatch(request: OutgoingIvrDtmfRequest): Boolean =
        dispatchAttempt(request) == OutgoingIvrDigitAttempt.STARTED

    @Synchronized override fun dispatchAttempt(request: OutgoingIvrDtmfRequest): OutgoingIvrDigitAttempt {
        if (closed || playing || stopUncertain || request.digit !in "0123456789*#") {
            return OutgoingIvrDigitAttempt.NOT_STARTED
        }
        // Check again at the actual hardware boundary, after the gate's two checks.
        if (!runCatching { active() && authorized(request) }.getOrDefault(false)) {
            return OutgoingIvrDigitAttempt.NOT_STARTED
        }
        val token = ++serial
        playing = true // Reserve BEFORE calling an external, possibly reentrant hardware adapter.
        return try {
            hardware.play(request.digit)
            // STOP, takeover or a revoked grant may happen during play(): the
            // tone might have started, so the outcome must never be retried.
            if (closed || !playing ||
                !runCatching { active() && authorized(request) }.getOrDefault(false)) {
                stopCurrent(token)
                OutgoingIvrDigitAttempt.UNCERTAIN
            } else if (!scheduler.schedule(toneDurationMs) { stopCurrent(token) }) {
                stopCurrent(token)
                OutgoingIvrDigitAttempt.UNCERTAIN
            } else if (playing && !closed) {
                OutgoingIvrDigitAttempt.STARTED
            } else {
                OutgoingIvrDigitAttempt.UNCERTAIN // synchronous timer or close callback
            }
        } catch (_: Exception) {
            stopCurrent(token) // play() may have started the physical tone.
            OutgoingIvrDigitAttempt.UNCERTAIN
        }
    }

    @Synchronized private fun stopCurrent(token: Long) {
        if (token != serial || (!playing && !stopUncertain)) return
        playing = false
        if (runCatching { hardware.stop() }.isFailure) {
            // The tone may still sound. Permanently fence starts; a later close()
            // may safely retry stop for this exact Call, never play another digit.
            stopUncertain = true
            closed = true
        } else {
            stopUncertain = false
        }
    }

    @Synchronized override fun close() {
        closed = true
        stopCurrent(serial)
    }
}
