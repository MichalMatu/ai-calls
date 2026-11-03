package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignContinuationPolicyTest {
    @Test fun retriesBackOffWithoutArbitraryCallQuota() {
        assertEquals(30_000L, CampaignContinuationPolicy.retryDelayMs(1))
        assertEquals(60_000L, CampaignContinuationPolicy.retryDelayMs(2))
        assertEquals(120_000L, CampaignContinuationPolicy.retryDelayMs(3))
        assertEquals(240_000L, CampaignContinuationPolicy.retryDelayMs(4))
        assertEquals(300_000L, CampaignContinuationPolicy.retryDelayMs(5))
        assertEquals(300_000L, CampaignContinuationPolicy.retryDelayMs(10_000))
        assertEquals(300_000L, CampaignContinuationPolicy.retryDelayMs(Int.MAX_VALUE))
    }
}
