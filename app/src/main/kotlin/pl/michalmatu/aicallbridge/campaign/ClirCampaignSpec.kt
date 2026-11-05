package pl.michalmatu.aicallbridge.campaign

import java.util.UUID
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

internal object ClirCampaignSpec {
    const val TARGET = "*100"
    const val ACTION = "SET_SERVICE"
    const val GRANT_VERSION = 1

    fun enableRequest(accountScope: String) = CampaignExecutionRequest(
        target = TARGET,
        action = ACTION,
        service = CallService.CLIR,
        enabled = true,
        accountScope = accountScope,
        disclosureFields = setOf(IdentityFieldId.PHONE),
    )

    fun acceptEnableTask(
        accountScope: String,
        nowEpochMs: Long,
        grantId: String = UUID.randomUUID().toString(),
        recordCallEvidence: Boolean = false,
        existing: CampaignAuthorizationGrant? = null,
    ): CampaignAuthorizationGrant {
        if (existing != null && existing.recordCallEvidence == recordCallEvidence &&
            CampaignAuthorizationPolicy.matchesExactTask(existing, enableRequest(accountScope))
        ) return existing
        return CampaignAuthorizationGrant(
            grantId = grantId,
            version = GRANT_VERSION,
            createdAtEpochMs = nowEpochMs,
            revokedAtEpochMs = null,
            accountScope = accountScope,
            allowedTargets = setOf(TARGET),
            allowedActions = setOf(ACTION),
            allowedServices = setOf(CallService.CLIR),
            allowedEffectValues = setOf(true),
            allowedDisclosureFields = setOf(IdentityFieldId.PHONE),
            attemptsUsed = 0,
            recordCallEvidence = recordCallEvidence,
            issuerEvidenceRef = "local-task-start:$grantId",
        )
    }
}
