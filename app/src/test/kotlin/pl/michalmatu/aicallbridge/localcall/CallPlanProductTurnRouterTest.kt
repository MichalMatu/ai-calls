package pl.michalmatu.aicallbridge.localcall

import java.math.BigDecimal
import java.util.EnumSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPaymentMode
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanCompletionRule
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy
import pl.michalmatu.aicallbridge.agent.CallPlanProposalRule
import pl.michalmatu.aicallbridge.agent.CallPlanRule
import pl.michalmatu.aicallbridge.agent.CallPolicyAction
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState
import pl.michalmatu.aicallbridge.agent.MoneyAmount
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallTurnController
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision

class CallPlanProductTurnRouterTest {
    @Test
    fun `final transcript say reaches application approval without backend generation`() {
        val task = task(CallConstraints.unconstrained(), mapOf("birth_year" to "1990"))
        val target = target("Clinic A", "+48123456789")
        val workflow = activeWorkflow(task, target)
        val plan = CallPlan(
            task,
            target,
            listOf(CallPlanRule("birth-year", setOf("rok urodzenia"), "birth_year")),
            emptyList(),
            emptyList(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val backend = FakeBackend()
        val approvedInputs = mutableListOf<String>()
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy { text ->
                approvedInputs += text
                TextOutputDecision.RELEASE
            },
        )
        val router = CallPlanProductTurnRouter(
            CallPlanTurnCoordinator(plan, workflow),
            CallPlanTextOutputRouter(controller),
        )
        val listener = RecordingListener()

        router.handleFinalTranscript("rok urodzenia", 0, listener)

        assertEquals(listOf("1990"), approvedInputs)
        assertEquals(listOf("1990"), listener.approvedText)
        assertTrue(listener.structured.isEmpty())
        assertFalse(listener.dropped)
        assertEquals(0, backend.generateCalls)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
    }

    @Test
    fun `proposal and completion remain structured while workflow stays authority owner`() {
        val constrainedTask = task(
            CallConstraints(
                emptyList(),
                MoneyAmount(BigDecimal("100.00"), "PLN"),
                EnumSet.noneOf(CallPaymentMode::class.java),
            ),
            emptyMap(),
        )
        val target = target("Clinic A", "+48123456789")
        val proposalWorkflow = activeWorkflow(constrainedTask, target)
        val proposal = CallProposal(
            null,
            MoneyAmount(BigDecimal("120.00"), "PLN"),
            CallPaymentMode.PRIVATE,
            "Clinic A",
            "Wrocław",
        )
        val proposalPlan = CallPlan(
            constrainedTask,
            target,
            emptyList(),
            emptyList(),
            listOf(CallPlanProposalRule("offer", setOf("oferta 120 zł"), proposal)),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        val backend = FakeBackend()
        var approvalCalls = 0
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy {
                approvalCalls += 1
                TextOutputDecision.RELEASE
            },
        )
        val proposalRouter = CallPlanProductTurnRouter(
            CallPlanTurnCoordinator(proposalPlan, proposalWorkflow),
            CallPlanTextOutputRouter(controller),
        )
        val proposalListener = RecordingListener()

        proposalRouter.handleFinalTranscript("oferta 120 zł", 0, proposalListener)

        assertEquals(1, proposalListener.structured.size)
        assertSame(proposal, proposalListener.structured.single().decision().proposal())
        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, proposalListener.structured.single().policyDecision().action())
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, proposalWorkflow.snapshot().state())
        assertSame(proposal, proposalWorkflow.snapshot().pendingProposal())
        assertEquals(0, approvalCalls)
        assertEquals(0, backend.generateCalls)

        val completionTask = task(CallConstraints.unconstrained(), emptyMap())
        val completionWorkflow = activeWorkflow(completionTask, target)
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
        val completionPlan = CallPlan(
            completionTask,
            target,
            emptyList(),
            listOf(CallPlanCompletionRule("done", setOf("gotowe"), outcome)),
            emptyList(),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        val completionRouter = CallPlanProductTurnRouter(
            CallPlanTurnCoordinator(completionPlan, completionWorkflow),
            CallPlanTextOutputRouter(controller),
        )
        val completionListener = RecordingListener()

        completionRouter.handleFinalTranscript("gotowe", 0, completionListener)

        assertEquals(1, completionListener.structured.size)
        assertSame(outcome, completionListener.structured.single().decision().outcome())
        assertEquals(CallWorkflowState.COMPLETED, completionWorkflow.snapshot().state())
        assertSame(outcome, completionWorkflow.snapshot().outcome())
        assertEquals(0, approvalCalls)
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `binding failure reaches neither approval nor backend`() {
        val task = task(CallConstraints.unconstrained(), mapOf("birth_year" to "1990"))
        val workflowTarget = target("Clinic A", "+48111111111")
        val planTarget = target("Clinic B", "+48222222222")
        val workflow = activeWorkflow(task, workflowTarget)
        val plan = CallPlan(
            task,
            planTarget,
            listOf(CallPlanRule("birth-year", setOf("rok urodzenia"), "birth_year")),
            emptyList(),
            emptyList(),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        val backend = FakeBackend()
        var approvalCalls = 0
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy {
                approvalCalls += 1
                TextOutputDecision.RELEASE
            },
        )
        val router = CallPlanProductTurnRouter(
            CallPlanTurnCoordinator(plan, workflow),
            CallPlanTextOutputRouter(controller),
        )

        assertThrows(IllegalStateException::class.java) {
            router.handleFinalTranscript("rok urodzenia", 0, RecordingListener())
        }

        assertEquals(0, approvalCalls)
        assertEquals(0, backend.generateCalls)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
    }

    private class RecordingListener : CallPlanTextOutputRouter.Listener {
        val approvedText = mutableListOf<String>()
        val structured = mutableListOf<CallPlanTurnResult>()
        var dropped = false

        override fun onApprovedText(text: String) { approvedText += text }
        override fun onDroppedText() { dropped = true }
        override fun onStructuredResult(result: CallPlanTurnResult) { structured += result }
        override fun onError(reason: String) = error(reason)
    }

    private class FakeBackend : TextCallAgentBackend {
        var generateCalls = 0
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) { generateCalls += 1 }
        override fun cancel() = Unit
    }

    private fun task(constraints: CallConstraints, facts: Map<String, String>) = CallTask(
        "Clinic A",
        "book",
        "consultation",
        constraints,
        CallPreferences.none(),
        facts,
    )

    private fun target(label: String, number: String) = CallResolvedTarget(label, number)

    private fun activeWorkflow(task: CallTask, target: CallResolvedTarget): CallWorkflow {
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
        workflow.resolveTarget(target)
        workflow.markDialing()
        workflow.markCallActive()
        return workflow
    }
}
