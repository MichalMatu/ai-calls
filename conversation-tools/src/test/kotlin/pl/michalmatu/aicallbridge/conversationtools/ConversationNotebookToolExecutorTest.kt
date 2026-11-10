package pl.michalmatu.aicallbridge.conversationtools

import org.junit.Assert.*
import org.junit.Test

class ConversationNotebookToolExecutorTest {
    @Test fun notePersistsOnceAndNeverEndsCallOrExecutesFutureActions() {
        var notes = 0
        var calls = 0
        val events = mutableListOf<ConversationToolEvent>()
        val executor = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = true
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int): Boolean { calls++; return true }
            override fun saveNote(turn: Int, proposal: ConversationNoteProposal): Boolean {
                notes++
                return proposal.kind == ConversationNoteKind.CALENDAR_PROPOSAL
            }
        })
        val payload = ConversationNoteProposal(ConversationNoteKind.CALENDAR_PROPOSAL, "Propozycja bez rezerwacji")
        repeat(3) {
            assertEquals(ConversationToolResult.REQUESTED,
                executor.execute(2, ConversationToolId.SAVE_NOTE, payload, events::add))
        }
        assertEquals(1, notes)
        assertEquals(0, calls)
        assertTrue(events.any { it.stage == ConversationToolStage.CONFIRMED })
        assertTrue(events.any { it.stage == ConversationToolStage.REUSED })
    }

    @Test fun idleSafetyCanEndCallEvenWhenModelToolIsDisabledButNotAfterTakeover() {
        var authority = true
        var ends = 0
        val port = object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = false
            override fun authorized(tool: ConversationToolId) = authority
            override fun requestEndCall(turn: Int): Boolean { ends++; return true }
        }
        val executor = ConversationToolExecutor(port)
        assertEquals(ConversationToolResult.DISABLED,
            executor.execute(1, ConversationToolId.END_CALL) {})
        assertEquals(ConversationToolResult.REQUESTED, executor.executeIdleClose(2) {})
        authority = false
        assertEquals(ConversationToolResult.NOT_AUTHORIZED, executor.executeIdleClose(3) {})
        assertEquals(1, ends)
    }

    @Test fun permissionsAndMissingPayloadFailClosed() {
        var notes = 0
        var allowed = false
        val port = object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = true
            override fun authorized(tool: ConversationToolId) = allowed
            override fun requestEndCall(turn: Int) = false
            override fun saveNote(turn: Int, proposal: ConversationNoteProposal): Boolean {
                notes++; return true
            }
        }
        val executor = ConversationToolExecutor(port)
        val payload = ConversationNoteProposal(ConversationNoteKind.NOTE, "Test")
        assertEquals(ConversationToolResult.NOT_AUTHORIZED,
            executor.execute(1, ConversationToolId.SAVE_NOTE, payload) {})
        assertEquals(0, notes)
        allowed = true
        assertEquals(ConversationToolResult.NOT_AUTHORIZED,
            executor.execute(2, ConversationToolId.SAVE_NOTE) {})
        assertEquals(0, notes)
        assertEquals(ConversationToolResult.REQUESTED,
            executor.execute(3, ConversationToolId.SAVE_NOTE, payload) {})
        assertEquals(1, notes)
    }
}
