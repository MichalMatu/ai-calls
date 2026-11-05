package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

class CampaignAuthorizationPolicyTest {
    @Test
    fun acceptedTaskAndCancellationDoNotDependOnWallClockOrdering() {
        val accepted = grant().copy(createdAtEpochMs = Long.MAX_VALUE)
        assertTrue(CampaignAuthorizationPolicy.authorize(accepted, request())
            is CampaignAuthorizationDecision.Authorized)
        val stopped = accepted.revoked(2_000L)
        assertTrue(CampaignAuthorizationPolicy.authorize(stopped, request())
            is CampaignAuthorizationDecision.Denied)
    }

    @Test
    fun acceptedTaskContinuesAfterTwentyFourHoursWithoutAnotherApproval() {
        val task = ClirCampaignSpec.acceptEnableTask("test-sim", 1_000L)
        val decision = CampaignAuthorizationPolicy.authorize(task,
            ClirCampaignSpec.enableRequest("test-sim"))
        assertTrue(decision is CampaignAuthorizationDecision.Authorized)
    }

    @Test
    fun exactScopedRequestIsAuthorized() {
        val decision = CampaignAuthorizationPolicy.authorize(
            grant = grant(),
            request = request(),
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
    fun revokedTasksFailClosed() {
        assertDenied(
            grant = grant().copy(revokedAtEpochMs = 1_500L),
            request = request(),
            expected = CampaignAuthorizationDenialReason.GRANT_REVOKED,
        )
    }

    @Test
    fun furtherAttemptsRemainAuthorizedInsideSameScope() {
        val initial = grant()
        val first = initial.withReservedAttempt()
        val second = first.withReservedAttempt()
        val third = second.withReservedAttempt()

        assertEquals(1, first.attemptsUsed)
        assertEquals(2, second.attemptsUsed)
        assertEquals(3, third.attemptsUsed)
        assertEquals(4, third.withReservedAttempt().attemptsUsed)
        assertTrue(CampaignAuthorizationPolicy.authorize(
            grant = initial.copy(attemptsUsed = 100),
            request = request(),
        ) is CampaignAuthorizationDecision.Authorized)
        assertEquals(Int.MAX_VALUE,
            initial.copy(attemptsUsed = Int.MAX_VALUE).withReservedAttempt().attemptsUsed)
    }

    @Test
    fun startReusesTheSameAcceptedTaskButNeverWidensScopeOrRevivesByRetry() {
        val accepted = ClirCampaignSpec.acceptEnableTask("test-sim", 1_000L)
        val later = 1_000L + 7L * 24 * 60 * 60 * 1_000
        assertTrue(accepted === ClirCampaignSpec.acceptEnableTask("test-sim", later, existing = accepted))
        val cancelled = accepted.revoked(2_000L)
        assertTrue(CampaignAuthorizationPolicy.authorize(cancelled,
            ClirCampaignSpec.enableRequest("test-sim")) is CampaignAuthorizationDecision.Denied)
        val otherAccount = ClirCampaignSpec.acceptEnableTask("other-sim", later, existing = accepted)
        assertTrue(otherAccount.grantId != accepted.grantId)
        assertEquals("other-sim", otherAccount.accountScope)
        assertTrue(CampaignAuthorizationPolicy.authorize(accepted,
            ClirCampaignSpec.enableRequest("other-sim")) is CampaignAuthorizationDecision.Denied)
    }

    private fun assertDenied(
        grant: CampaignAuthorizationGrant,
        request: CampaignExecutionRequest,
        expected: CampaignAuthorizationDenialReason,
    ) {
        val decision = CampaignAuthorizationPolicy.authorize(
            grant = grant,
            request = request,
        )
        assertTrue(decision is CampaignAuthorizationDecision.Denied)
        assertEquals(expected, (decision as CampaignAuthorizationDecision.Denied).reason)
    }

    private fun grant() = CampaignAuthorizationGrant(
        grantId = "grant-1",
        version = 1,
        createdAtEpochMs = 1_000L,
        revokedAtEpochMs = null,
        accountScope = "voice-subscription:1",
        allowedTargets = setOf("*100"),
        allowedActions = setOf("SET_SERVICE"),
        allowedServices = setOf(CallService.CLIR),
        allowedEffectValues = setOf(true),
        allowedDisclosureFields = setOf(IdentityFieldId.PHONE),
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
