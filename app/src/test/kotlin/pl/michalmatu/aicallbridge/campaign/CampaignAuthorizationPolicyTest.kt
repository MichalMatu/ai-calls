package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

class CampaignAuthorizationPolicyTest {
    @Test
    fun exactScopedRequestIsAuthorized() {
        val decision = CampaignAuthorizationPolicy.authorize(
            grant = grant(),
            request = request(),
            nowEpochMs = 2_000L,
        )

        assertTrue(decision is CampaignAuthorizationDecision.Authorized)
    }

    @Test
    fun inverseEffectValueIsRejected() {
        assertDenied(
            grant = grant(),
            request = request().copy(enabled = false),
            expected = CampaignAuthorizationDenialReason.EFFECT_VALUE_NOT_ALLOWED,
        )
    }

    @Test
    fun targetAndAccountWideningFailClosed() {
        assertDenied(
            grant = grant(),
            request = request().copy(target = "other"),
            expected = CampaignAuthorizationDenialReason.TARGET_NOT_ALLOWED,
        )
        assertDenied(
            grant = grant(),
            request = request().copy(accountScope = "voice-subscription:other"),
            expected = CampaignAuthorizationDenialReason.ACCOUNT_SCOPE_MISMATCH,
        )
    }

    @Test
    fun disclosureWideningFailsClosed() {
        assertDenied(
            grant = grant(),
            request = request().copy(
                disclosureFields = setOf(
                    IdentityFieldId.PHONE,
                    IdentityFieldId.DATE_OF_BIRTH,
                ),
            ),
            expected = CampaignAuthorizationDenialReason.DISCLOSURE_NOT_ALLOWED,
        )
    }

    @Test
    fun revokedExpiredAndExhaustedGrantsFailClosed() {
        assertDenied(
            grant = grant().copy(revokedAtEpochMs = 1_500L),
            request = request(),
            expected = CampaignAuthorizationDenialReason.GRANT_REVOKED,
        )
        assertDenied(
            grant = grant().copy(expiresAtEpochMs = 1_999L),
            request = request(),
            expected = CampaignAuthorizationDenialReason.GRANT_EXPIRED,
        )
        assertDenied(
            grant = grant().copy(attemptsUsed = 3),
            request = request(),
            expected = CampaignAuthorizationDenialReason.ATTEMPT_LIMIT_REACHED,
        )
    }

    @Test
    fun reservingAttemptIsMonotonicAndBounded() {
        val initial = grant()
        val first = initial.withReservedAttempt()
        val second = first.withReservedAttempt()
        val third = second.withReservedAttempt()

        assertEquals(1, first.attemptsUsed)
        assertEquals(2, second.attemptsUsed)
        assertEquals(3, third.attemptsUsed)
        runCatching { third.withReservedAttempt() }
            .onSuccess { throw AssertionError("expected attempt limit failure") }
    }

    private fun assertDenied(
        grant: CampaignAuthorizationGrant,
        request: CampaignExecutionRequest,
        expected: CampaignAuthorizationDenialReason,
    ) {
        val decision = CampaignAuthorizationPolicy.authorize(
            grant = grant,
            request = request,
            nowEpochMs = 2_000L,
        )
        assertTrue(decision is CampaignAuthorizationDecision.Denied)
        assertEquals(expected, (decision as CampaignAuthorizationDecision.Denied).reason)
    }

    private fun grant() = CampaignAuthorizationGrant(
        grantId = "grant-1",
        version = 1,
        createdAtEpochMs = 1_000L,
        expiresAtEpochMs = 10_000L,
        revokedAtEpochMs = null,
        accountScope = "voice-subscription:1",
        allowedTargets = setOf("*100"),
        allowedActions = setOf("SET_SERVICE"),
        allowedServices = setOf(CallService.CLIR),
        allowedEffectValues = setOf(true),
        allowedDisclosureFields = setOf(IdentityFieldId.PHONE),
        maxAttempts = 3,
        attemptsUsed = 0,
        issuerEvidenceRef = "local-ui:test",
    )

    private fun request() = CampaignExecutionRequest(
        target = "*100",
        action = "SET_SERVICE",
        service = CallService.CLIR,
        enabled = true,
        accountScope = "voice-subscription:1",
        disclosureFields = setOf(IdentityFieldId.PHONE),
    )
}
