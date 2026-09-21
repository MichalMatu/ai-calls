package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCallTurnControllerCandidateTextTest {
    @Test
    fun `candidate text release uses approval and never invokes backend generation`() {
        val backend = FakeBackend()
        val approvalInputs = mutableListOf<String>()
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy { text ->
                approvalInputs += text
                TextOutputDecision.RELEASE
            },
        )
        val listener = RecordingListener()

        controller.submitCandidateText("1990", listener)

        assertEquals(listOf("1990"), approvalInputs)
        assertEquals(listOf("1990"), listener.approved)
        assertFalse(listener.dropped)
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `candidate text drop stays dropped and never invokes backend generation`() {
        val backend = FakeBackend()
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy { TextOutputDecision.DROP },
        )
        val listener = RecordingListener()

        controller.submitCandidateText("sensitive", listener)

        assertTrue(listener.dropped)
        assertTrue(listener.approved.isEmpty())
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `blank candidate is rejected before approval or backend work`() {
        val backend = FakeBackend()
        var approvalCalls = 0
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy {
                approvalCalls += 1
                TextOutputDecision.RELEASE
            },
        )

        assertThrows(IllegalArgumentException::class.java) {
            controller.submitCandidateText("   ", RecordingListener())
        }

        assertEquals(0, approvalCalls)
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `candidate invalidates stale backend completion before release`() {
        val backend = FakeBackend()
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy { TextOutputDecision.RELEASE },
        )
        val stale = RecordingListener()
        val deterministic = RecordingListener()

        controller.submitUserText("old generative turn", stale)
        controller.submitCandidateText("authorized fact", deterministic)
        backend.complete("stale generated text")

        assertTrue(stale.approved.isEmpty())
        assertFalse(stale.dropped)
        assertEquals(listOf("authorized fact"), deterministic.approved)
        assertEquals(1, backend.generateCalls)
        assertTrue(backend.cancelCalls >= 2)
    }

    private class RecordingListener : TextCallTurnController.Listener {
        val approved = mutableListOf<String>()
        var dropped = false

        override fun onApprovedResponse(text: String) {
            approved += text
        }

        override fun onDroppedResponse() {
            dropped = true
        }

        override fun onError(reason: String) = error(reason)
    }

    private class FakeBackend : TextCallAgentBackend {
        private var listener: TextCallAgentBackend.Listener? = null
        var generateCalls = 0
        var cancelCalls = 0

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
            this.listener = listener
        }

        fun complete(text: String) {
            listener?.onComplete(text)
        }

        override fun cancel() {
            cancelCalls += 1
        }
    }
}
