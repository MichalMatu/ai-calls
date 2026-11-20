package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PhoneTaskManagementTest {
    private fun task(state: PhoneTaskState, start: Long = 1000L) = PhoneTask(
        UUID.randomUUID().toString(), "Local test", "quote", "Ask for service price",
        "Synthetic test task; no real calls", 1, "test-sim", start,
        500000L, "Europe/Warsaw", null, 500L,
        listOf(PhoneTaskRecipient(UUID.randomUUID().toString(), "Test", "+12025550111")),
        state = state,
    ).validated()

    @Test fun groupingPreservesCurrentScheduledAndPast() {
        assertEquals(PhoneTaskManagement.Group.CURRENT,
            PhoneTaskManagement.group(task(PhoneTaskState.READY), 2000))
        assertEquals(PhoneTaskManagement.Group.CURRENT,
            PhoneTaskManagement.group(task(PhoneTaskState.PAUSED, 3000), 2000))
        assertEquals(PhoneTaskManagement.Group.SCHEDULED,
            PhoneTaskManagement.group(task(PhoneTaskState.READY, 3000), 2000))
        assertEquals(PhoneTaskManagement.Group.HISTORY,
            PhoneTaskManagement.group(task(PhoneTaskState.FINISHED), 2000))
        assertEquals(PhoneTaskManagement.Group.HISTORY,
            PhoneTaskManagement.group(task(PhoneTaskState.CANCELLED), 2000))
    }

    @Test fun deleteTerminalRemovesOnlyTargetWithoutTouchingOtherTasks() {
        val done = task(PhoneTaskState.FINISHED)
        val cancelled = task(PhoneTaskState.CANCELLED)
        val future = task(PhoneTaskState.READY, 3000)
        val updated = PhoneTaskManagement.removeTerminal(listOf(done, cancelled, future),
            cancelled.id, null)
        assertEquals(listOf(done, future), updated)
        assertEquals(emptyList<PhoneTask>(), PhoneTaskManagement.removeTerminal(listOf(done), done.id, null))
    }

    @Test fun activeAndScheduledTasksCannotBeDeleted() {
        for (state in listOf(PhoneTaskState.READY, PhoneTaskState.PAUSED)) {
            val current = task(state)
            assertFalse(PhoneTaskManagement.canDelete(current, null))
            assertTrue(runCatching {
                PhoneTaskManagement.removeTerminal(listOf(current), current.id, null)
            }.isFailure)
        }
    }

    @Test fun activeTaskIdAndUnfinishedAttemptBlockTerminalDeletion() {
        val done = task(PhoneTaskState.FINISHED)
        assertFalse(PhoneTaskManagement.canDelete(done, done.id))
        assertTrue(runCatching {
            PhoneTaskManagement.removeTerminal(listOf(done), done.id, done.id)
        }.isFailure)
        val attempt = PhoneTaskAttempt(UUID.randomUUID().toString(), 1200, endedAt = null)
        val inFlight = done.copy(recipients = done.recipients.map {
            it.copy(state = PhoneRecipientState.IN_FLIGHT, attempts = listOf(attempt))
        })
        assertFalse(PhoneTaskManagement.canDelete(inFlight, null))
        assertTrue(runCatching {
            PhoneTaskManagement.removeTerminal(listOf(inFlight), inFlight.id, null)
        }.isFailure)
        val danglingAttempt = done.copy(recipients = done.recipients.map {
            it.copy(attempts = listOf(attempt))
        })
        assertFalse(PhoneTaskManagement.canDelete(danglingAttempt, null))
    }

    @Test fun missingOrAlreadyDeletedTaskFailsClosed() {
        val task = task(PhoneTaskState.FINISHED)
        assertTrue(runCatching {
            PhoneTaskManagement.removeTerminal(emptyList(), task.id, null)
        }.isFailure)
    }
}
