package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTurnOwnerTest {
    @Test fun matchingTurnEpochAndGenerationMayComplete() {
        val owner = ConversationTurnOwner(4, 33L, 11L)
        assertTrue(owner.isCurrent(4, 33L, 11L, finished = false))
    }

    @Test fun bargeInInvalidatesPriorDeferredTranscriptWithoutCancelingNewTurn() {
        val old = ConversationTurnOwner(4, 33L, 11L)
        assertFalse(old.isCurrent(4, 34L, 11L, finished = false))
        val next = ConversationTurnOwner(5, 35L, 11L)
        assertTrue(next.isCurrent(5, 35L, 11L, finished = false))
        assertFalse(old.isCurrent(5, 35L, 11L, finished = false))
    }

    @Test fun staleRxFailureCannotStopResumedMediaOrNewSttTurn() {
        val owner = ConversationTurnOwner(2, 6L, 3L)
        assertFalse(owner.isCurrent(2, 6L, 4L, finished = false))
        assertFalse(owner.isCurrent(3, 6L, 3L, finished = false))
        assertFalse(owner.isCurrent(2, 7L, 3L, finished = false))
    }

    @Test fun nextSilenceWindowInvalidatesPreviousRxErrorWithSameTurnAndEpoch() {
        val turn = ConversationTurnOwner(2, 6L, 3L)
        val first = ConversationRxCaptureOwner(turn, attempt = 10L)
        val next = ConversationRxCaptureOwner(turn, attempt = 11L)
        assertTrue(first.isCurrent(2, 6L, 3L, finished = false, attemptNow = 10L))
        assertFalse(first.isCurrent(2, 6L, 3L, finished = false, attemptNow = 11L))
        assertTrue(next.isCurrent(2, 6L, 3L, finished = false, attemptNow = 11L))
        assertFalse(next.isCurrent(2, 7L, 3L, finished = false, attemptNow = 11L))
    }

    @Test fun oldRxErrorCannotOverrideTakeoverOrResumedMediaGeneration() {
        val old = ConversationRxCaptureOwner(ConversationTurnOwner(3, 8L, 2L), attempt = 6L)
        assertFalse(old.isCurrent(3, 8L, 2L, finished = true, attemptNow = 6L))
        assertFalse(old.isCurrent(3, 8L, 3L, finished = false, attemptNow = 6L))
        assertFalse(old.isCurrent(3, 8L, 2L, finished = false, attemptNow = 7L))
    }

    @Test fun stopRevokesOtherwiseMatchingDeferredCallback() {
        val owner = ConversationTurnOwner(2, 6L, 3L)
        assertFalse(owner.isCurrent(2, 6L, 3L, finished = true))
    }
}
