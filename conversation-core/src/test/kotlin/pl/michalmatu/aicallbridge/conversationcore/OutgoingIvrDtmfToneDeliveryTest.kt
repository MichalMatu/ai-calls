package pl.michalmatu.aicallbridge.conversationcore

import org.junit.Assert.*
import org.junit.Test

class OutgoingIvrDtmfToneDeliveryTest {
    private val digit = OutgoingIvrDtmfRequest(17L, 2, 1, '5')

    private class Hardware : OutgoingIvrToneHardware {
        val events = mutableListOf<String>()
        var afterPlay: (() -> Unit)? = null
        override fun play(digit: Char) {
            events += "play:$digit"
            afterPlay?.invoke()
        }
        override fun stop() { events += "stop" }
    }

    private class Timer : OutgoingIvrToneStopScheduler {
        val tasks = mutableListOf<() -> Unit>()
        var accepts = true
        override fun schedule(delayMs: Long, action: () -> Unit): Boolean {
            assertEquals(150L, delayMs)
            if (accepts) tasks += action
            return accepts
        }
        fun fire() { tasks.removeAt(0).invoke() }
    }

    @Test fun startsSingleToneAndPairsWithStopBeforeNextTone() {
        val hardware = Hardware()
        val timer = Timer()
        val port = OutgoingIvrDtmfToneDelivery({ true }, { true }, hardware, timer)
        assertTrue(port.dispatch(digit))
        assertFalse(port.dispatch(digit.copy(stepIndex = 2, digit = '#')))
        timer.fire()
        assertTrue(port.dispatch(digit.copy(stepIndex = 2, digit = '#')))
        timer.fire()
        assertEquals(listOf("play:5", "stop", "play:#", "stop"), hardware.events)
    }

    @Test fun closingActiveToneStopsOnlyExactToneAndLateTimerIsInert() {
        val hardware = Hardware()
        val timer = Timer()
        val port = OutgoingIvrDtmfToneDelivery({ true }, { true }, hardware, timer)
        assertTrue(port.dispatch(digit))
        port.close()
        port.close()
        timer.fire()
        assertFalse(port.dispatch(digit.copy(stepIndex = 2)))
        assertEquals(listOf("play:5", "stop"), hardware.events)
    }

    @Test fun busyToneDoesNotConsumeDifferentStepAndCanRetryAfterStop() {
        val hardware = Hardware()
        val timer = Timer()
        val delivery = OutgoingIvrDtmfToneDelivery({ true }, { true }, hardware, timer)
        val gate = OutgoingIvrDtmfGate({ true }, { true }, delivery)
        val first = digit
        val second = digit.copy(stepIndex = 2, digit = '#')
        assertEquals(OutgoingIvrDtmfResult.DISPATCHED, gate.request(first))
        assertEquals(OutgoingIvrDtmfResult.NOT_READY, gate.request(second))
        assertEquals(listOf("play:5"), hardware.events)
        timer.fire()
        assertEquals(OutgoingIvrDtmfResult.DISPATCHED, gate.request(second))
        assertEquals(OutgoingIvrDtmfResult.REUSED, gate.request(second))
        timer.fire()
        assertEquals(listOf("play:5", "stop", "play:#", "stop"), hardware.events)
    }

    @Test fun deniedAtHardwareBoundaryIsNotStartedAndMayRetryAfterGrantReturns() {
        val hardware = Hardware()
        var permission = false
        val delivery = OutgoingIvrDtmfToneDelivery({ true }, { permission }, hardware, Timer())
        val gate = OutgoingIvrDtmfGate({ true }, { true }, delivery)
        assertEquals(OutgoingIvrDtmfResult.NOT_READY, gate.request(digit))
        assertTrue(hardware.events.isEmpty())
        permission = true
        assertEquals(OutgoingIvrDtmfResult.DISPATCHED, gate.request(digit))
    }

    @Test fun exceptionAfterHardwarePlayCannotBeRetriedAsNeverStarted() {
        val hardware = Hardware()
        val timer = Timer()
        hardware.afterPlay = { throw IllegalStateException("synthetic_telecom_exception") }
        val gate = OutgoingIvrDtmfGate({ true }, { true },
            OutgoingIvrDtmfToneDelivery({ true }, { true }, hardware, timer))
        assertEquals(OutgoingIvrDtmfResult.UNKNOWN, gate.request(digit))
        assertEquals(OutgoingIvrDtmfResult.UNKNOWN, gate.request(digit))
        assertEquals(listOf("play:5", "stop"), hardware.events)
        assertTrue(timer.tasks.isEmpty())
    }

    @Test fun failedHardwareStopBlocksNextToneAndCloseRetriesExactStop() {
        val events = mutableListOf<String>()
        var failures = 1
        val hardware = object : OutgoingIvrToneHardware {
            override fun play(digit: Char) { events += "play:$digit" }
            override fun stop() {
                events += "stop"
                if (failures-- > 0) throw IllegalStateException("synthetic_stop_failure")
            }
        }
        val timer = Timer()
        val port = OutgoingIvrDtmfToneDelivery({ true }, { true }, hardware, timer)
        val gate = OutgoingIvrDtmfGate({ true }, { true }, port)
        assertEquals(OutgoingIvrDtmfResult.DISPATCHED, gate.request(digit))
        timer.fire() // stop throws: never mark hardware as available for the next digit
        val next = digit.copy(stepIndex = 2, digit = '#')
        assertEquals(OutgoingIvrDtmfResult.NOT_READY, gate.request(next))
        assertEquals(listOf("play:5", "stop"), events)
        port.close() // safe best-effort stop retry on the same original hardware/Call
        assertEquals(listOf("play:5", "stop", "stop"), events)
        assertEquals(OutgoingIvrDtmfResult.NOT_READY, gate.request(next))
        assertEquals(listOf("play:5", "stop", "stop"), events)
    }

    @Test fun repeatedStopFailureIsFailClosedAndNeverReplaysDigit() {
        var plays = 0
        var stops = 0
        val hardware = object : OutgoingIvrToneHardware {
            override fun play(digit: Char) { plays++ }
            override fun stop() { stops++; error("still_uncertain") }
        }
        val timer = Timer()
        val port = OutgoingIvrDtmfToneDelivery({ true }, { true }, hardware, timer)
        assertEquals(OutgoingIvrDigitAttempt.STARTED, port.dispatchAttempt(digit))
        timer.fire()
        port.close()
        port.close()
        assertEquals(1, plays)
        assertEquals(3, stops)
        assertEquals(OutgoingIvrDigitAttempt.NOT_STARTED,
            port.dispatchAttempt(digit.copy(stepIndex = 2)))
    }

    @Test fun timerFailureIsUnknownAndStopsImmediately() {
        val hardware = Hardware()
        val timer = Timer().also { it.accepts = false }
        val gate = OutgoingIvrDtmfGate({ true }, { true },
            OutgoingIvrDtmfToneDelivery({ true }, { true }, hardware, timer))
        assertEquals(OutgoingIvrDtmfResult.UNKNOWN, gate.request(digit))
        assertEquals(OutgoingIvrDtmfResult.UNKNOWN, gate.request(digit))
        assertEquals(listOf("play:5", "stop"), hardware.events)
    }

    @Test fun revokedGrantOrInactiveCallNeverStartsTone() {
        val hardware = Hardware()
        val port = OutgoingIvrDtmfToneDelivery({ false }, { true }, hardware, Timer())
        assertFalse(port.dispatch(digit))
        val revoked = OutgoingIvrDtmfToneDelivery({ true }, { false }, hardware, Timer())
        assertFalse(revoked.dispatch(digit))
        assertTrue(hardware.events.isEmpty())
    }

    @Test fun reentrantStopDuringPlatformPlayDoesNotScheduleFutureTone() {
        val hardware = Hardware()
        val timer = Timer()
        lateinit var port: OutgoingIvrDtmfToneDelivery
        port = OutgoingIvrDtmfToneDelivery({ true }, { true }, hardware, timer)
        hardware.afterPlay = { port.close() }
        assertFalse(port.dispatch(digit))
        assertEquals(listOf("play:5", "stop"), hardware.events)
        assertTrue(timer.tasks.isEmpty())
    }

    @Test fun grantRevokedDuringPlatformPlayStopsImmediately() {
        val hardware = Hardware()
        val timer = Timer()
        var authorized = true
        val port = OutgoingIvrDtmfToneDelivery({ true }, { authorized }, hardware, timer)
        hardware.afterPlay = { authorized = false }
        assertFalse(port.dispatch(digit))
        assertEquals(listOf("play:5", "stop"), hardware.events)
        assertTrue(timer.tasks.isEmpty())
    }
}
