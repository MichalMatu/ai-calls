package pl.michalmatu.aicallbridge.campaign

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A deterministic, off-call acceptance test on Samsung hardware.
 * Never starts the service, calls Telecom or accesses the actual grant store.
 */
@RunWith(AndroidJUnit4::class)
class CampaignStartReconciliationContractTest {
    private val waiting = CampaignContinuationState(
        grantId = "approved-grant",
        accountScope = "sim-one",
        nextAttemptAtEpochMs = 480_000L,
        incompleteAttempts = 19,
    )

    @Test fun duplicateStartDuringCallCannotRedial() {
        assertEquals(
            CampaignStartDisposition.ALREADY_RUNNING,
            CampaignStartReconciliationPolicy.decide(waiting, "approved-grant", "sim-one", true),
        )
    }

    @Test fun processRestorationRetainsRetryDueTimeAndAttemptCount() {
        assertEquals(
            CampaignStartDisposition.REUSE_SAVED,
            CampaignStartReconciliationPolicy.decide(waiting, "approved-grant", "sim-one", false),
        )
        assertEquals(480_000L, waiting.nextAttemptAtEpochMs)
        assertEquals(19, waiting.incompleteAttempts)
    }

    @Test fun newGrantCannotOverlapAnExistingOwnedCall() {
        assertEquals(
            CampaignStartDisposition.QUEUE_REPLACEMENT,
            CampaignStartReconciliationPolicy.decide(waiting, "new-grant", "sim-one", true),
        )
    }
}
