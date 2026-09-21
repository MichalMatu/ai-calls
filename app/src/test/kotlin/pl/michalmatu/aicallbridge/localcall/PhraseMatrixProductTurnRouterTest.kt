package pl.michalmatu.aicallbridge.localcall

import java.math.BigDecimal
import java.util.EnumSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPaymentMode
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanAction
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

class PhraseMatrixProductTurnRouterTest {
    @Test
    fun `alias match preserves diagnostics and resolves only plan owned fact`() {
        val task = task(CallConstraints.unconstrained(), mapOf("birth_year" to "1990"))
        val target = target()
        val workflow = activeWorkflow(task, target)
        val plan = CallPlan(
            task,
            target,
            listOf(CallPlanRule("birth-year", setOf("jaki jest rok urodzenia"), "birth_year")),
            emptyList(),
            emptyList(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "birth-year",
                    phrases = setOf("jaki jest rok urodzenia"),
                    aliases = setOf("jaki jest rok urodzenia prosze"),
                    variantClass = "AUTHORIZED_FACT",
                ),
            ),
        )
        val router = PhraseMatrixProductTurnRouter(matrix, CallPlanTurnCoordinator(plan, workflow))

        val result = router.handleFinalTranscript("Jaki jest rok urodzenia, prosze?", 0)

        assertEquals("birth-year", result?.match?.ruleId)
        assertEquals(PhraseMatcherKind.ALIAS, result?.match?.matcherKind)
        assertEquals(1.0, result?.match?.confidence ?: 0.0, 0.0)
        assertEquals("AUTHORIZED_FACT", result?.match?.variantClass)
        assertEquals(CallPlanAction.SAY, result?.turnResult?.decision()?.action())
        assertEquals("1990", result?.turnResult?.decision()?.text())
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
    }

    @Test
    fun `no matrix match is explicitly unhandled and does not mutate workflow`() {
        val task = task(CallConstraints.unconstrained(), mapOf("birth_year" to "1990"))
        val target = target()
        val workflow = activeWorkflow(task, target)
        val plan = CallPlan(
            task,
            target,
            listOf(CallPlanRule("birth-year", setOf("rok urodzenia"), "birth_year")),
            emptyList(),
            emptyList(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val router = PhraseMatrixProductTurnRouter(
            PhraseMatrix(listOf(PhraseMatrixRule("birth-year", setOf("rok urodzenia")))),
            CallPlanTurnCoordinator(plan, workflow),
        )

        val result = router.handleFinalTranscript("zupełnie inne zdanie", 0)

        assertNull(result)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        assertNull(workflow.snapshot().pendingProposal())
        assertNull(workflow.snapshot().outcome())
    }

    @Test
    fun `matrix rule absent from plan fails closed through bounded plan fallback`() {
        val task = task(CallConstraints.unconstrained(), emptyMap())
        val target = target()
        val workflow = activeWorkflow(task, target)
        val plan = CallPlan(
            task,
            target,
            emptyList(),
            emptyList(),
            emptyList(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val router = PhraseMatrixProductTurnRouter(
            PhraseMatrix(listOf(PhraseMatrixRule("not-in-plan", setOf("tak")))),
            CallPlanTurnCoordinator(plan, workflow),
        )

        val first = router.handleFinalTranscript("tak", 0)
        val exhausted = router.handleFinalTranscript("tak", 1)

        assertEquals("not-in-plan", first?.match?.ruleId)
        assertEquals(CallPlanAction.ASK_REPEAT, first?.turnResult?.decision()?.action())
        assertNull(first?.turnResult?.decision()?.ruleId())
        assertEquals(CallPlanAction.TAKE_OVER, exhausted?.turnResult?.decision()?.action())
        assertNull(exhausted?.turnResult?.decision()?.ruleId())
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
    }

    @Test
    fun `matrix proposal still uses existing workflow policy and exact plan payload`() {
        val task = task(
            CallConstraints(
                emptyList(),
                MoneyAmount(BigDecimal("100.00"), "PLN"),
                EnumSet.noneOf(CallPaymentMode::class.java),
            ),
            emptyMap(),
        )
        val target = target()
        val workflow = activeWorkflow(task, target)
        val proposal = CallProposal(
            null,
            MoneyAmount(BigDecimal("120.00"), "PLN"),
            CallPaymentMode.PRIVATE,
            "Clinic A",
            "Wrocław",
        )
        val plan = CallPlan(
            task,
            target,
            emptyList(),
            emptyList(),
            listOf(CallPlanProposalRule("offer", setOf("oferta"), proposal)),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        val router = PhraseMatrixProductTurnRouter(
            PhraseMatrix(listOf(PhraseMatrixRule("offer", setOf("mamy ofertę")))),
            CallPlanTurnCoordinator(plan, workflow),
        )

        val result = router.handleFinalTranscript("mamy ofertę", 0)

        assertEquals(CallPlanAction.PROPOSAL, result?.turnResult?.decision()?.action())
        assertSame(proposal, result?.turnResult?.decision()?.proposal())
        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, result?.turnResult?.policyDecision()?.action())
        assertSame(proposal, workflow.snapshot().pendingProposal())
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state())
    }

    private fun task(constraints: CallConstraints, facts: Map<String, String>) = CallTask(
        "Clinic A",
        "book",
        "consultation",
        constraints,
        CallPreferences.none(),
        facts,
    )

    private fun target() = CallResolvedTarget("Clinic A", "+48123456789")

    private fun activeWorkflow(task: CallTask, target: CallResolvedTarget): CallWorkflow =
        CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }
}
