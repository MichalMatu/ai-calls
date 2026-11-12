package pl.michalmatu.aicallbridge.conversation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason

class ConversationRxInterruptionGateTest {
    @Test fun downlinkErrorRecordsPartialRxBeforeTeardownCanClaimIt() {
        val done = AtomicBoolean(false)
        var events = 0
        assertTrue(ConversationRxInterruptionGate.claimAndRecord(done) { events++ })
        assertFalse(ConversationRxInterruptionGate.claimAndRecord(done) { events++ })
        assertEquals(1, events)
        assertTrue(done.get())
    }

    @Test fun normalFinishedOrAbandonedWindowDoesNotReportInterruptedRx() {
        val done = AtomicBoolean(false)
        done.compareAndSet(false, true)
        var events = 0
        assertFalse(ConversationRxInterruptionGate.claimAndRecord(done) { events++ })
        assertEquals(0, events)
    }

    @Test fun teardownCannotPassAnInFlightInterruptedEvidenceWrite() {
        val done = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val concurrentCompleted = CountDownLatch(1)
        val events = AtomicInteger(0)
        val failureThread = Thread {
            ConversationRxInterruptionGate.claimAndRecord(done) {
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                events.incrementAndGet()
            }
        }
        failureThread.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        val teardownThread = Thread {
            ConversationRxInterruptionGate.claimAndRecord(done) { events.incrementAndGet() }
            concurrentCompleted.countDown()
        }
        try {
            teardownThread.start()
            assertFalse(concurrentCompleted.await(50, TimeUnit.MILLISECONDS))
        } finally {
            release.countDown()
            failureThread.join(2_000)
            teardownThread.join(2_000)
        }
        assertTrue(concurrentCompleted.await(1, TimeUnit.SECONDS))
        assertEquals(1, events.get())
    }

    @Test fun sourceFailureReasonUsesOnlyRecorderAllowlistedCodes() {
        assertEquals("OWNED_CALL_ENDED", ConversationRxInterruptionGate.sourceFailureCode(null, false))
        assertEquals("SESSION_END", ConversationRxInterruptionGate.sourceFailureCode(null, true))
        assertEquals("OPERATOR_STOP",
            ConversationRxInterruptionGate.sourceFailureCode(ConversationStopReason.OPERATOR_STOP, true))
        assertEquals("OWNED_CALL_ENDED",
            ConversationRxInterruptionGate.sourceFailureCode(ConversationStopReason.CALL_ENDED, true))
    }
}
