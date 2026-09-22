package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPlanHelperSuggestion
import pl.michalmatu.aicallbridge.agent.CallPlanHelperValidator

class OrangeManualNetworkSelectionSeedTest {
    @Test
    fun `orange manual network selection seed uses one reviewed non committing utterance`() {
        val scenario = GateCLiveCallScenarioFactory.create(
            GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
            OrangeLiveAction.MANUAL_NETWORK_SELECTION,
        )
        val observedRoot =
            "dzień dobry jestem max twój wirtualny asystent orange nasza rozmowa jest nagrywana " +
                "chętnie pomogę powiedz w jakiej sprawie dzwonisz"

        val match = scenario.phraseMatrix.match(observedRoot)
        assertNotNull(match)
        val decision = CallPlanHelperValidator().validate(
            scenario.callPlan,
            CallPlanHelperSuggestion(checkNotNull(match).ruleId),
            0,
        )
        assertEquals(CallPlanAction.SAY, decision.action())
        assertEquals("Jak włączyć ręczny wybór sieci operatora?", decision.text())
        assertEquals(0, scenario.callPlan.proposalRules().size)
        assertEquals(0, scenario.callPlan.completionRules().size)
    }
}
