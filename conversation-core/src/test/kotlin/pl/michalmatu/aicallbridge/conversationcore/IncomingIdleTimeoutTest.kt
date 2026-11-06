package pl.michalmatu.aicallbridge.conversationcore

import org.junit.Assert.*
import org.junit.Test

class IncomingIdleTimeoutTest {
    @Test fun warnsAfterThirtySecondsOfActualListeningNotDuringTtsAndClosesAfterFifteenMore() {
        val guard = IncomingIdleTimeout(30_000L, 15_000L)
        assertEquals(30_000L, guard.remainingMs(1_000L))
        assertNull(guard.expire(30_999L))
        assertEquals(1L, guard.remainingMs(30_999L))
        assertEquals(IncomingIdleTimeout.Action.WARN, guard.expire(31_000L))
        // Simulated 12 seconds spent synthesizing and playing the warning do not count.
        assertEquals(15_000L, guard.remainingMs(43_000L))
        assertNull(guard.expire(57_999L))
        assertEquals(IncomingIdleTimeout.Action.CLOSE, guard.expire(58_000L))
    }

    @Test fun callerVoiceResetsWarningAndGraceButNoSpeechRetriesDoNotResetCountdown() {
        val guard = IncomingIdleTimeout(30_000L, 15_000L)
        assertEquals(30_000L, guard.remainingMs(100L))
        assertEquals(18_000L, guard.remainingMs(12_100L))
        assertEquals(IncomingIdleTimeout.Action.WARN, guard.expire(30_100L))
        assertEquals(15_000L, guard.remainingMs(33_000L))
        guard.heardVoice()
        assertEquals(30_000L, guard.remainingMs(44_000L))
        assertNull(guard.expire(58_999L))
        assertEquals(IncomingIdleTimeout.Action.WARN, guard.expire(74_000L))
    }


    @Test fun repeatedChecksAndSpuriousExpirationCannotSkipWarningOrCloseEarly() {
        val timeout = IncomingIdleTimeout(30_000L, 15_000L)
        assertEquals(30_000L, timeout.remainingMs(0L))
        assertEquals(20_000L, timeout.remainingMs(10_000L))
        assertNull(timeout.expire(29_999L))
        assertEquals(0L, timeout.remainingMs(30_000L))
        assertEquals(IncomingIdleTimeout.Action.WARN, timeout.expire(30_000L))
        // Warning playback occurs before the next call to remainingMs; it must not consume grace.
        assertEquals(15_000L, timeout.remainingMs(90_000L))
        assertEquals(1L, timeout.remainingMs(104_999L))
        assertEquals(IncomingIdleTimeout.Action.CLOSE, timeout.expire(105_000L))
    }

    @Test fun speechDuringGraceCancelsClosureAndStartsNewFullInitialWindow() {
        val timeout = IncomingIdleTimeout(30_000L, 15_000L)
        assertEquals(30_000L, timeout.remainingMs(0L))
        assertEquals(IncomingIdleTimeout.Action.WARN, timeout.expire(30_000L))
        assertEquals(15_000L, timeout.remainingMs(50_000L))
        timeout.heardVoice()
        assertEquals(30_000L, timeout.remainingMs(51_000L))
        assertNull(timeout.expire(80_999L))
        assertEquals(IncomingIdleTimeout.Action.WARN, timeout.expire(81_000L))
    }
}
