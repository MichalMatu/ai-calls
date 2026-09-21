package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCallFinalTurnDispatcherTest {
    @Test
    fun `generate route uses final transcript as backend input`() {
        val backend = FakeBackend()
        val approved = mutableListOf<String>()
        val approvalSeen = mutableListOf<String>()
        val dispatcher = TextCallFinalTurnDispatcher(
            TextCallTurnController(
                backend,
                TextOutputApprovalPolicy { text ->
                    approvalSeen += text
                    TextOutputDecision.RELEASE
                },
            ),
        )
        val listener = RecordingListener(approved)

        dispatcher.dispatch("caller final text", TextCallFinalTurnRoute.Generate, listener)
        assertEquals(1, backend.generateCalls)
        assertEquals("caller final text", backend.lastUserText)
        backend.complete("backend reply")

        assertEquals(listOf("backend reply"), approvalSeen)
        assertEquals(listOf("backend reply"), approved)
        assertEquals(0, listener.consumedCalls)
    }

    @Test
    fun `candidate route uses exact deterministic text without backend generation`() {
        val backend = FakeBackend()
        val approved = mutableListOf<String>()
        val approvalSeen = mutableListOf<String>()
        val dispatcher = TextCallFinalTurnDispatcher(
            TextCallTurnController(
                backend,
                TextOutputApprovalPolicy { text ->
                    approvalSeen += text
                    TextOutputDecision.RELEASE
                },
            ),
        )
        val listener = RecordingListener(approved)

        dispatcher.dispatch(
            "caller final text",
            TextCallFinalTurnRoute.Candidate("1990"),
            listener,
        )

        assertEquals(0, backend.generateCalls)
        assertEquals(listOf("1990"), approvalSeen)
        assertEquals(listOf("1990"), approved)
        assertEquals(0, listener.consumedCalls)
    }

    @Test
    fun `consumed route invalidates stale generative callback and never enters approval`() {
        val backend = FakeBackend()
        val approved = mutableListOf<String>()
        val approvalSeen = mutableListOf<String>()
        val dispatcher = TextCallFinalTurnDispatcher(
            TextCallTurnController(
                backend,
                TextOutputApprovalPolicy { text ->
                    approvalSeen += text
                    TextOutputDecision.RELEASE
                },
            ),
        )
        val listener = RecordingListener(approved)

        dispatcher.dispatch("first transcript", TextCallFinalTurnRoute.Generate, listener)
        val stale = backend.pendingListener
        dispatcher.dispatch("structured transcript", TextCallFinalTurnRoute.Consumed, listener)

        assertEquals(1, backend.generateCalls)
        assertEquals(1, listener.consumedCalls)
        assertTrue(approvalSeen.isEmpty())
        stale?.onComplete("stale backend reply")
        assertTrue(approvalSeen.isEmpty())
        assertTrue(approved.isEmpty())
        assertTrue(backend.cancelCalls >= 2)
    }

    private class RecordingListener(
        private val approved: MutableList<String>,
    ) : TextCallFinalTurnDispatcher.Listener {
        var consumedCalls = 0
        override fun onApprovedText(text: String) {
            approved += text
        }
        override fun onDroppedText() = Unit
        override fun onConsumed() {
            consumedCalls += 1
        }
        override fun onError(reason: String) {
            throw AssertionError(reason)
        }
    }

    private class FakeBackend : TextCallAgentBackend {
        var generateCalls = 0
        var cancelCalls = 0
        var lastUserText: String? = null
        var pendingListener: TextCallAgentBackend.Listener? = null

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
            lastUserText = userText
            pendingListener = listener
        }

        fun complete(text: String) {
            pendingListener?.onComplete(text)
        }

        override fun cancel() {
            cancelCalls += 1
        }
    }
}
