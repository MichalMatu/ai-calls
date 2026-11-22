package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class ConversationAutoNoteCollectorTest {
    @Test fun selectsMaterialFactsAndCorrectionsNotGreetingsOrWholeTranscript() {
        val collector = ConversationAutoNoteCollector()
        collector.accept(1, "Dzień dobry, chciałbym zostawić wiadomość.")
        collector.accept(2, "Proszę przekazać, że spotkanie jest w poniedziałek.")
        collector.accept(3, "Poprawka: jednak we wtorek o dwunastej.")
        collector.accept(4, "Dziękuję, do widzenia.")
        val content = requireNotNull(collector.proposal()).content
        assertTrue(content.contains("poniedziałek"))
        assertTrue(content.contains("wtorek"))
        assertFalse(content.contains("Dziękuję, do widzenia"))
        assertTrue(collector.revision <= 3)
    }

    @Test fun englishMaterialAndSecretsAreFilteredConservatively() {
        val collector = ConversationAutoNoteCollector()
        collector.accept(1, "I'd like to leave a message.")
        assertNull(collector.proposal())
        collector.accept(2, "The appointment is on Thursday.")
        collector.accept(3, "My password is secret and my PIN is 1234.")
        collector.accept(4, "My bank account number is 1234567890.")
        collector.accept(5, "Goodbye.")
        assertEquals(1, collector.revision)
        assertEquals("The appointment is on Thursday.",
            collector.proposal()?.content)
        assertFalse(ConversationAutoNoteCollector.safe("Hasło to KWIAT"))
        assertFalse(ConversationAutoNoteCollector.safe("Numer 123456789"))
    }

    @Test fun repeatedTurnDoesNotCreateDuplicateFacts() {
        val collector = ConversationAutoNoteCollector()
        collector.accept(1, "Spotkanie odbędzie się w piątek.")
        collector.accept(1, "Spotkanie odbędzie się w piątek.")
        assertEquals(1, collector.revision)
        assertEquals(1, collector.proposal()?.content?.lines()?.size)
    }
}
