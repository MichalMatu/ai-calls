package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.*
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision

class ExplicitSaveNotePolicyTest {
    @Test fun explicitRequestProducesBoundedNoteFromAdjacentCallerUtterance() {
        val note = ExplicitSaveNotePolicy.proposal(
            "Proszę zapisać tę notatkę i do widzenia.",
            "Zostawiam wiadomość: potrzebuję nowego terminu spotkania w przyszłym tygodniu."
        )
        assertNotNull(note)
        assertTrue(note!!.content.contains("nowego terminu"))
        assertFalse(note.content.contains("do widzenia"))
        assertTrue(note.content.length <= 900)
    }

    @Test fun directRequestWithContentNeedsNoPriorUtterance() {
        val note = ExplicitSaveNotePolicy.proposal(
            "Zapisz termin spotkania na przyszłą środę, najlepiej po południu.", null)
        assertNotNull(note)
        assertTrue(note!!.content.contains("termin spotkania"))
    }

    @Test fun discussionNegationSecretsOrBareRequestCannotAutoSave() {
        for (input in listOf(
            "Nie zapisuj tej informacji, to pomyłka.",
            "Powiedział: \"zapisz tę notatkę\" w przykładzie.",
            "Wspomnieliśmy o zapisie notatki.",
            "Zapisz notatkę.",
            "Zapisz hasło do konta 1234.",
        )) {
            assertNull(input, ExplicitSaveNotePolicy.proposal(input, null))
        }
        assertNull(ExplicitSaveNotePolicy.proposal(
            "Proszę zanotować tę wiadomość.", "Mój kod PIN to 1234"))
    }

    @Test fun missedSaveNoteAndApprovedEndCallBothExecuteAtPlaybackBoundary() {
        val model = FakeModel()
        val events = mutableListOf<ConversationToolEvent>()
        val stored = mutableListOf<ConversationNoteProposal>()
        var ends = 0
        var turn = 1
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = true
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int): Boolean { ends++; return true }
            override fun saveNote(turn: Int, proposal: ConversationNoteProposal): Boolean {
                stored += proposal
                return true
            }
        })
        val session = session(model, tools, { turn }, events::add)
        session.beginTurn()
        session.onTranscript(1,
            "Zostawiam wiadomość: chciałbym przełożyć spotkanie na przyszły tydzień.")
        respond(session, model,
            """{"reply":"Czy coś jeszcze mogę pomóc?","tool":"none","note":"","kind":"NOTE"}""")
        assertNull(session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertTrue(stored.isEmpty())

        turn = 2
        session.beginTurn()
        session.onTranscript(2, "Proszę zapisać tę notatkę i do widzenia.")
        respond(session, model,
            """{"reply":"Dziękuję, do widzenia.","tool":"end_call","note":"","kind":"NOTE"}""")
        // Approval alone never persists notes or disconnects.
        assertEquals(0, stored.size)
        assertEquals(0, ends)
        assertEquals(ConversationOutcome.Completed,
            session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(1, stored.size)
        assertTrue(stored.single().content.contains("przełożyć spotkanie"))
        assertEquals(1, ends)
        assertTrue(events.any {
            it.tool == ConversationToolId.SAVE_NOTE && it.stage == ConversationToolStage.CONFIRMED
        })
        assertTrue(events.any {
            it.tool == ConversationToolId.END_CALL && it.stage == ConversationToolStage.DISPATCHED
        })
    }

    @Test fun normalModelSaveNoteIsNeverDuplicatedByRecovery() {
        val model = FakeModel()
        var count = 0
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = true
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int) = true
            override fun saveNote(turn: Int, proposal: ConversationNoteProposal): Boolean {
                count++; return true
            }
        })
        val session = session(model, tools, { 1 }, {})
        session.beginTurn()
        session.onTranscript(1, "Zapisz termin spotkania na przyszłą środę.")
        respond(session, model,
            """{"reply":"Dobrze, zanotuję.","tool":"save_note","note":"Spotkanie w przyszłą środę","kind":"CALENDAR_PROPOSAL"}""")
        assertNull(session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(1, count)
    }

    @Test fun disabledSaveNoteNeverMutatesStorageOrBlocksEndCall() {
        val model = FakeModel()
        var count = 0
        var ends = 0
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = tool == ConversationToolId.END_CALL
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int): Boolean { ends++; return true }
            override fun saveNote(turn: Int, proposal: ConversationNoteProposal): Boolean {
                count++; return true
            }
        })
        val session = session(model, tools, { 1 }, {})
        session.beginTurn()
        session.onTranscript(1, "Zapisz termin spotkania na przyszłą środę.")
        respond(session, model,
            """{"reply":"Do widzenia.","tool":"end_call","note":"","kind":"NOTE"}""")
        assertEquals(ConversationOutcome.Completed,
            session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(0, count)
        assertEquals(1, ends)
    }

    private fun session(model: FakeModel, tools: ConversationToolExecutor,
        turn: () -> Int, sink: (ConversationToolEvent) -> Unit): ToolCallingConversationSession =
        ToolCallingConversationSession(
            model,
            TextOutputApprovalPolicy { TextOutputDecision.RELEASE },
            tools,
            ConversationRuntimeHooks(
                authorized = { true }, mediaGeneration = { 1L }, turn = turn,
                emit = {}, decisionSink = ConversationDecisionSink { _, _, _, _, _ -> },
                toolEventSink = sink,
            ), "TEST", null,
        )

    private fun respond(session: ToolCallingConversationSession, model: FakeModel, json: String) {
        var text: String? = null
        session.backend.generate("synthetic", object : TextCallAgentBackend.Listener {
            override fun onComplete(value: String) { text = value }
            override fun onError(reason: String) { throw AssertionError(reason) }
        })
        model.listener!!.onComplete(json)
        assertNotNull(text)
        assertEquals(TextOutputDecision.RELEASE, session.approvalPolicy.evaluate(text!!))
    }

    private class FakeModel : TextCallAgentBackend {
        var listener: TextCallAgentBackend.Listener? = null
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            this.listener = listener
        }
        override fun cancel() = Unit
    }
}
