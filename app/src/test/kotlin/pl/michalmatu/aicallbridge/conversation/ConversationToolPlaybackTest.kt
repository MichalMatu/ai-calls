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
            override fun onComplete(text: String) { assertEquals("Dziękuję, do widzenia.", text) }
            override fun onError(reason: String) = error(reason)
        })
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
