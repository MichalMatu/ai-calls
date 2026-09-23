package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPlanCompletionRule
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState

class CallPlanDeferredCompletionModeTest {
    @Test
    fun `default completion mode keeps workflow-owned completion behavior`() {
        val fixture = fixture()

        val result = CallPlanTurnCoordinator(fixture.plan, fixture.workflow)
            .handleFinalTranscript("gotowe", 0)

        assertEquals(CallPlanAction.COMPLETE, result.decision().action())
        assertSame(fixture.outcome, result.decision().outcome())
        assertEquals(CallWorkflowState.COMPLETED, fixture.workflow.snapshot().state())
        assertSame(fixture.outcome, fixture.workflow.snapshot().outcome())
    }

    @Test
    fun `reviewed deferred completion carries outcome without mutating workflow`() {
        val fixture = fixture()

        val result = CallPlanTurnCoordinator(
            fixture.plan,
            fixture.workflow,
            CallPlanCompletionMode.DEFER_TO_PRODUCT_OWNER,
        ).handleFinalTranscript("gotowe", 0)

        assertEquals(CallPlanAction.COMPLETE, result.decision().action())
        assertSame(fixture.outcome, result.decision().outcome())
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())
        assertNull(fixture.workflow.snapshot().outcome())
    }

    private fun fixture(): Fixture {
        val task = CallTask(
            "Clinic A",
            "book",
            "consultation",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        val target = CallResolvedTarget("Clinic A", "+48123456789")
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }
        val outcome = CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "Done",
            null,
            null,
            null,
            null,
            null,
            null,
        )
        val plan = CallPlan(
            task,
            target,
            emptyList(),
            listOf(CallPlanCompletionRule("done", setOf("gotowe"), outcome)),
            emptyList(),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        return Fixture(plan, workflow, outcome)
    }

    private data class Fixture(
        val plan: CallPlan,
        val workflow: CallWorkflow,
        val outcome: CallOutcome,
    )
}
