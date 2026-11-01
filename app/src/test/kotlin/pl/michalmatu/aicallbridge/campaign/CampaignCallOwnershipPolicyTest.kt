package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignCallOwnershipPolicyTest {
    @Test fun genericTelecomCannotProveCallOwnership() {
        assertFalse(CampaignCallOwnershipPolicy.allowsAutonomousDial(
            CampaignCallOwnershipProof(false, false),
        ))
        assertFalse(CampaignCallOwnershipPolicy.allowsAutonomousDial(
            CampaignCallOwnershipProof(true, false),
        ))
        assertFalse(CampaignCallOwnershipPolicy.allowsAutonomousDial(
            CampaignCallOwnershipProof(false, true),
        ))
        assertTrue(CampaignCallOwnershipPolicy.allowsAutonomousDial(
            CampaignCallOwnershipProof(true, true),
        ))
    }
}
