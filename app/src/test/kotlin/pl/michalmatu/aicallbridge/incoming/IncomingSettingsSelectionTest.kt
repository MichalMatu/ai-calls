package pl.michalmatu.aicallbridge.incoming

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode

class IncomingSettingsSelectionTest {
    @Test fun savedSelectionRemainsCleanUntilAnyFieldChanges() {
        val saved = IncomingSettingsSelection(AnsweringMode.AI, "test-account", true)
        assertEquals(saved, saved.copy())
        assertNotEquals(saved, saved.copy(mode = AnsweringMode.MANUAL))
        assertNotEquals(saved, saved.copy(accountKey = "other-account"))
        assertNotEquals(saved, saved.copy(recordEvidence = false))
        assertEquals(saved, saved.copy(mode = AnsweringMode.MANUAL).copy(mode = AnsweringMode.AI))
    }
}
