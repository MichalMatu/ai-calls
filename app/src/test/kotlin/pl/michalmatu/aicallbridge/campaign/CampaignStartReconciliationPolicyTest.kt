package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignStartReconciliationPolicyTest {
    private val saved = CampaignContinuationState(
        grantId = "grant-A",
        accountScope = "voice-SIM-A",
        nextAttemptAtEpochMs = 80_000L,
        incompleteAttempts = 7,
    )

    @Test fun duplicateStartOfActiveCallNeverCreatesSecondAttempt() {
        assertEquals(
            CampaignStartDisposition.ALREADY_RUNNING,
            CampaignStartReconciliationPolicy.decide(saved, "grant-A", "voice-SIM-A", true),
        )
    }

    @Test fun restartOfTheSameGrantPreservesTheSavedRetryDeadline() {
        assertEquals(
            CampaignStartDisposition.REUSE_SAVED,
            CampaignStartReconciliationPolicy.decide(saved, "grant-A", "voice-SIM-A", false),
        )
        assertEquals(80_000L, saved.nextAttemptAtEpochMs)
        assertEquals(7, saved.incompleteAttempts)
    }

    @Test fun newApprovedGrantWaitsForOldOwnedCallToFinish() {
        assertEquals(
            CampaignStartDisposition.QUEUE_REPLACEMENT,
            CampaignStartReconciliationPolicy.decide(saved, "grant-B", "voice-SIM-A", true),
        )
        assertEquals(
            CampaignStartDisposition.BEGIN_NEW,
            CampaignStartReconciliationPolicy.decide(saved, "grant-B", "voice-SIM-A", false),
        )
    }

    @Test fun changedVoiceScopeIsNeverReusedAsTheSameCampaign() {
        assertEquals(
            CampaignStartDisposition.QUEUE_REPLACEMENT,
            CampaignStartReconciliationPolicy.decide(saved, "grant-A", "voice-SIM-B", true),
        )
        assertEquals(
            CampaignStartDisposition.BEGIN_NEW,
            CampaignStartReconciliationPolicy.decide(saved, "grant-A", "voice-SIM-B", false),
        )
    }

    @Test fun freshAuthorizedTaskIsStartedOnlyWhenNoOldOwnedCallRuns() {
        assertEquals(
            CampaignStartDisposition.BEGIN_NEW,
            CampaignStartReconciliationPolicy.decide(null, "grant-A", "voice-SIM-A", false),
        )
        assertEquals(
            CampaignStartDisposition.QUEUE_REPLACEMENT,
            CampaignStartReconciliationPolicy.decide(null, "grant-A", "voice-SIM-A", true),
        )
    }
}
