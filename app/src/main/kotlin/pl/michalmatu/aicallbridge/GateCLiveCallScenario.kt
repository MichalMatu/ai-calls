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
 * Deliberately bounded live-call diagnostic plan for the exact allowlisted Orange support target.
 *
 * Every speech-producing explorer action maps to one reviewed utterance. OBSERVE_ONLY has no SAY
 * rules at all and therefore can only fail closed to TAKE_OVER after recording the final STT text.
 * This scenario never grants dialing, proposal, completion, transaction, or model authority.
 */
internal object GateCLiveCallScenarioFactory {
    const val ORANGE_SUPPORT_NUMBER = "510100100"
    const val GREETING_RULE_ID = "orange-greeting"
    const val MAX_ROOT_RULE_ID = "orange-max-root"
    const val INVOICE_STATUS_RULE_ID = "orange-invoice-status-root"
    const val INVOICE_TOPIC_RULE_ID = "orange-invoice-topic-root"
    const val INTERNET_PROBLEM_RULE_ID = "orange-internet-problem-root"
    const val OUTAGE_TOPIC_RULE_ID = "orange-outage-topic-root"
    const val WIFI_PROBLEM_RULE_ID = "orange-wifi-problem-root"
    const val COVERAGE_INFO_RULE_ID = "orange-coverage-info-root"
    const val ROAMING_INFO_RULE_ID = "orange-roaming-info-root"
    const val ROAMING_PRICES_RULE_ID = "orange-roaming-prices-root"

    private const val GREETING_FACT_KEY = "orange-greeting-response"
    private const val EXPLORER_FACT_KEY = "orange-explorer-response"
    private const val INVOICE_STATUS_FACT_KEY = "orange-invoice-status-response"
    private const val INVOICE_TOPIC_FACT_KEY = "orange-invoice-topic-response"
    private const val INTERNET_PROBLEM_FACT_KEY = "orange-internet-problem-response"
    private const val OUTAGE_TOPIC_FACT_KEY = "orange-outage-topic-response"
    private const val WIFI_PROBLEM_FACT_KEY = "orange-wifi-problem-response"
    private const val COVERAGE_INFO_FACT_KEY = "orange-coverage-info-response"
    private const val ROAMING_INFO_FACT_KEY = "orange-roaming-info-response"
    private const val ROAMING_PRICES_FACT_KEY = "orange-roaming-prices-response"
    private const val REVIEWED_GREETING = "orange dzień dobry jestem max twój wi"
    private const val REVIEWED_GREETING_FULL =
        "orange dzień dobry jestem max twój wirtualny asystent"
    private const val REVIEWED_MAX_ROOT_EVENING =
        "dobry wieczór jestem max twój wirtualny asystent orange nasza rozmowa jest nagrywana " +
            "chętnie pomogę powiedz w jakiej sprawie dzwonisz"
    private const val REVIEWED_MAX_ROOT_DAY =
        "dzień dobry jestem max twój wirtualny asystent orange nasza rozmowa jest nagrywana " +
            "chętnie pomogę powiedz w jakiej sprawie dzwonisz"

    private data class ReviewedTurn(
        val ruleId: String,
        val factKey: String,
        val phrases: Set<String>,
        val aliases: Set<String>,
        val response: String,
    )

    fun create(
        targetDialAddress: String,
        action: OrangeLiveAction = OrangeLiveAction.GREETING,
    ): GateCLiveCallScenario {
        val normalizedTarget = targetDialAddress.trim()
        require(normalizedTarget.isNotEmpty()) { "target_dial_address_must_not_be_blank" }
        require(normalizedTarget == ORANGE_SUPPORT_NUMBER) { "target_not_allowlisted_for_gate_c_live_probe" }

        val reviewedTurn = reviewedTurn(action)
        val task = CallTask(
            "Controlled Orange Gate C diagnostic",
            "Execute only reviewed Orange action ${action.wireId}; do not transact or commit",
            "Orange support",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            reviewedTurn?.let { mapOf(it.factKey to it.response) } ?: emptyMap(),
        )
        val target = CallResolvedTarget("Orange support", normalizedTarget)
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
        workflow.resolveTarget(target)

        val callPlanRules = reviewedTurn?.let { turn ->
            listOf(
                CallPlanRule(
                    turn.ruleId,
                    turn.phrases + turn.aliases,
                    turn.factKey,
                ),
            )
        } ?: emptyList()
        val phraseRules = reviewedTurn?.let { turn ->
            listOf(
                PhraseMatrixRule(
                    ruleId = turn.ruleId,
                    phrases = turn.phrases,
                    aliases = turn.aliases,
                ),
            )
        } ?: emptyList()

        return GateCLiveCallScenario(
            workflow = workflow,
            callPlan = CallPlan(
                task,
                target,
                callPlanRules,
                CallPlanFallback.TAKE_OVER,
            ),
            phraseMatrix = PhraseMatrix(phraseRules),
        )
    }

    private fun reviewedTurn(action: OrangeLiveAction): ReviewedTurn? = when (action) {
        OrangeLiveAction.GREETING -> ReviewedTurn(
            ruleId = GREETING_RULE_ID,
            factKey = GREETING_FACT_KEY,
            phrases = setOf(REVIEWED_GREETING),
            aliases = setOf(REVIEWED_GREETING_FULL),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.LIST_CAPABILITIES -> ReviewedTurn(
            ruleId = MAX_ROOT_RULE_ID,
            factKey = EXPLORER_FACT_KEY,
            phrases = setOf(REVIEWED_MAX_ROOT_EVENING),
            aliases = setOf(REVIEWED_MAX_ROOT_DAY),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.INVOICE_STATUS -> ReviewedTurn(
            ruleId = INVOICE_STATUS_RULE_ID,
            factKey = INVOICE_STATUS_FACT_KEY,
            phrases = setOf(REVIEWED_MAX_ROOT_EVENING),
            aliases = setOf(REVIEWED_MAX_ROOT_DAY),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.INVOICE_TOPIC -> ReviewedTurn(
            ruleId = INVOICE_TOPIC_RULE_ID,
            factKey = INVOICE_TOPIC_FACT_KEY,
            phrases = setOf(REVIEWED_MAX_ROOT_EVENING),
            aliases = setOf(REVIEWED_MAX_ROOT_DAY),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.INTERNET_PROBLEM -> ReviewedTurn(
            ruleId = INTERNET_PROBLEM_RULE_ID,
            factKey = INTERNET_PROBLEM_FACT_KEY,
            phrases = setOf(REVIEWED_MAX_ROOT_EVENING),
            aliases = setOf(REVIEWED_MAX_ROOT_DAY),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.OUTAGE_TOPIC -> ReviewedTurn(
            ruleId = OUTAGE_TOPIC_RULE_ID,
            factKey = OUTAGE_TOPIC_FACT_KEY,
            phrases = setOf(REVIEWED_MAX_ROOT_EVENING),
            aliases = setOf(REVIEWED_MAX_ROOT_DAY),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.WIFI_PROBLEM -> ReviewedTurn(
            ruleId = WIFI_PROBLEM_RULE_ID,
            factKey = WIFI_PROBLEM_FACT_KEY,
            phrases = setOf(REVIEWED_MAX_ROOT_EVENING),
            aliases = setOf(REVIEWED_MAX_ROOT_DAY),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.COVERAGE_INFO -> ReviewedTurn(
            ruleId = COVERAGE_INFO_RULE_ID,
            factKey = COVERAGE_INFO_FACT_KEY,
            phrases = setOf(REVIEWED_MAX_ROOT_EVENING),
            aliases = setOf(REVIEWED_MAX_ROOT_DAY),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.ROAMING_INFO -> ReviewedTurn(
            ruleId = ROAMING_INFO_RULE_ID,
            factKey = ROAMING_INFO_FACT_KEY,
            phrases = setOf(REVIEWED_MAX_ROOT_EVENING),
            aliases = setOf(REVIEWED_MAX_ROOT_DAY),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.ROAMING_PRICES -> ReviewedTurn(
            ruleId = ROAMING_PRICES_RULE_ID,
            factKey = ROAMING_PRICES_FACT_KEY,
            phrases = setOf(REVIEWED_MAX_ROOT_EVENING),
            aliases = setOf(REVIEWED_MAX_ROOT_DAY),
            response = checkNotNull(action.reviewedResponse),
        )
        OrangeLiveAction.OBSERVE_ONLY -> null
    }
}
