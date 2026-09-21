package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPlanDecision
import pl.michalmatu.aicallbridge.agent.CallPolicyAction
import pl.michalmatu.aicallbridge.agent.CallPolicyDecision
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

class CallPlanFinalTurnRouteMapperTest {
    @Test
    fun `say maps to exact candidate without structured result`() {
        val result = CallPlanTurnResult(CallPlanDecision.say("Dokładna odpowiedź", "known-fact"), null)

        val mapped = CallPlanFinalTurnRouteMapper.map(result)

        assertEquals(TextCallFinalTurnRoute.Candidate("Dokładna odpowiedź"), mapped.route)
        assertNull(mapped.structuredResult)
    }

    @Test
    fun `all structured plan actions map to consumed and preserve exact result`() {
        val outcome = CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "done",
            null,
            null,
            null,
            null,
            null,
            null,
        )
        val proposal = CallProposal(null, null, null, "Clinic A", "Wrocław")
        val policy = CallPolicyDecision(CallPolicyAction.AUTONOMOUSLY_ALLOWED, emptyList())
        val results = listOf(
            CallPlanTurnResult(CallPlanDecision.askRepeat(), null),
            CallPlanTurnResult(CallPlanDecision.proposal(proposal, "offer"), policy),
            CallPlanTurnResult(CallPlanDecision.complete(outcome, "done"), null),
            CallPlanTurnResult(CallPlanDecision.takeOver("fallback"), null),
        )

        results.forEach { result ->
            val mapped = CallPlanFinalTurnRouteMapper.map(result)

            assertSame(TextCallFinalTurnRoute.Consumed, mapped.route)
            assertSame(result, mapped.structuredResult)
        }
    }
}
