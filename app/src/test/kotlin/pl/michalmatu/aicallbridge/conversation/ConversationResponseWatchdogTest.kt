package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class ConversationResponseWatchdogTest {
    private class ManualQueue {
        val posted = mutableListOf<Pair<Runnable, Long>>()
        val removed = mutableListOf<Runnable>()
        fun post(runnable: Runnable, delay: Long) { posted += runnable to delay }
        fun remove(runnable: Runnable) { removed += runnable }
    }

    @Test fun timesOutOnlyTheCurrentModelResponseOnce() {
        val queue = ManualQueue()
        val watchdog = ConversationResponseWatchdog(90_000L, queue::post, queue::remove)
        var outcomes = 0
        watchdog.arm { outcomes++ }
        val stale = queue.posted.last().first
        watchdog.arm { outcomes += 10 }
        val active = queue.posted.last().first
        assertEquals(90_000L, queue.posted.last().second)
        assertTrue(stale in queue.removed)
        stale.run() // Already queued on the main Looper despite removeCallbacks.
        assertEquals(0, outcomes)
        active.run()
        active.run()
        assertEquals(10, outcomes)
    }

    @Test fun approvedResponseStopAndRestartInvalidateTheOldTimeout() {
        val queue = ManualQueue()
        val watchdog = ConversationResponseWatchdog(90_000L, queue::post, queue::remove)
        var timedOut = false
        watchdog.arm { timedOut = true }
        val beforeApproval = queue.posted.last().first
        watchdog.cancel()
        beforeApproval.run()
        assertFalse(timedOut)
        assertTrue(beforeApproval in queue.removed)
        watchdog.arm { timedOut = true }
        val beforeStop = queue.posted.last().first
        watchdog.cancel()
        beforeStop.run()
        assertFalse(timedOut)
    }
}
