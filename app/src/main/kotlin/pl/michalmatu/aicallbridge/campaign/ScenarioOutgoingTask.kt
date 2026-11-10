package pl.michalmatu.aicallbridge.campaign

import java.util.UUID
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog

/** One exact authorized outgoing enquiry; it cannot approve purchases, bookings or disclosure. */
internal data class ScenarioOutgoingTask(
    val grantId: String,
    val target: String,
    val subscriptionId: Int,
    val accountScope: String,
    val scenarioId: String,
    val title: String,
    val goal: String,
    val brief: String,
) {
    init {
        require(grantId.isNotBlank())
        require(target.matches(Regex("\\+[1-9][0-9]{6,14}"))) { "scenario_target_e164_required" }
        require(subscriptionId >= 0 && accountScope.isNotBlank()) { "scenario_sim_required" }
        require(ScenarioCatalog.byId(scenarioId) != null) { "scenario_template_missing" }
        require(title.length in 2..140)
        require(goal.length in 5..300)
        require(brief.length in 5..3500) { "scenario_brief_invalid" }
    }
    fun validated() = copy()

    /** The exact action pins the chosen template as well as the phone number and SIM. */
    fun request() = CampaignExecutionRequest(
        target = target, action = ACTION_PREFIX + scenarioId, service = CallService.INQUIRY,
        enabled = true, accountScope = accountScope, disclosureFields = emptySet(),
    )
    fun grant(nowEpochMs: Long) = CampaignAuthorizationGrant(
        grantId = grantId, version = 1, createdAtEpochMs = nowEpochMs,
        revokedAtEpochMs = null, accountScope = accountScope,
        allowedTargets = setOf(target), allowedActions = setOf(request().action),
        allowedServices = setOf(CallService.INQUIRY), allowedEffectValues = setOf(true),
        allowedDisclosureFields = emptySet(), attemptsUsed = 0, recordCallEvidence = false,
        issuerEvidenceRef = "scenario-ui:" + grantId,
    )
    override fun toString(): String =
        "ScenarioOutgoingTask(template=" + scenarioId + ",target=REDACTED,brief=REDACTED)"

    companion object {
        const val ACTION_PREFIX = "INQUIRE_SCENARIO_"
        fun create(
            target: String, subscriptionId: Int, accountScope: String,
            scenarioId: String, title: String, goal: String, brief: String,
        ) = ScenarioOutgoingTask(
            UUID.randomUUID().toString(), target.trim(),
            subscriptionId, accountScope, scenarioId, title.trim(), goal.trim(), brief.trim(),
        )
    }
}
