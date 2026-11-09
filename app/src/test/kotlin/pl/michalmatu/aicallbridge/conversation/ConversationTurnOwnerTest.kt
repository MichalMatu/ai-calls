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

    @Test fun stopRevokesOtherwiseMatchingDeferredCallback() {
        val owner = ConversationTurnOwner(2, 6L, 3L)
        assertFalse(owner.isCurrent(2, 6L, 3L, finished = true))
    }
}
