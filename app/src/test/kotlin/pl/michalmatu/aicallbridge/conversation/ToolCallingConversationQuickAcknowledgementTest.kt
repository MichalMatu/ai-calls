package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolExecutor
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolPort
import pl.michalmatu.aicallbridge.developerrelay.QuickAcknowledgementPolicy
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision

class ToolCallingConversationQuickAcknowledgementTest {
    @Test fun optionalPacingApprovalPreservesPendingEndCallUntilPlaybackCompletes() {
        val model = FakeModel()
        var endRequests = 0
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = true
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int): Boolean {
                endRequests++
                return true
            }
        })
        val session = ToolCallingConversationSession(
            model,
            TextOutputApprovalPolicy { TextOutputDecision.RELEASE },
            tools,
            ConversationRuntimeHooks(
                authorized = { true },
                mediaGeneration = { 1L },
                turn = { 1 },
                emit = {},
                decisionSink = ConversationDecisionSink { _, _, _, _, _ -> },
                toolEventSink = {},
            ),
            "TEST", null,
        )
        session.beginTurn()
        var speech: String? = null
        session.backend.generate("synthetic goodbye", object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { speech = text }
            override fun onError(reason: String) { throw AssertionError(reason) }
        })
        model.listener!!.onComplete("""{"reply":"Do widzenia.","tool":"end_call","note":"","kind":"NOTE"}""")
        assertEquals("Do widzenia.", speech)
        assertEquals(TextOutputDecision.RELEASE, session.approvalPolicy.evaluate(speech!!))

        // Previously this optional voice check re-entered the stateful reply approval,
        // clearing the END_CALL proposal before its playback boundary.
        assertEquals(TextOutputDecision.RELEASE,
            session.acknowledgementPolicy.evaluate(QuickAcknowledgementPolicy.TEXT))
        assertEquals(ConversationOutcome.Completed,
            session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
        assertEquals(1, endRequests)
    }

    private class FakeModel : TextCallAgentBackend {
        var listener: TextCallAgentBackend.Listener? = null
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            this.listener = listener
        }
        override fun cancel() = Unit
    }
}
