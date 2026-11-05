package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignAgentLaunchPolicyTest {
    @Test fun explicitOwnerTaskNeedsNoPreviouslyGrantedMobileApproval() {
        assertEquals(CampaignAgentReadiness.READY, CampaignAgentLaunchPolicy.decide(true, true, true))
    }
    @Test fun missingAndroidDialerRoleCannotBeReplacedByTaskIntake() {
        assertEquals(CampaignAgentReadiness.DIALER_ROLE_REQUIRED, CampaignAgentLaunchPolicy.decide(true, true, false))
    }
    @Test fun missingPermissionsOrSimCannotBeReplacedByTaskIntake() {
        assertEquals(CampaignAgentReadiness.MISSING_RUNTIME_PERMISSION, CampaignAgentLaunchPolicy.decide(false, true, true))
        assertEquals(CampaignAgentReadiness.NO_VOICE_SUBSCRIPTION, CampaignAgentLaunchPolicy.decide(true, false, true))
    }
}
