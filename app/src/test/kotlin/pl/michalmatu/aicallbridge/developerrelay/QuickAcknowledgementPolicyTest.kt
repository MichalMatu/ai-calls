package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAcknowledgementPolicyTest {
    private fun mayPlay(
        authorized: Boolean = true,
        supervised: Boolean = false,
        speech: Boolean = true,
        ready: Boolean = true,
        turn: Int = 1,
        sent: Int = 0,
        final: Int = 0,
    ): Boolean = QuickAcknowledgementPolicy.shouldPlay(
        authorized, supervised, speech, ready, turn, sent, final,
    )

    @Test fun spokenEndMayReleaseOneNeutralOptionalAcknowledgement() {
        assertTrue(mayPlay())
        assertTrue(QuickAcknowledgementPolicy.TEXT.contains("zaczekać"))
    }

    @Test fun neverTalkOnRevokedGrantStoppedCallOrSupervisorPilot() {
        assertFalse(mayPlay(authorized = false))
        assertFalse(mayPlay(supervised = true))
        assertFalse(mayPlay(speech = false))
        assertFalse(mayPlay(ready = false))
    }

    @Test fun neverRepeatTurnOrTalkOverAReadyFullAnswer() {
        assertFalse(mayPlay(turn = 0))
        assertFalse(mayPlay(sent = 1))
        assertFalse(mayPlay(final = 1))
        assertTrue(mayPlay(turn = 2, sent = 1, final = 1))
    }
}
