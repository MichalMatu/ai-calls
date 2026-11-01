package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

class CampaignAuthorizationPolicyBoundaryTest {
    private val request = CampaignExecutionRequest(
        target = "*100", action = "SET_SERVICE", service = CallService.CLIR,
        enabled = true, accountScope = "test-account",
        disclosureFields = setOf(IdentityFieldId.PHONE),
    )

    private val grant = CampaignAuthorizationGrant(
        grantId = "grant", version = 1, createdAtEpochMs = 1000,
        expiresAtEpochMs = 3000, revokedAtEpochMs = null,
        accountScope = "test-account", allowedTargets = setOf("*100"),
        allowedActions = setOf("SET_SERVICE"), allowedServices = setOf(CallService.CLIR),
        allowedEffectValues = setOf(true),
        allowedDisclosureFields = setOf(IdentityFieldId.PHONE),
        maxAttempts = 1, attemptsUsed = 1, issuerEvidenceRef = "local-ui:test",
    )

    @Test fun reservedFinalAttemptCanContinueUntilExpiryButCannotAuthorizeAnother() {
        assertEquals(
            CampaignAuthorizationDenialReason.ATTEMPT_LIMIT_REACHED,
            (CampaignAuthorizationPolicy.authorize(grant, request, 2000) as
                CampaignAuthorizationDecision.Denied).reason,
        )
        assertTrue(
            CampaignAuthorizationPolicy.authorize(grant, request, 2000,
                attemptAlreadyReserved = true) is CampaignAuthorizationDecision.Authorized,
        )
    }

    @Test fun inFlightReservationStillFailsOnRevocationExpiryOrWrongAccount() {
        listOf(
            Triple(grant.copy(revokedAtEpochMs = 1900L), request, 2000L),
            Triple(grant, request, 3000L),
            Triple(grant, request.copy(accountScope = "another"), 2000L),
            Triple(grant, request, 999L),
        ).forEach { (g, req, now) ->
            assertTrue(
                CampaignAuthorizationPolicy.authorize(g, req, now,
                    attemptAlreadyReserved = true) is CampaignAuthorizationDecision.Denied,
            )
        }
    }
}
