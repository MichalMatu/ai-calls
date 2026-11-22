package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.*
import pl.michalmatu.aicallbridge.textagent.*

class ConversationToolPlaybackTest {
    private class Fixture {
        var authorized = true
        var enabled = true
        var requests = 0
        val events = mutableListOf<ConversationToolEvent>()
        val executor = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = enabled
            override fun authorized(tool: ConversationToolId) = authorized
            override fun requestEndCall(turn: Int): Boolean { requests++; return true }
        })
        val session = ToolCallingConversationSession(object : TextCallAgentBackend {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                listener.onComplete("""{"reply":"Dziękuję, do widzenia.","tool":"end_call"}""")
            }
            override fun cancel() = Unit
        }, TextOutputApprovalPolicy { TextOutputDecision.RELEASE }, executor,
            ConversationRuntimeHooks({ authorized }, { 7L }, { 1 }, {}, ConversationDecisionSink { _,_,_,_,_ -> }, events::add),
            "TEST_MODEL", null)
        fun generate() = session.backend.generate("synthetic", object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                assertEquals("Dziękuję, do widzenia.", text)
                assertEquals(TextOutputDecision.RELEASE, session.approvalPolicy.evaluate(text))
            }
            override fun onError(reason: String) = error(reason)
        })
    }

    @Test fun missingModelNotesStillSaveOneUpdatedNoteBeforeEndCall() {
        var turn = 1
        var enabled = true
        val persisted = linkedMapOf<String, String>()
        var disconnects = 0
        val events = mutableListOf<ConversationToolEvent>()
        val outputs = ArrayDeque(listOf(
            """{"reply":"Rozumiem.","tool":"none","note":"","kind":"NOTE"}""",
            """{"reply":"Dziękuję, do widzenia.","tool":"end_call","note":"","kind":"NOTE"}"""
        ))
        val backend = object : TextCallAgentBackend {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                listener.onComplete(outputs.removeFirst())
            }
            override fun cancel() = Unit
        }
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) =
                if (tool == ConversationToolId.SAVE_NOTE) enabled else true
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int): Boolean { disconnects++; return true }
            override fun saveAutomaticNote(turn: Int, proposal: ConversationNoteProposal): Boolean {
                persisted["call-1:auto"] = proposal.content
                return true
            }
        })
        val session = ToolCallingConversationSession(backend,
            TextOutputApprovalPolicy { TextOutputDecision.RELEASE }, tools,
            ConversationRuntimeHooks({ true }, { 1L }, { turn }, {},
                ConversationDecisionSink { _, _, _, _, _ -> }, events::add),
            "TEST_MODEL", null)
        fun deliver() {
            session.backend.generate("synthetic", object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    assertEquals(TextOutputDecision.RELEASE, session.approvalPolicy.evaluate(text))
                }
                override fun onError(reason: String) = error(reason)
            })
        }

        session.onTranscript(1, "Spotkanie zaplanowano w poniedziałek.")
        deliver()
        assertTrue(persisted.isEmpty())
        assertNull(session.takeOutcome(ConversationOutcomeBoundary.TRANSCRIPT))
        assertNull(session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(1, persisted.size)
        assertTrue(persisted.values.single().contains("poniedziałek"))

        turn = 2
        session.beginTurn()
        session.onTranscript(2, "Poprawka: spotkanie jest jednak we wtorek.")
        deliver()
        assertEquals(0, disconnects)
        assertEquals(ConversationOutcome.Completed, session.takeOutcome(
            ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(1, persisted.size)
        assertTrue(persisted.values.single().contains("we wtorek"))
        assertEquals(1, disconnects)
        val notes = events.filter { it.tool == ConversationToolId.SAVE_NOTE }
        assertEquals(2, notes.count { it.stage == ConversationToolStage.CONFIRMED })
        val finalNoteIndex = events.indexOfLast {
            it.tool == ConversationToolId.SAVE_NOTE && it.stage == ConversationToolStage.CONFIRMED
        }
        val endIndex = events.indexOfFirst {
            it.tool == ConversationToolId.END_CALL && it.stage == ConversationToolStage.REQUESTED
        }
        assertTrue(finalNoteIndex >= 0 && finalNoteIndex < endIndex)
        assertNull(session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
    }

    @Test fun explicitNoteAndEndCallCanExecuteInTheSameApprovedTurn() {
        var notes = 0
        var end = 0
        val events = mutableListOf<ConversationToolEvent>()
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = true
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int): Boolean { end++; return true }
            override fun saveAutomaticNote(turn: Int, proposal: ConversationNoteProposal): Boolean {
                assertEquals("Spotkanie jutro.", proposal.content)
                notes++; return true
            }
        })
        val backend = object : TextCallAgentBackend {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                listener.onComplete("""{"reply":"Do widzenia.","tool":"end_call","note":"Spotkanie jutro.","kind":"NOTE"}""")
            }
            override fun cancel() = Unit
        }
        val session = ToolCallingConversationSession(backend,
            TextOutputApprovalPolicy { TextOutputDecision.RELEASE }, tools,
            ConversationRuntimeHooks({ true }, { 1L }, { 1 }, {},
                ConversationDecisionSink { _, _, _, _, _ -> }, events::add),
            "TEST_MODEL", null)
        session.backend.generate("synthetic", object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                assertEquals(TextOutputDecision.RELEASE, session.approvalPolicy.evaluate(text))
            }
            override fun onError(reason: String) = error(reason)
        })
        assertEquals(0, notes)
        assertEquals(0, end)
        assertEquals(ConversationOutcome.Completed, session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(1, notes)
        assertEquals(1, end)
    }

    @Test fun disabledNotesAndDroppedOutputNeverSaveMaterial() {
        var saved = 0
        val events = mutableListOf<ConversationToolEvent>()
        var notesEnabled = false
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) =
                tool != ConversationToolId.SAVE_NOTE || notesEnabled
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int) = true
            override fun saveAutomaticNote(turn: Int, proposal: ConversationNoteProposal): Boolean {
                saved++; return true
            }
        })
        val session = ToolCallingConversationSession(object : TextCallAgentBackend {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                listener.onComplete("""{"reply":"Dziękuję.","tool":"none","note":"","kind":"NOTE"}""")
            }
            override fun cancel() = Unit
        }, TextOutputApprovalPolicy { TextOutputDecision.RELEASE }, tools,
            ConversationRuntimeHooks({ true }, { 1L }, { 1 }, {},
                ConversationDecisionSink { _, _, _, _, _ -> }, events::add),
            "TEST_MODEL", null)
        session.onTranscript(1, "Proszę przekazać wiadomość o spotkaniu w środę.")
        session.backend.generate("synthetic", object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                session.approvalPolicy.evaluate(text)
            }
            override fun onError(reason: String) = error(reason)
        })
        assertNull(session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(0, saved)
        assertEquals(ConversationToolResult.DISABLED, events.last().result)
        assertFalse(session.finalDiagnostics().any { it.contains("auto_note_persisted_revision=1") })
    }

    @Test fun approvedSpeechThroughActualTextControllerPreservesToolUntilPlayback() {
        val f = Fixture()
        val controller = TextCallTurnController(f.session.backend, f.session.approvalPolicy)
        var released: String? = null
        controller.submitUserText("synthetic caller farewell", object : TextCallTurnController.Listener {
            override fun onApprovedResponse(text: String) { released = text }
            override fun onDroppedResponse() = error("unexpected_drop")
            override fun onError(reason: String) = error(reason)
        })
        assertEquals("Dziękuję, do widzenia.", released)
        assertEquals(0, f.requests)
        assertNull(f.session.takeOutcome(ConversationOutcomeBoundary.TRANSCRIPT))
        assertEquals(ConversationOutcome.Completed, f.session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(1, f.requests)
        controller.close()
    }

    @Test fun evenSynchronousModelCannotDispatchAtTranscriptOrTtsStage() {
        val f = Fixture(); f.generate()
        assertNull(f.session.takeOutcome(ConversationOutcomeBoundary.TRANSCRIPT))
        assertEquals(0, f.requests)
        assertEquals(ConversationOutcome.Completed, f.session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(1, f.requests); assertTrue(f.session.awaitToolCompletion)
        assertNull(f.session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE)); assertEquals(1, f.requests)
    }
    @Test fun takeoverBetweenGeneratedFarewellAndPlaybackPreventsDispatch() {
        val f = Fixture(); f.generate(); f.authorized = false
        assertTrue(f.session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE) is ConversationOutcome.Failed)
        assertEquals(0, f.requests); assertFalse(f.session.awaitToolCompletion)
    }
    @Test fun toolDisableWhileSpeechIsPlayingKeepsCallWithoutDispatch() {
        val f = Fixture(); f.generate(); f.enabled = false
        assertNull(f.session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(0, f.requests); assertFalse(f.session.awaitToolCompletion)
        assertEquals(ConversationToolResult.DISABLED, f.events.last().result)
    }
}
