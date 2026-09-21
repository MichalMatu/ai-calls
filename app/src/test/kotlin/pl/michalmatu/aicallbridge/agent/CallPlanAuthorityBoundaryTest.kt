package pl.michalmatu.aicallbridge.agent

import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.EnumSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.realtime.RealtimeOutputPartId
import pl.michalmatu.aicallbridge.session.CallRealtimeOutputDecision

class CallPlanAuthorityBoundaryTest {
    private val partId = RealtimeOutputPartId("resp_plan", "item_plan", 0, 0)

    @Test
    fun outOfPolicyPlanProposalCannotBypassUserDecisionOrSpeechGate() {
        val task = task(maxPrice = "100.00")
        val proposal = proposal("120.00")
        val decision = CallPlanEngine().decide(plan(task, proposal), "oferta 120 zł", 0)
        val gate = CallCommitmentGate { "permit-needs-user" }

        assertEquals(CallPlanAction.PROPOSAL, decision.action())
        assertSame(proposal, decision.proposal())
        assertFalse(gate.hasAuthorization())
        assertTrue(gate.consume("self-issued-token").isFailure)

        val workflow = activeWorkflow(task)
        val policyDecision = workflow.evaluateProposal(decision.proposal())

        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, policyDecision.action())
        assertEquals(listOf(CallPolicyReason.PRICE_ABOVE_MAX), policyDecision.reasons())
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state())
        assertSame(proposal, workflow.snapshot().pendingProposal())
        assertFalse(gate.hasAuthorization())

        val outputPolicy = CallRealtimeAgentOutputApprovalPolicy(workflow, gate)
        assertEquals(
            CallRealtimeOutputDecision.DROP,
            outputPolicy.evaluate(partId, "To rezerwuję bez decyzji użytkownika."),
        )
    }

    @Test
    fun autonomousPolicyDecisionStillNeedsExplicitExactOneShotCommitmentPermit() {
        val task = task(maxPrice = "200.00")
        val proposal = proposal("120.00")
        val decision = CallPlanEngine().decide(plan(task, proposal), "oferta 120 zł", 0)
        val workflow = activeWorkflow(task)
        val gate = CallCommitmentGate { "permit-exact-plan" }
        val outputPolicy = CallRealtimeAgentOutputApprovalPolicy(workflow, gate)

        val policyDecision = workflow.evaluateProposal(decision.proposal())

        assertEquals(CallPolicyAction.AUTONOMOUSLY_ALLOWED, policyDecision.action())
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        assertFalse(gate.hasAuthorization())
        assertTrue(gate.consume("permit-exact-plan").isFailure)
        assertEquals(
            CallRealtimeOutputDecision.RELEASE,
            outputPolicy.evaluate(partId, "Czy potwierdza pani ten termin?"),
        )

        val authorization = gate.authorize(decision.proposal())

        assertTrue(gate.hasAuthorization())
        assertEquals(
            CallRealtimeOutputDecision.DROP,
            outputPolicy.evaluate(partId, "Potwierdzam i rezerwuję."),
        )
        assertTrue(gate.consume("wrong-token").isFailure)
        assertTrue(gate.hasAuthorization())

        val consumed = gate.consume(authorization.value)
        assertTrue(consumed.isSuccess)
        assertSame(proposal, consumed.getOrThrow())
        assertFalse(gate.hasAuthorization())
        assertTrue(gate.consume(authorization.value).isFailure)
        assertEquals(
            CallRealtimeOutputDecision.RELEASE,
            outputPolicy.evaluate(partId, "Dziękuję."),
        )
    }

    private fun plan(task: CallTask, proposal: CallProposal) =
        CallPlan(
            task,
            CallResolvedTarget("Clinic A", "+48123456789"),
            emptyList(),
            emptyList(),
            listOf(CallPlanProposalRule("offer", setOf("oferta 120 zł"), proposal)),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )

    private fun activeWorkflow(task: CallTask) =
        CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(CallResolvedTarget("Clinic A", "+48123456789"))
            markDialing()
            markCallActive()
        }

    private fun task(maxPrice: String) =
        CallTask(
            "Clinic A",
            "book",
            "consultation",
            CallConstraints(
                emptyList(),
                MoneyAmount(BigDecimal(maxPrice), "PLN"),
                EnumSet.of(CallPaymentMode.PRIVATE),
            ),
            CallPreferences.none(),
            emptyMap(),
        )

    private fun proposal(amount: String) =
        CallProposal(
            ZonedDateTime.of(2026, 9, 22, 14, 0, 0, 0, ZoneId.of("Europe/Warsaw")),
            MoneyAmount(BigDecimal(amount), "PLN"),
            CallPaymentMode.PRIVATE,
            "Clinic A",
            "Wrocław",
        )
}
