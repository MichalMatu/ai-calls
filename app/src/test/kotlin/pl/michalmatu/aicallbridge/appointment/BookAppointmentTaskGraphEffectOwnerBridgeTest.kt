package pl.michalmatu.aicallbridge.appointment

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `proposal effect keeps graph in proposal while workflow decides autonomous path`() {
        val workflow = activeWorkflow(task())
        val result = proposedResult()

        val ownerResult = BookAppointmentTaskGraphEffectOwnerBridge(workflow).onApplyResult(result)

        assertEquals(BookAppointmentTaskGraph.PROPOSAL, result.snapshot.state)
        assertEquals(
            BookAppointmentEffectOwnerResult.Applied(CallPolicyAction.AUTONOMOUSLY_ALLOWED),
            ownerResult,
        )
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        assertEquals(null, workflow.snapshot().pendingProposal())
        assertEquals(
            TaskGraphSlotValue.Text(scheduledAt.toString()),
            result.snapshot.context[BookAppointmentTaskGraph.APPOINTMENT_AT],
        )
        assertTrue(result.effects.contains(BookAppointmentTaskGraph.EVALUATE_PROPOSAL_EFFECT))
    }

    @Test
    fun `proposal effect lets workflow own pending user decision without advancing graph`() {
        val preferredWindow = CallTimeWindow(
            ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 9, 24, 17, 0, 0, 0, zone),
        )
        val workflow = activeWorkflow(task(CallPreferences(listOf(preferredWindow), emptyList(), emptyList())))
        val result = proposedResult()

        val ownerResult = BookAppointmentTaskGraphEffectOwnerBridge(workflow).onApplyResult(result)

        assertEquals(BookAppointmentTaskGraph.PROPOSAL, result.snapshot.state)
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
    }

    @Test
    fun `user rejection clears proposal candidate before returning to waiting offer`() {
        val core = CustomTaskGraphCore(BookAppointmentTaskGraph.definition)
        val initial = BookAppointmentTaskGraph.definition.initialSnapshot()
        val proposal = accepted(
            core,
            initial,
            BookAppointmentTaskGraph.PROPOSE_APPOINTMENT,
            mapOf(
                BookAppointmentTaskGraph.APPOINTMENT_AT to
                    TaskGraphSlotValue.Text(scheduledAt.toString()),
            ),
        )
        val confirmation = accepted(
            core,
            proposal,
            BookAppointmentTaskGraph.REQUIRE_CONFIRMATION,
        )

        val waiting = accepted(
            core,
            confirmation,
            BookAppointmentTaskGraph.USER_REJECTED,
        )

        assertEquals(BookAppointmentTaskGraph.WAITING_OFFER, waiting.state)
        assertFalse(waiting.context.contains(BookAppointmentTaskGraph.APPOINTMENT_AT))
    }

    @Test
    fun `rejected apply result is ignored without workflow mutation`() {
        val workflow = activeWorkflow(task())
        val before = workflow.snapshot()
        val rejected = TaskGraphApplyResult.Rejected(
            snapshot = BookAppointmentTaskGraph.definition.initialSnapshot(),
            reason = pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyRejectReason.UNMAPPED_TRANSITION,
        )

        val ownerResult = BookAppointmentTaskGraphEffectOwnerBridge(workflow).onApplyResult(rejected)

        assertEquals(BookAppointmentEffectOwnerResult.Ignored, ownerResult)
        assertEquals(before, workflow.snapshot())
    }

    private fun proposedResult(): TaskGraphApplyResult.Accepted {
        val core = CustomTaskGraphCore(BookAppointmentTaskGraph.definition)
        val reduction = core.reduce(
            BookAppointmentTaskGraph.definition.initialSnapshot(),
            TaskGraphEvent(
                id = BookAppointmentTaskGraph.PROPOSE_APPOINTMENT,
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

    private fun accepted(
        core: CustomTaskGraphCore,
        snapshot: pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot,
        eventId: pl.michalmatu.aicallbridge.taskgraph.TaskGraphEventId,
        candidates: Map<pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId, TaskGraphSlotValue> = emptyMap(),
    ): pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot {
        val reduction = core.reduce(
            snapshot,
            TaskGraphEvent(eventId, snapshot.generation, candidates),
        ) as TaskGraphReduction.Accepted
        return reduction.snapshot
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
