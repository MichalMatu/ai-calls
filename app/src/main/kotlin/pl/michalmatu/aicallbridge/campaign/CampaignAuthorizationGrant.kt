package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

internal data class CampaignAuthorizationGrant(
    val grantId: String,
    val version: Int,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val revokedAtEpochMs: Long?,
    val accountScope: String,
    val allowedTargets: Set<String>,
    val allowedActions: Set<String>,
    val allowedServices: Set<CallService>,
    val allowedEffectValues: Set<Boolean>,
    val allowedDisclosureFields: Set<IdentityFieldId>,
    val maxAttempts: Int,
    val attemptsUsed: Int,
    val issuerEvidenceRef: String,
) {
    init {
        require(grantId.isNotBlank())
        require(version > 0)
        require(createdAtEpochMs >= 0L)
        require(expiresAtEpochMs > createdAtEpochMs)
        require(revokedAtEpochMs == null || revokedAtEpochMs >= createdAtEpochMs)
        require(accountScope.isNotBlank())
        require(allowedTargets.isNotEmpty() && allowedTargets.none { it.isBlank() })
        require(allowedActions.isNotEmpty() && allowedActions.none { it.isBlank() })
        require(allowedServices.isNotEmpty())
        require(allowedEffectValues.isNotEmpty())
        require(maxAttempts > 0)
        require(attemptsUsed in 0..maxAttempts)
        require(issuerEvidenceRef.isNotBlank())
    }

    fun withReservedAttempt(): CampaignAuthorizationGrant {
        require(attemptsUsed < maxAttempts) { "campaign_attempt_limit_reached" }
        return copy(attemptsUsed = attemptsUsed + 1)
    }

    fun revoked(nowEpochMs: Long): CampaignAuthorizationGrant =
        copy(revokedAtEpochMs = nowEpochMs)

    override fun toString(): String =
        "CampaignAuthorizationGrant(id=$grantId, scope=REDACTED, attempts=$attemptsUsed/$maxAttempts)"
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
    GRANT_EXPIRED,
    ATTEMPT_LIMIT_REACHED,
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
    fun authorize(
        grant: CampaignAuthorizationGrant?,
        request: CampaignExecutionRequest,
        nowEpochMs: Long,
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
        if (nowEpochMs >= grant.expiresAtEpochMs) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.GRANT_EXPIRED,
            )
        }
        if (grant.attemptsUsed >= grant.maxAttempts) {
            return CampaignAuthorizationDecision.Denied(
                CampaignAuthorizationDenialReason.ATTEMPT_LIMIT_REACHED,
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
