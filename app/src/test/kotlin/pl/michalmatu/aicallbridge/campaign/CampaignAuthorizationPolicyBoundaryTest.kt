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
        revokedAtEpochMs = null,
        accountScope = "test-account", allowedTargets = setOf("*100"),
        allowedActions = setOf("SET_SERVICE"), allowedServices = setOf(CallService.CLIR),
        allowedEffectValues = setOf(true),
        allowedDisclosureFields = setOf(IdentityFieldId.PHONE),
        attemptsUsed = 1, issuerEvidenceRef = "local-ui:test",
    )

    @Test fun previousReservationsNeverExhaustUnchangedScope() {
        assertTrue(
            CampaignAuthorizationPolicy.authorize(grant, request)
                is CampaignAuthorizationDecision.Authorized,
        )
        assertTrue(
            CampaignAuthorizationPolicy.authorize(
                grant.copy(attemptsUsed = Int.MAX_VALUE), request,
            ) is CampaignAuthorizationDecision.Authorized,
        )
    }

    @Test fun inFlightReservationStillFailsOnRevocationOrWrongAccount() {
        listOf(
            Triple(grant.copy(revokedAtEpochMs = 1900L), request, 2000L),
            Triple(grant, request.copy(accountScope = "another"), 2000L),
        ).forEach { (g, req, now) ->
            assertTrue(
                CampaignAuthorizationPolicy.authorize(g, req)
                    is CampaignAuthorizationDecision.Denied,
            )
        }
    }
}
