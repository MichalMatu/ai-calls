package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignAgentLaunchPolicyTest {
    @Test
    fun existingGrantAndDialerRoleAreReadyWithoutAnyUiAction() {
        assertEquals(
            CampaignAgentReadiness.READY,
            CampaignAgentLaunchPolicy.decide(
                permissionsPresent = true,
                voiceSubscriptionPresent = true,
                grantAuthorized = true,
                dialerRoleReady = true,
            ),
        )
    }

    @Test
    fun absentOrRevokedGrantNeverEnablesAdbDrivenStart() {
        assertEquals(
            CampaignAgentReadiness.NO_VALID_EXACT_GRANT,
            CampaignAgentLaunchPolicy.decide(true, true, false, true),
        )
    }

    @Test
    fun missingAndroidDialerRoleCannotBeReplacedByAdb() {
        assertEquals(
            CampaignAgentReadiness.DIALER_ROLE_REQUIRED,
            CampaignAgentLaunchPolicy.decide(true, true, true, false),
        )
    }

    @Test
    fun missingPermissionsOrSimCannotAuthorizeCampaign() {
        assertEquals(
            CampaignAgentReadiness.MISSING_RUNTIME_PERMISSION,
            CampaignAgentLaunchPolicy.decide(false, true, true, true),
        )
        assertEquals(
            CampaignAgentReadiness.NO_VOICE_SUBSCRIPTION,
            CampaignAgentLaunchPolicy.decide(true, false, true, true),
        )
    }
}
