package pl.michalmatu.aicallbridge.campaign

import java.util.UUID
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

internal object ClirCampaignSpec {
    const val TARGET = "*100"
    const val ACTION = "SET_SERVICE"
    const val GRANT_VERSION = 1
    const val MAX_ATTEMPTS = 5
    const val GRANT_TTL_MS = 24L * 60L * 60L * 1_000L

    fun enableRequest(accountScope: String) = CampaignExecutionRequest(
        target = TARGET,
        action = ACTION,
        service = CallService.CLIR,
        enabled = true,
        accountScope = accountScope,
        disclosureFields = setOf(IdentityFieldId.PHONE),
    )

    fun newEnableGrant(
        accountScope: String,
        nowEpochMs: Long,
        grantId: String = UUID.randomUUID().toString(),
        issuerEvidenceRef: String = "local-ui:$grantId",
    ) = CampaignAuthorizationGrant(
        grantId = grantId,
        version = GRANT_VERSION,
        createdAtEpochMs = nowEpochMs,
        expiresAtEpochMs = nowEpochMs + GRANT_TTL_MS,
        revokedAtEpochMs = null,
        accountScope = accountScope,
        allowedTargets = setOf(TARGET),
        allowedActions = setOf(ACTION),
        allowedServices = setOf(CallService.CLIR),
        allowedEffectValues = setOf(true),
        allowedDisclosureFields = setOf(IdentityFieldId.PHONE),
        maxAttempts = MAX_ATTEMPTS,
        attemptsUsed = 0,
        issuerEvidenceRef = issuerEvidenceRef,
    )
}
