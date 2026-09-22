package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPlanHelperSuggestion
import pl.michalmatu.aicallbridge.agent.CallPlanHelperValidator

class OrangeVoiceQualityProblemSeedTest {
    @Test
    fun `orange voice quality problem seed uses one reviewed non committing utterance`() {
        val scenario = GateCLiveCallScenarioFactory.create(
            GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
            OrangeLiveAction.VOICE_QUALITY_PROBLEM,
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
        assertEquals("Podczas rozmów zanika głos.", decision.text())
        assertEquals(0, scenario.callPlan.proposalRules().size)
        assertEquals(0, scenario.callPlan.completionRules().size)
    }
}
