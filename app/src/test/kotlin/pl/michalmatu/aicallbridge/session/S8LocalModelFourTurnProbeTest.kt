package pl.michalmatu.aicallbridge.session

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class S8LocalModelFourTurnProbeTest {
    private val immediate = Executor { it.run() }

    @Test fun fourSyntheticNativeTextTurnsCompleteWithoutExposingResponses() {
        val model = FakeModel()
        val results = mutableListOf<S8LocalModelFourTurnProbe.Result>()
        val test = S8LocalModelFourTurnProbe({ model }, immediate) { results += it }

        test.start()

        assertEquals(4, model.calls.get())
        assertEquals(1, results.size)
        assertEquals(S8LocalModelFourTurnProbe.Status.PASS, results.single().status)
        assertEquals(4, results.single().completedTurns)
        assertEquals(1, model.closeCount.get())
        test.close() // idempotent
        assertEquals(1, results.size)
        assertEquals(1, model.closeCount.get())
    }

    @Test fun blankModelOutputFailsClosedWithoutAnotherTurn() {
        val model = FakeModel(output = "")
        val results = mutableListOf<S8LocalModelFourTurnProbe.Result>()
        S8LocalModelFourTurnProbe({ model }, immediate) { results += it }.start()

        assertEquals(1, model.calls.get())
        assertEquals(S8LocalModelFourTurnProbe.Status.FAILED, results.single().status)
        assertEquals(0, results.single().completedTurns)
        assertEquals(1, model.closeCount.get())
    }

    @Test fun immediateStopDiscardsLateNativeResponse() {
        val model = FakeModel(deferred = true)
        val results = mutableListOf<S8LocalModelFourTurnProbe.Result>()
        val probe = S8LocalModelFourTurnProbe({ model }, immediate) { results += it }
        probe.start()
        assertEquals(1, model.calls.get())

        probe.close()
        model.pending?.onComplete("spóźniona odpowiedź")

        assertEquals(1, results.size)
        assertEquals(S8LocalModelFourTurnProbe.Status.STOPPED, results.single().status)
        assertEquals(0, results.single().completedTurns)
        assertEquals(1, model.calls.get())
        assertEquals(1, model.closeCount.get())
    }

    @Test fun backendFactoryFailureHasNoFurtherAttempts() {
        val calls = AtomicInteger()
        val results = mutableListOf<S8LocalModelFourTurnProbe.Result>()
        S8LocalModelFourTurnProbe({
            calls.incrementAndGet()
            throw IllegalStateException("test_only")
        }, immediate) { results += it }.start()

        assertEquals(1, calls.get())
        assertEquals(S8LocalModelFourTurnProbe.Status.FAILED, results.single().status)
        assertEquals(0, results.single().completedTurns)
    }

    private class FakeModel(
        private val output: String = "Krótka odpowiedź po polsku.",
        private val deferred: Boolean = false,
    ) : TextCallAgentBackend {
        val calls = AtomicInteger()
        val closeCount = AtomicInteger()
        var pending: TextCallAgentBackend.Listener? = null

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            assertFalse(userText.isBlank())
            calls.incrementAndGet()
            if (deferred) pending = listener else listener.onComplete(output)
        }

        override fun cancel() { closeCount.incrementAndGet() }
    }
}
