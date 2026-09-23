package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy
import pl.michalmatu.aicallbridge.agent.CallPlanRule
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow

class PhraseMatrixTemperatureProductTurnRouterTest {
    @Test
    fun `warm natural variation routes through existing call plan owner`() {
        val task = CallTask(
            "operator",
            "answer bounded confirmation",
            "support",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            mapOf("answer" to "Tak, sprawa dotyczy numeru, z którego dzwonię."),
        )
        val target = CallResolvedTarget("support", "allowlisted")
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }
        val plan = CallPlan(
            task,
            target,
            listOf(
                CallPlanRule(
                    "confirm-current-line",
                    setOf("czy sprawa dotyczy numeru z którego dzwonisz"),
                    "answer",
                ),
            ),
            emptyList(),
            emptyList(),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        val router = PhraseMatrixProductTurnRouter(
            PhraseMatrix(
                listOf(
                    PhraseMatrixRule(
                        "confirm-current-line",
                        setOf("czy sprawa dotyczy numeru z którego dzwonisz"),
                    ),
                ),
            ),
            CallPlanTurnCoordinator(plan, workflow),
        )

        val result = router.handleFinalTranscript(
            "czy ta sprawa dotyczy numeru z którego teraz dzwonisz",
            0,
        )

        assertNull(result?.match)
        assertEquals(PhraseResponseTemperatureBand.WARM, result?.temperature?.band)
        assertEquals("confirm-current-line", result?.temperature?.ruleId)
        assertTrue((result?.temperature?.confidence ?: 0.0) >= 0.70)
        assertEquals(CallPlanAction.SAY, result?.turnResult?.decision()?.action())
        assertEquals(
            "Tak, sprawa dotyczy numeru, z którego dzwonię.",
            result?.turnResult?.decision()?.text(),
        )
    }

    @Test
    fun `uncertain or ambiguous temperature remains unhandled for model fallback`() {
        val task = CallTask(
            "operator",
            "bounded",
            "support",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            mapOf("phone" to "telefon", "invoice" to "faktura"),
        )
        val target = CallResolvedTarget("support", "allowlisted")
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }
        val plan = CallPlan(
            task,
            target,
            listOf(
                CallPlanRule("phone-number", setOf("czy chodzi o ten numer telefonu"), "phone"),
                CallPlanRule("invoice-number", setOf("czy chodzi o ten numer faktury"), "invoice"),
            ),
            emptyList(),
            emptyList(),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        val router = PhraseMatrixProductTurnRouter(
            PhraseMatrix(
                listOf(
                    PhraseMatrixRule("phone-number", setOf("czy chodzi o ten numer telefonu")),
                    PhraseMatrixRule("invoice-number", setOf("czy chodzi o ten numer faktury")),
                ),
            ),
            CallPlanTurnCoordinator(plan, workflow),
        )

        assertNull(router.handleFinalTranscript("czy chodzi o ten numer", 0))
    }
}
