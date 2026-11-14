package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gemma4LiteRtTextBackendTest {
    @Test
    fun prewarmInitializesModelWithoutSyntheticTurn() {
        val runtime = RecordingRuntime()
        var loads = 0
        val backend = Gemma4LiteRtTextBackend(
            runtimeFactory = Gemma4RuntimeFactory { _, _ -> loads++; runtime },
            modelPath = "/models/gemma-4-E2B-it.litertlm",
            systemInstruction = "incoming-secretary",
        )
        backend.prewarm()
        assertEquals(1, loads)
        assertEquals(1, runtime.prewarmCalls)
        assertTrue(runtime.prompts.isEmpty())
        backend.generate("incoming question", RecordingListener())
        assertEquals(1, loads)
        assertEquals(listOf("incoming question"), runtime.prompts)
        backend.close()
    }

    @Test
    fun `forwards one complete bounded response from runtime`() {
        val runtime = RecordingRuntime()
        val backend = Gemma4LiteRtTextBackend(
            runtimeFactory = Gemma4RuntimeFactory { modelPath, systemInstruction ->
                runtime.modelPath = modelPath
                runtime.systemInstruction = systemInstruction
                runtime
            },
            modelPath = "/models/gemma-4-E2B-it.litertlm",
            systemInstruction = "skill-only",
        )
        val listener = RecordingListener()

        backend.generate("dzień dobry", listener)
        runtime.complete("{\"skill\":\"ASK_CLARIFY\",\"confidence\":0.91}")

        assertEquals("/models/gemma-4-E2B-it.litertlm", runtime.modelPath)
        assertEquals("skill-only", runtime.systemInstruction)
        assertEquals(listOf("dzień dobry"), runtime.prompts)
        assertEquals("{\"skill\":\"ASK_CLARIFY\",\"confidence\":0.91}", listener.text)
        assertEquals(null, listener.error)
        backend.close()
    }

    @Test
    fun `new generation cancels previous runtime work and ignores stale completion`() {
        val runtime = RecordingRuntime()
        val backend = Gemma4LiteRtTextBackend(
            runtimeFactory = Gemma4RuntimeFactory { _, _ -> runtime },
            modelPath = "/models/gemma-4-E2B-it.litertlm",
            systemInstruction = "skill-only",
        )
        val first = RecordingListener()
        val second = RecordingListener()

        backend.generate("first", first)
        val stale = runtime.currentListener
        backend.generate("second", second)
        stale?.onComplete("stale")
        runtime.complete("fresh")

        assertEquals(1, runtime.cancelCalls)
        assertEquals(null, first.text)
        assertEquals("fresh", second.text)
        backend.close()
    }

    @Test
    fun `one backend retains runtime across call turns but prewarm can reset synthetic chat`() {
        val runtime = RecordingRuntime()
        var modelLoads = 0
        val backend = Gemma4LiteRtTextBackend(
            runtimeFactory = Gemma4RuntimeFactory { _, _ ->
                modelLoads += 1
                runtime
            },
            modelPath = "/models/gemma-4-E2B-it.litertlm",
            systemInstruction = "structured actions",
        )
        backend.generate("synthetic-prewarm", RecordingListener())
        runtime.complete("prewarm")
        backend.resetConversationContext()

        val first = RecordingListener()
        backend.generate("real-call-turn-1", first)
        runtime.complete("first-answer")
        val second = RecordingListener()
        backend.generate("real-call-turn-2", second)
        runtime.complete("second-answer")

        assertEquals(1, modelLoads)
        assertEquals(1, runtime.contextResets)
        assertEquals(
            listOf("synthetic-prewarm", "real-call-turn-1", "real-call-turn-2"),
            runtime.prompts,
        )
        assertEquals("first-answer", first.text)
        assertEquals("second-answer", second.text)
        assertEquals(0, runtime.closeCalls)
        backend.close()
        assertEquals(1, runtime.closeCalls)
    }

    @Test
    fun `runtime error is sanitized and close is idempotent`() {
        val runtime = RecordingRuntime()
        val backend = Gemma4LiteRtTextBackend(
            runtimeFactory = Gemma4RuntimeFactory { _, _ -> runtime },
            modelPath = "/models/gemma-4-E2B-it.litertlm",
            systemInstruction = "skill-only",
        )
        val listener = RecordingListener()

        backend.generate("test", listener)
        runtime.fail("native\nboom=detail")
        backend.close()
        backend.close()

        assertEquals("gemma4_runtime_native boom:detail", listener.error)
        assertTrue(runtime.closeCalls >= 1)
    }

    private class RecordingRuntime : Gemma4Runtime {
        var modelPath: String? = null
        var systemInstruction: String? = null
        val prompts = mutableListOf<String>()
        var currentListener: TextCallAgentBackend.Listener? = null
        var cancelCalls = 0
        var contextResets = 0
        var closeCalls = 0
        var prewarmCalls = 0

        override fun prewarm() { prewarmCalls++ }

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            prompts += userText
            currentListener = listener
        }

        fun complete(text: String) {
            currentListener?.onComplete(text)
        }

        fun fail(reason: String) {
            currentListener?.onError(reason)
        }

        override fun cancel() {
            cancelCalls += 1
        }

        override fun resetConversationContext() {
            contextResets += 1
        }

        override fun close() {
            closeCalls += 1
        }
    }

    private class RecordingListener : TextCallAgentBackend.Listener {
        var text: String? = null
        var error: String? = null

        override fun onComplete(text: String) {
            this.text = text
        }

        override fun onError(reason: String) {
            error = reason
        }
    }
}
