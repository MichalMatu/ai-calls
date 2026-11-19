package pl.michalmatu.aicallbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode

class IncomingHomeSummaryTest {
    @Test fun manualIsNeverAdvertisedAsAiReady() {
        assertEquals(R.string.home_incoming_mode_manual,
            IncomingHomeSummary.resource(AnsweringMode.MANUAL, true, true))
    }
    @Test fun aiWithoutSelectedAvailableSimReportsRequiredAction() {
        assertEquals(R.string.home_incoming_sim_unavailable,
            IncomingHomeSummary.resource(AnsweringMode.AI, false, true))
    }
    @Test fun aiWithoutBinderHostDoesNotClaimReady() {
        assertEquals(R.string.home_incoming_audio_unavailable,
            IncomingHomeSummary.resource(AnsweringMode.AI, true, false))
    }
    @Test fun aiWithVerifiedPrerequisitesIsReportedAsConfigured() {
        assertEquals(R.string.home_incoming_mode_ready,
            IncomingHomeSummary.resource(AnsweringMode.AI, true, true))
    }
}
