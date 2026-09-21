package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPlanHelperSuggestion
import pl.michalmatu.aicallbridge.agent.CallPlanHelperValidator
import pl.michalmatu.aicallbridge.agent.CallWorkflowState

class GateCLiveCallScenarioTest {
    @Test
    fun `orange diagnostic scenario binds exact target and reviewed greeting only`() {
        val scenario = GateCLiveCallScenarioFactory.create(" 510100100 ")
        val snapshot = scenario.workflow.snapshot()

        assertEquals(CallWorkflowState.READY_TO_DIAL, snapshot.state())
        assertEquals("510100100", snapshot.resolvedTarget()?.dialAddress())
        assertSame(snapshot.task(), scenario.callPlan.task())
        assertEquals(snapshot.resolvedTarget(), scenario.callPlan.resolvedTarget())

        val historical = scenario.phraseMatrix.match("Orange, dzień dobry. Jestem Max, twój wi")
        assertNotNull(historical)
        assertEquals(GateCLiveCallScenarioFactory.GREETING_RULE_ID, historical?.ruleId)

        val fullGreeting = scenario.phraseMatrix.match(
            "Orange dzień dobry jestem Max twój wirtualny asystent",
        )
        assertEquals(GateCLiveCallScenarioFactory.GREETING_RULE_ID, fullGreeting?.ruleId)

        assertNull(scenario.phraseMatrix.match("proszę podać numer PESEL"))

        val decision = CallPlanHelperValidator().validate(
            scenario.callPlan,
            CallPlanHelperSuggestion(checkNotNull(historical).ruleId),
            0,
        )
        assertEquals(CallPlanAction.SAY, decision.action())
        assertEquals("Dzień dobry.", decision.text())
        assertEquals(GateCLiveCallScenarioFactory.GREETING_RULE_ID, decision.ruleId())
        assertEquals(0, scenario.callPlan.proposalRules().size)
        assertEquals(0, scenario.callPlan.completionRules().size)
    }

    @Test
    fun `orange diagnostic scenario rejects blank target`() {
        assertThrows(IllegalArgumentException::class.java) {
            GateCLiveCallScenarioFactory.create("   ")
        }
    }
}
