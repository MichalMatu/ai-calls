package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteProposal

class ConversationNotebookPolicyTest {
    @Test fun separateCallsAreAppendOnlyAndDuplicatesDoNotWriteAgain() {
        val first = ConversationNoteProposal(ConversationNoteKind.NOTE, "Syntetyczne ustalenie A")
        val later = ConversationNoteProposal(ConversationNoteKind.CALENDAR_PROPOSAL,
            "Syntetyczny termin do potwierdzenia")
        val empty = ConversationNotebookLibrary()
        val one = ConversationNotebookPolicy.append(empty, "incoming-abc", "INCOMING", 1, first, 1000L)
        val repeated = ConversationNotebookPolicy.append(one, "incoming-abc", "INCOMING", 1, first, 2000L)
        assertSame("idempotent retry must not change timestamp", one, repeated)
        val two = ConversationNotebookPolicy.append(repeated, "incoming-abc", "INCOMING", 3, later, 3000L)
        val three = ConversationNotebookPolicy.append(two, "outgoing-xyz", "OUTGOING", 1, first, 4000L)
        assertEquals(2, three.conversations.size)
        assertEquals(listOf("Syntetyczne ustalenie A", "Syntetyczny termin do potwierdzenia"),
            three.conversations.single { it.sessionId == "incoming-abc" }.entries.map { it.text })
        assertEquals(1, three.conversations.single { it.sessionId == "outgoing-xyz" }.entries.size)
        assertSame(three, ConversationNotebookPolicy.validate(three))
        assertFalse(three.toString().contains("Syntetyczny"))
        assertFalse(three.conversations.first().entries.first().toString().contains("Syntetyczny"))
    }

    @Test fun sameTurnCannotBeOverwrittenOrMovedToDifferentCallDirection() {
        val note = ConversationNoteProposal(ConversationNoteKind.NOTE, "Wiadomość testowa")
        val base = ConversationNotebookPolicy.append(
            ConversationNotebookLibrary(), "incoming-abc", "INCOMING", 1, note, 1000L)
        assertTrue(runCatching {
            ConversationNotebookPolicy.append(base, "incoming-abc", "INCOMING", 1,
                ConversationNoteProposal(ConversationNoteKind.TASK, "Inna wartość"), 1002L)
        }.isFailure)
        assertTrue(runCatching {
            ConversationNotebookPolicy.append(base, "incoming-abc", "OUTGOING", 2, note, 1002L)
        }.isFailure)
        assertTrue(runCatching {
            ConversationNotebookPolicy.append(base, "../bad", "INCOMING", 1, note, 1002L)
        }.isFailure)
        assertTrue(runCatching {
            ConversationNotebookPolicy.append(base, "outgoing-valid", "OUTGOING", 0, note, 1002L)
        }.isFailure)
    }

    @Test fun pendingActionsRemainTypedInertNotes() {
        val base = ConversationNotebookLibrary()
        val kinds = ConversationNoteKind.entries.toList()
        val out = kinds.foldIndexed(base) { i, state, kind ->
            ConversationNotebookPolicy.append(state, "outgoing-test", "OUTGOING", i + 1,
                ConversationNoteProposal(kind, "Tylko propozycja " + kind.name), 1200L + i)
        }
        assertEquals(kinds, out.conversations.single().entries.map { it.kind })
    }
}
