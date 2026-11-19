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

    @Test fun outgoingEnquiryClosesAfterGoalOrOutOfScopeVerification() {
        for (language in listOf("pl-PL", "en-US")) {
            val compiled = ConversationInstructionBuilder.build(
                ConversationPreparation.outgoing(),
                "Zapytaj o dostępność pomocy automatycznej.",
                ConversationResponseFormat.TOOL_REPLY, language,
            )
            assertTrue(compiled.contains("ZAKOŃCZENIE ZADANIA OUTGOING"))
            assertTrue(compiled.contains("tool=end_call"))
            assertTrue(compiled.contains("nie przedłużaj rozmowy"))
            assertTrue(compiled.contains("przed rozłączeniem"))
            assertTrue(compiled.contains("automat IVR"))
            assertTrue(compiled.contains("nie możesz tego zrobić"))
        }
    }

    @Test fun completionInstructionIsNotInjectedIntoIncomingOrPlainSpeech() {
        val incoming = ConversationInstructionBuilder.build(
            ConversationPreparation.incoming(), "Przyjmij wiadomość.",
            ConversationResponseFormat.TOOL_REPLY,
        )
        val plainOutgoing = ConversationInstructionBuilder.build(
            ConversationPreparation.outgoing(), "Zapytaj o ofertę.",
            ConversationResponseFormat.SPOKEN_TEXT,
        )
        assertFalse(incoming.contains("ZAKOŃCZENIE ZADANIA OUTGOING"))
        assertFalse(plainOutgoing.contains("ZAKOŃCZENIE ZADANIA OUTGOING"))
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
