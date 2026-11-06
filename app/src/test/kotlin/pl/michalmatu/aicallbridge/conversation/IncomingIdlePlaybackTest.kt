package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolExecutor
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolPort
import pl.michalmatu.aicallbridge.textagent.*

class IncomingIdlePlaybackTest {
    @Test fun syntheticFarewellIsDispatchedOnceAfterApprovedPlayback() {
        var requests = 0
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = true
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int): Boolean { requests++; return true }
        })
        val session = ToolCallingConversationSession(
            object : TextCallAgentBackend {
                override fun generate(userText: String, listener: TextCallAgentBackend.Listener) = Unit
                override fun cancel() = Unit
            },
            TextOutputApprovalPolicy { TextOutputDecision.RELEASE },
            tools, ConversationRuntimeHooks({ true }, { 1L }, { 1 }, {}, ConversationDecisionSink { _,_,_,_,_ -> }, {}),
            "TEST", null,
        )
        val controller = TextCallTurnController(session.backend, session.approvalPolicy)
        session.beginTurn()
        assertTrue(session.armIdleClosure())
        var speech: String? = null
        controller.submitCandidateText("Nie słyszę odpowiedzi. Kończę połączenie.", object : TextCallTurnController.Listener {
            override fun onApprovedResponse(text: String) { speech = text }
            override fun onDroppedResponse() = error("unexpected_drop")
            override fun onError(reason: String) = error(reason)
        })
        assertNotNull(speech)
        assertEquals(0, requests)
        assertEquals(ConversationOutcome.Completed, session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(1, requests)
        assertNull(session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        controller.close()
    }

    @Test fun deterministicIdleFarewellPassesTextControllerButToolRequiresPlaybackAndAuthorization() {
        var calls = 0
        var authorized = true
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = true
            override fun authorized(tool: ConversationToolId) = authorized
            override fun requestEndCall(turn: Int): Boolean { calls++; return true }
        })
        val session = ToolCallingConversationSession(
            object : TextCallAgentBackend {
                override fun generate(userText: String, listener: TextCallAgentBackend.Listener) = Unit
                override fun cancel() = Unit
            },
            TextOutputApprovalPolicy { TextOutputDecision.RELEASE },
            tools, ConversationRuntimeHooks({ authorized }, { 2L }, { 1 }, {}, ConversationDecisionSink { _,_,_,_,_ -> }, {}),
            "TEST", null,
        )
        val controller = TextCallTurnController(session.backend, session.approvalPolicy)
        session.beginTurn()
        assertTrue(session.armIdleClosure())
        var approved: String? = null
        controller.submitCandidateText("Nie ma odpowiedzi. Kończę połączenie. Do widzenia.", object : TextCallTurnController.Listener {
            override fun onApprovedResponse(text: String) { approved = text }
            override fun onDroppedResponse() = error("unexpected_drop")
            override fun onError(reason: String) = error(reason)
        })
        assertNotNull(approved)
        assertEquals(0, calls)
        assertNull(session.takeOutcome(ConversationOutcomeBoundary.TRANSCRIPT))
        authorized = false
        assertTrue(session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE) is ConversationOutcome.Failed)
        assertEquals(0, calls)
        controller.close()
    }
}
