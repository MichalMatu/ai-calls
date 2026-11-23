package pl.michalmatu.aicallbridge.presentation.tasks

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import pl.michalmatu.aicallbridge.campaign.*

class PhoneTaskListControllerTest {
    private fun task(state: PhoneTaskState, start: Long, approved: Long,
        title: String = "Synthetic") = PhoneTask(
        UUID.randomUUID().toString(), title, "quote", "Check service",
        "Synthetic, no GSM", 1, "sim", start, 500000L,
        "Europe/Warsaw", null, approved,
        listOf(PhoneTaskRecipient(UUID.randomUUID().toString(), "Contact", "+12025550111")),
        state = state, recordAudio = false,
    ).validated()

    @Test fun groupsAreOrderedAndSkipEmptySections() {
        val current = task(PhoneTaskState.READY, 1000L, 100L)
        val futureOld = task(PhoneTaskState.READY, 3000L, 150L)
        val futureNew = task(PhoneTaskState.READY, 3000L, 250L)
        val history = task(PhoneTaskState.FINISHED, 1000L, 200L)
        val result = PhoneTaskListController.sections(
            listOf(futureOld, history, futureNew, current), 2000L)
        assertEquals(listOf(PhoneTaskManagement.Group.CURRENT,
            PhoneTaskManagement.Group.SCHEDULED, PhoneTaskManagement.Group.HISTORY),
            result.map { it.group })
        assertEquals(listOf(futureNew, futureOld), result[1].tasks)
        assertEquals(listOf(current), result[0].tasks)
        assertEquals(listOf(history), result[2].tasks)
        assertTrue(PhoneTaskListController.sections(emptyList(), 2000L).isEmpty())
    }

    @Test fun copiedTaskIsOnlyAnEditableDraftWithoutApprovalOrCallAttempts() {
        val original = task(PhoneTaskState.FINISHED, 1000L, 200L, title = "Service")
        val draft = PhoneTaskListController.duplicateDraft(original)
        assertEquals("Service", draft.title)
        assertEquals(original.goal, draft.goal)
        assertEquals(original.brief, draft.suppliedBrief)
        assertEquals(original.subscriptionId, draft.subscriptionId)
        assertEquals(original.accountScope, draft.accountScope)
        assertEquals(original.recordAudio, draft.recordAudio)
        assertEquals("Contact; +12025550111", draft.recipients)
        assertEquals(0, draft.step)
        assertNotEquals(original.id, draft.id)
    }
}
