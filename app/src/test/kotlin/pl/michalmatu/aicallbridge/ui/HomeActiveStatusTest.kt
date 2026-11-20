package pl.michalmatu.aicallbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.campaign.CampaignLivePhase

class HomeActiveStatusTest {
    @Test fun idleHidesStop() {
        val state = HomeActiveStatus.resolve(CampaignLivePhase.INACTIVE, false, false)
        assertFalse(state.showStop)
        assertEquals(R.string.home_activity_idle, state.label)
    }
    @Test fun activeIncomingCallShowsStopWithoutOutgoingTask() {
        val state = HomeActiveStatus.resolve(CampaignLivePhase.INACTIVE, true, false)
        assertTrue(state.showStop)
        assertEquals(R.string.home_activity_incoming, state.label)
    }
    @Test fun preparingWaitingAndAttemptShowStop() {
        for (phase in listOf(CampaignLivePhase.PREPARING,
                CampaignLivePhase.WAITING_FOR_PHONE,
                CampaignLivePhase.ATTEMPT_IN_PROGRESS,
                CampaignLivePhase.SUPERVISED_AUDIO)) {
            assertTrue(HomeActiveStatus.resolve(phase, false, false).showStop)
        }
    }
    @Test fun activeTaskIdKeepsStopVisibleDuringRuntimeTransition() {
        val state = HomeActiveStatus.resolve(CampaignLivePhase.INACTIVE, false, true)
        assertTrue(state.showStop)
        assertEquals(R.string.home_activity_outgoing, state.label)
    }
}
