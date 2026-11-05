package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

internal data class CampaignAuthorizationGrant(
    val grantId: String,
    val version: Int,
    val createdAtEpochMs: Long,
    val revokedAtEpochMs: Long?,
    val accountScope: String,
    val allowedTargets: Set<String>,
    val allowedActions: Set<String>,
    val allowedServices: Set<CallService>,
    val allowedEffectValues: Set<Boolean>,
    val allowedDisclosureFields: Set<IdentityFieldId>,
    val attemptsUsed: Int,
    val recordCallEvidence: Boolean = false,
    val issuerEvidenceRef: String,
) {
    init {
        require(grantId.isNotBlank())
        require(version > 0)
        require(createdAtEpochMs >= 0L)
        require(revokedAtEpochMs == null || revokedAtEpochMs >= 0L)
        require(accountScope.isNotBlank())
        require(allowedTargets.isNotEmpty() && allowedTargets.none { it.isBlank() })
        require(allowedActions.isNotEmpty() && allowedActions.none { it.isBlank() })
        require(allowedServices.isNotEmpty())
        require(allowedEffectValues.isNotEmpty())
        require(attemptsUsed >= 0)
        require(issuerEvidenceRef.isNotBlank())
    }

    /** Internal reservation evidence, not an authorization quota. */
    fun withReservedAttempt(): CampaignAuthorizationGrant =
        copy(attemptsUsed = if (attemptsUsed == Int.MAX_VALUE) Int.MAX_VALUE else attemptsUsed + 1)

    fun revoked(nowEpochMs: Long): CampaignAuthorizationGrant =
        copy(revokedAtEpochMs = nowEpochMs)

    override fun toString(): String =
        "CampaignAuthorizationGrant(id=$grantId, scope=REDACTED, reservation_record=REDACTED)"
}

internal data class CampaignExecutionRequest(
    val target: String,
    val action: String,
    val service: CallService,
    val enabled: Boolean,
    val accountScope: String,
    val disclosureFields: Set<IdentityFieldId>,
)

internal enum class CampaignAuthorizationDenialReason {
    GRANT_MISSING,
    GRANT_REVOKED,
    ACCOUNT_SCOPE_MISMATCH,
    TARGET_NOT_ALLOWED,
    ACTION_NOT_ALLOWED,
    SERVICE_NOT_ALLOWED,
    EFFECT_VALUE_NOT_ALLOWED,
    DISCLOSURE_NOT_ALLOWED,
}

internal sealed interface CampaignAuthorizationDecision {
    data class Authorized(
        val grant: CampaignAuthorizationGrant,
    ) : CampaignAuthorizationDecision

    data class Denied(
        val reason: CampaignAuthorizationDenialReason,
    ) : CampaignAuthorizationDecision
}

internal object CampaignAuthorizationPolicy {
    fun matchesExactTask(
        grant: CampaignAuthorizationGrant?, request: CampaignExecutionRequest,
    ): Boolean = grant != null && authorize(grant, request) is CampaignAuthorizationDecision.Authorized &&
        grant.allowedTargets == setOf(request.target) && grant.allowedActions == setOf(request.action) &&
        grant.allowedServices == setOf(request.service) && grant.allowedEffectValues == setOf(request.enabled) &&
        grant.allowedDisclosureFields == request.disclosureFields

    fun authorize(
        grant: CampaignAuthorizationGrant?,
        request: CampaignExecutionRequest,
    ): CampaignAuthorizationDecision {
        if (grant == null) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.GRANT_MISSING,
            )
        }
        if (grant.revokedAtEpochMs != null) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.GRANT_REVOKED,
            )
        }
        if (request.accountScope != grant.accountScope) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.ACCOUNT_SCOPE_MISMATCH,
            )
        }
        if (request.target !in grant.allowedTargets) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.TARGET_NOT_ALLOWED,
            )
        }
        if (request.action !in grant.allowedActions) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.ACTION_NOT_ALLOWED,
            )
        }
        if (request.service !in grant.allowedServices) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.SERVICE_NOT_ALLOWED,
            )
        }
        if (request.enabled !in grant.allowedEffectValues) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.EFFECT_VALUE_NOT_ALLOWED,
            )
        }
        if (!grant.allowedDisclosureFields.containsAll(request.disclosureFields)) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.DISCLOSURE_NOT_ALLOWED,
            )
        }
        return CampaignAuthorizationDecision.Authorized(grant)
    }
}
