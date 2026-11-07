package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignLiveStateResolverTest {
    private fun state(
        supervised: Boolean = false,
        waiting: Boolean = false,
        preparing: Boolean = false,
        requested: Boolean = false,
        running: Boolean = false,
    ) = CampaignLiveStateResolver.resolve(supervised, waiting, preparing, requested, running)

    @Test fun idleIsNotReinterpretedFromHistoricalEvent() {
        assertEquals(CampaignLivePhase.INACTIVE, state())
        // No persisted status string is accepted as input to this resolver.
    }

    @Test fun preparingAndWaitingAreNotMistakenForConnectedCall() {
        assertEquals(CampaignLivePhase.PREPARING, state(requested = true))
        assertEquals(CampaignLivePhase.PREPARING, state(preparing = true, requested = true))
        assertEquals(CampaignLivePhase.WAITING_FOR_PHONE, state(waiting = true, requested = true))
        assertEquals(CampaignLivePhase.ATTEMPT_IN_PROGRESS, state(running = true))
    }

    @Test fun supervisedPilotHasDistinctIdentityAndPrecedence() {
        assertEquals(CampaignLivePhase.SUPERVISED_AUDIO, state(supervised = true))
        assertEquals(CampaignLivePhase.SUPERVISED_AUDIO,
            state(supervised = true, requested = true, running = true))
    }
}
