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
