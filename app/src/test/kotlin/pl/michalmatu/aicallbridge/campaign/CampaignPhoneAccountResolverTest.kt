package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignPhoneAccountResolverTest {
    @Test fun selectedAccountCanBeCorrectEvenWhenAnotherSimExists() {
        assertTrue(CampaignPhoneAccountResolver.accountMatchesDefault(101, 101))
        assertFalse(CampaignPhoneAccountResolver.accountMatchesDefault(102, 101))
        assertFalse(CampaignPhoneAccountResolver.accountMatchesDefault(-1, -1))
    }
}
