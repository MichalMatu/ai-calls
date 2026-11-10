package pl.michalmatu.aicallbridge.incoming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantPolicy
import pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantState

class IncomingEndCallAuthorityTest {
    private val granted = IncomingAssistantPolicy("user-approval", AnsweringMode.AI, "sim-a", true)

    private fun permitted(
        state: IncomingAssistantState? = IncomingAssistantState.ENDING,
        generation: Long? = 7L,
        slotGeneration: Long = 7L,
        owned: Boolean = true,
        cancelled: Boolean = false,
        endToolRequested: Boolean = true,
        current: IncomingAssistantPolicy = granted,
    ): Boolean = IncomingEndCallAuthority.permits(
        state, generation, slotGeneration, owned, cancelled,
        endToolRequested, granted, current,
    )

    @Test fun onlyExactEndingGenerationWithLiveFullPolicyCanDisconnect() {
        assertTrue(permitted())
    }

    @Test fun sameSimNewConsentIdDoesNotAuthorizeOldEndCall() {
        assertFalse(permitted(current = granted.copy(id = "rotated")))
        assertFalse(permitted(current = granted.copy(recordEvidence = false)))
    }

    @Test fun modeAndSimChangesBlockEndCallEvenWithSameGeneration() {
        assertFalse(permitted(current = granted.copy(mode = AnsweringMode.MANUAL)))
        assertFalse(permitted(current = granted.copy(accountKey = "sim-b")))
    }

    @Test fun staleOrForeignSlotCannotDisconnectOwnedTelecomCall() {
        assertFalse(permitted(slotGeneration = 6L))
        assertFalse(permitted(generation = 8L))
        assertFalse(permitted(generation = null))
        assertFalse(permitted(owned = false))
    }

    @Test fun stoppedSessionAndUnrequestedEndCallAreRejected() {
        assertFalse(permitted(cancelled = true))
        assertFalse(permitted(endToolRequested = false))
        assertFalse(permitted(state = IncomingAssistantState.AI))
        assertFalse(permitted(state = IncomingAssistantState.MANUAL))
        assertFalse(permitted(state = IncomingAssistantState.ENDED))
    }
}
