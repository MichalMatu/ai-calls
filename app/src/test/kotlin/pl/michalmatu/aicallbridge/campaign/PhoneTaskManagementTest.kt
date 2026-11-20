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

    @Test fun skipAndRestoreOnlyExplicitlySkippedRecipientAndPreserveEvidence() {
        val original = task(PhoneTaskState.READY)
        val recipient = original.recipients.single()
        val skipped = PhoneTaskManagement.skipQueued(original, recipient.id)
        assertEquals(PhoneRecipientState.SKIPPED, skipped.recipients.single().state)
        assertEquals(PhoneTaskState.READY, skipped.state)
        assertTrue(PhoneTaskManagement.canRestore(skipped, recipient.id, 2_000L))
        val restored = PhoneTaskManagement.restoreSkipped(skipped, recipient.id, 2_000L)
        assertEquals(PhoneRecipientState.QUEUED, restored.recipients.single().state)
        assertEquals(2_000L, restored.recipients.single().nextAt)
        assertEquals(PhoneTaskState.READY, restored.state)
        assertFalse(PhoneTaskManagement.canRestore(restored, recipient.id, 2_000L))
        assertEquals(restored, PhoneTaskManagement.skipQueued(restored, "wrong-id"))
        assertEquals(original, PhoneTaskManagement.skipQueued(original.copy(state = PhoneTaskState.CANCELLED), recipient.id).copy(state = PhoneTaskState.READY))
        assertTrue(runCatching { PhoneTaskManagement.restoreSkipped(restored, recipient.id, 2_000L) }.isFailure)
        val withHistory = skipped.copy(recipients = skipped.recipients.map {
            it.copy(attempts = listOf(PhoneTaskAttempt(UUID.randomUUID().toString(), 1_200,
                endedAt = 1_500, outcome = PhoneAttemptOutcome.STOPPED)))
        })
        assertEquals(withHistory.recipients.single().attempts,
            PhoneTaskManagement.restoreSkipped(withHistory, recipient.id, 2_000L).recipients.single().attempts)
    }

    @Test fun restoreLastSkippedAfterFinishedReopensPausedAndNeverDials() {
        val original = task(PhoneTaskState.READY)
        val skipped = PhoneTaskManagement.skipQueued(original, original.recipients.single().id)
        val finished = PhoneTaskQueue.settle(skipped, 2_000L)
        assertEquals(PhoneTaskState.FINISHED, finished.state)
        assertTrue(PhoneTaskManagement.canRestore(finished, original.recipients.single().id, 3_000L))
        val restored = PhoneTaskManagement.restoreSkipped(finished, original.recipients.single().id, 3_000L)
        assertEquals(PhoneTaskState.PAUSED, restored.state)
        assertEquals(PhoneRecipientState.QUEUED, restored.recipients.single().state)
        assertNull(PhoneTaskQueue.nextAt(restored))
        assertNull(PhoneTaskQueue.claim(listOf(restored), 3_000L, UUID.randomUUID().toString()))
        val explicitResume = restored.copy(state = PhoneTaskState.READY)
        assertNotNull(PhoneTaskQueue.claim(listOf(explicitResume), 3_000L, UUID.randomUUID().toString()))
    }

    @Test fun restorationFailsClosedAfterExpiryForCancelledAndForUnknownOutcomes() {
        val original = task(PhoneTaskState.READY)
        val id = original.recipients.single().id
        val skipped = PhoneTaskManagement.skipQueued(original, id)
        assertFalse(PhoneTaskManagement.canRestore(skipped, id, skipped.endAt))
        assertFalse(PhoneTaskManagement.canRestore(skipped.copy(state = PhoneTaskState.CANCELLED), id, 2_000L))
        assertFalse(PhoneTaskManagement.canRestore(skipped.copy(recipients =
            skipped.recipients.map { it.copy(state = PhoneRecipientState.UNKNOWN) }), id, 2_000L))
        assertFalse(PhoneTaskManagement.canRestore(skipped, "not-a-recipient", 2_000L))
        assertTrue(runCatching { PhoneTaskManagement.restoreSkipped(skipped, id, skipped.endAt) }.isFailure)
        assertFalse(PhoneTaskManagement.hasComparison(original))
        val answered = original.copy(recipients = listOf(original.recipients.single().copy(
            attempts = listOf(PhoneTaskAttempt(UUID.randomUUID().toString(), 2_000L,
                endedAt = 3_000L, outcome = PhoneAttemptOutcome.ANSWERED,
                answers = listOf(PhoneTaskAnswer(1, "Test answer")))))))
        assertFalse(PhoneTaskManagement.hasComparison(answered))
        assertTrue(PhoneTaskManagement.hasComparison(answered.copy(recipients =
            answered.recipients + original.recipients.single().copy(
                id = UUID.randomUUID().toString(), number = "+12025550112"))))
    }

    @Test fun missingOrAlreadyDeletedTaskFailsClosed() {
        val task = task(PhoneTaskState.FINISHED)
        assertTrue(runCatching {
            PhoneTaskManagement.removeTerminal(emptyList(), task.id, null)
        }.isFailure)
    }
}
