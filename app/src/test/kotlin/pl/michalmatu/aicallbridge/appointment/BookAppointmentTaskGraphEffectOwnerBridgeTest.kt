package pl.michalmatu.aicallbridge.appointment

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPolicyAction
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallTimeWindow
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState
import pl.michalmatu.aicallbridge.taskgraph.CustomTaskGraphCore
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyResult
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEvent
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphReduction
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue

class BookAppointmentTaskGraphEffectOwnerBridgeTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val scheduledAt = ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, zone)
    private val target = CallResolvedTarget("Example clinic", "+48123456789")

    @Test
    fun `autonomous proposal effect is rechecked by workflow and does not create commitment authority`() {
        val workflow = activeWorkflow(task())
        val before = workflow.snapshot()
        val result = acceptedResult(BookAppointmentTaskGraph.ACCEPT_AUTONOMOUS)

        val ownerResult = BookAppointmentTaskGraphEffectOwnerBridge(workflow).onApplyResult(result)

        assertEquals(
            BookAppointmentEffectOwnerResult.Applied(CallPolicyAction.AUTONOMOUSLY_ALLOWED),
            ownerResult,
        )
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        assertEquals(null, workflow.snapshot().pendingProposal())
        assertEquals(before.task(), workflow.snapshot().task())
        assertTrue(result.effects.contains(BookAppointmentTaskGraph.EVALUATE_PROPOSAL_EFFECT))
    }

    @Test
    fun `confirmation proposal effect stores only the exact workflow-owned pending proposal`() {
        val preferredWindow = CallTimeWindow(
            ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 9, 24, 17, 0, 0, 0, zone),
        )
        val workflow = activeWorkflow(task(CallPreferences(listOf(preferredWindow), emptyList(), emptyList())))
        val result = acceptedResult(BookAppointmentTaskGraph.REQUIRE_CONFIRMATION)

        val ownerResult = BookAppointmentTaskGraphEffectOwnerBridge(workflow).onApplyResult(result)

        assertEquals(
            BookAppointmentEffectOwnerResult.Applied(CallPolicyAction.NEEDS_USER_DECISION),
            ownerResult,
        )
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state())
        assertEquals(scheduledAt, workflow.snapshot().pendingProposal().scheduledAt())
        assertEquals(null, workflow.snapshot().pendingProposal().price())
        assertEquals(null, workflow.snapshot().pendingProposal().paymentMode())
        assertEquals(null, workflow.snapshot().pendingProposal().provider())
        assertEquals(null, workflow.snapshot().pendingProposal().location())
        assertTrue(result.effects.contains(BookAppointmentTaskGraph.EVALUATE_PROPOSAL_EFFECT))
    }

    @Test
    fun `policy transition mismatch fails closed before workflow mutation`() {
        val preferredWindow = CallTimeWindow(
            ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 9, 24, 17, 0, 0, 0, zone),
        )
        val workflow = activeWorkflow(task(CallPreferences(listOf(preferredWindow), emptyList(), emptyList())))
        val before = workflow.snapshot()
        val result = acceptedResult(BookAppointmentTaskGraph.ACCEPT_AUTONOMOUS)

        val ownerResult = BookAppointmentTaskGraphEffectOwnerBridge(workflow).onApplyResult(result)

        assertEquals(
            BookAppointmentEffectOwnerResult.Rejected(
                BookAppointmentEffectOwnerRejectReason.POLICY_TRANSITION_MISMATCH,
            ),
            ownerResult,
        )
        assertEquals(before, workflow.snapshot())
    }

    private fun acceptedResult(eventId: pl.michalmatu.aicallbridge.taskgraph.TaskGraphEventId): TaskGraphApplyResult.Accepted {
        val reduction = CustomTaskGraphCore(BookAppointmentTaskGraph.definition).reduce(
            BookAppointmentTaskGraph.definition.initialSnapshot(),
            TaskGraphEvent(
                id = eventId,
                generation = 0L,
                candidates = mapOf(
                    BookAppointmentTaskGraph.APPOINTMENT_AT to
                        TaskGraphSlotValue.Text(scheduledAt.toString()),
                ),
            ),
        ) as TaskGraphReduction.Accepted
        return TaskGraphApplyResult.Accepted(
            snapshot = reduction.snapshot,
            effects = reduction.effects,
            record = reduction.record,
        )
    }

    private fun activeWorkflow(task: CallTask): CallWorkflow =
        CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }

    private fun task(preferences: CallPreferences = CallPreferences.none()): CallTask = CallTask(
        "Example clinic",
        "book appointment",
        "appointment",
        CallConstraints(
            listOf(
                CallTimeWindow(
                    ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
                    ZonedDateTime.of(2026, 9, 24, 18, 0, 0, 0, zone),
                ),
            ),
            null,
            emptySet(),
        ),
        preferences,
        emptyMap(),
    )
}
