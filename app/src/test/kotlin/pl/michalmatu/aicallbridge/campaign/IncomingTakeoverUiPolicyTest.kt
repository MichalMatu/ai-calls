package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingTakeoverUiPolicyTest {
    @Test fun incomingAiOwnershipOffersHumanTakeover() {
        listOf("ANSWERING", "STARTING", "AI", "ENDING:conversation_completed").forEach {
            assertTrue(it, IncomingTakeoverUiPolicy.canTakeOver(it))
        }
    }

    @Test fun manualOutgoingRingingAndDisconnectedStatesHideTakeover() {
        listOf(null, "", "MANUAL", "MANUAL:gsm_shell_host_unavailable",
            "RINGING", "ENDED", "NOT_ASSISTANT", "AI_fake").forEach {
            assertFalse(it ?: "null", IncomingTakeoverUiPolicy.canTakeOver(it))
        }
    }
}
