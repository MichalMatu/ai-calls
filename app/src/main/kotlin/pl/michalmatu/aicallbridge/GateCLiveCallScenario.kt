package pl.michalmatu.aicallbridge

import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanFallback
import pl.michalmatu.aicallbridge.agent.CallPlanRule
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.localcall.PhraseMatrix
import pl.michalmatu.aicallbridge.localcall.PhraseMatrixRule

internal data class GateCLiveCallScenario(
    val workflow: CallWorkflow,
    val callPlan: CallPlan,
    val phraseMatrix: PhraseMatrix,
)

/**
 * Deliberately tiny live-call diagnostic plan for the allowlisted Orange support target.
 *
 * It authorizes exactly one reviewed greeting response and no proposal/completion action. Unknown
 * or changed counterparty speech fails closed to TAKE_OVER; this scenario never grants dialing
 * authority and never enables a model fallback.
 */
internal object GateCLiveCallScenarioFactory {
    const val GREETING_RULE_ID = "orange-greeting"
    private const val GREETING_FACT_KEY = "orange-greeting-response"
    private const val REVIEWED_GREETING = "orange dzień dobry jestem max twój wi"
    private const val REVIEWED_GREETING_FULL =
        "orange dzień dobry jestem max twój wirtualny asystent"

    fun create(targetDialAddress: String): GateCLiveCallScenario {
        val normalizedTarget = targetDialAddress.trim()
        require(normalizedTarget.isNotEmpty()) { "target_dial_address_must_not_be_blank" }

        val task = CallTask(
            "Controlled Orange Gate C diagnostic",
            "Reply only with the reviewed diagnostic greeting; do not transact or commit",
            "Orange support",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            mapOf(GREETING_FACT_KEY to "Dzień dobry."),
        )
        val target = CallResolvedTarget("Orange support", normalizedTarget)
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
        workflow.resolveTarget(target)

        val callPlan = CallPlan(
            task,
            target,
            listOf(
                CallPlanRule(
                    GREETING_RULE_ID,
                    setOf(REVIEWED_GREETING, REVIEWED_GREETING_FULL),
                    GREETING_FACT_KEY,
                ),
            ),
            CallPlanFallback.TAKE_OVER,
        )
        val phraseMatrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = GREETING_RULE_ID,
                    phrases = setOf(REVIEWED_GREETING),
                    aliases = setOf(REVIEWED_GREETING_FULL),
                ),
            ),
        )

        return GateCLiveCallScenario(
            workflow = workflow,
            callPlan = callPlan,
            phraseMatrix = phraseMatrix,
        )
    }
}
