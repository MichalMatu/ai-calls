package pl.michalmatu.aicallbridge.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRealtimeFunctionCallTrackerTest {
    @Test
    fun duplicateCallIdIsRejectedUntilPendingEntryIsConsumed() {
        val tracker = CallRealtimeFunctionCallTracker()

        assertTrue(tracker.register("call_1"))
        assertFalse(tracker.register("call_1"))
        assertTrue(tracker.consume("call_1"))
        assertFalse(tracker.consume("call_1"))
        assertTrue(tracker.register("call_1"))
    }

    @Test
    fun clearInvalidatesPendingEntriesAndAllowsIdsInNextGeneration() {
        val tracker = CallRealtimeFunctionCallTracker()
        assertTrue(tracker.register("call_old"))
        assertTrue(tracker.register("call_other"))

        tracker.clear()

        assertFalse(tracker.consume("call_old"))
        assertFalse(tracker.consume("call_other"))
        assertTrue(tracker.register("call_old"))
    }

    @Test
    fun discardRemovesAbandonedHandlerCall() {
        val tracker = CallRealtimeFunctionCallTracker()
        assertTrue(tracker.register("call_crash"))

        tracker.discard("call_crash")

        assertFalse(tracker.consume("call_crash"))
        assertTrue(tracker.register("call_crash"))
    }
}
