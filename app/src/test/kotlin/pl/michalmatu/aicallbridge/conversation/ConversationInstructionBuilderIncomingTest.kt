package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationInstructionBuilderIncomingTest {
    @Test fun incomingCallerFeedbackMustBeAddressedInsteadOfRepeatingQuestion() {
        val compiled = ConversationInstructionBuilder.build(
            ConversationPreparation.incoming(),
            "Przyjmij wiadomość dla właściciela telefonu.",
            ConversationResponseFormat.TOOL_REPLY,
        )
        assertTrue(compiled.contains("odpowiedz najpierw na tę uwagę"))
        assertTrue(compiled.contains("nie powtarzaj tego samego pytania"))
        assertTrue(compiled.contains("Zwracaj się neutralnie"))
        assertTrue(compiled.contains("nie zgaduj płci rozmówcy"))
        assertTrue(compiled.contains("Jeśli raz poprosiłaś o treść wiadomości"))
        assertTrue(compiled.contains(ConversationInstructionBuilder.INCOMING_OPENING))
        assertTrue(compiled.contains("Nie witaj się ponownie"))
    }

    @Test fun outgoingCallDoesNotInheritIncomingOnlyGuidance() {
        val compiled = ConversationInstructionBuilder.build(
            ConversationPreparation.outgoing(),
            "Zapytaj o procedurę.",
            ConversationResponseFormat.TOOL_REPLY,
        )
        assertFalse(compiled.contains("Jeśli raz poprosiłaś o treść wiadomości"))
        assertFalse(compiled.contains(ConversationInstructionBuilder.INCOMING_OPENING))
    }
}
