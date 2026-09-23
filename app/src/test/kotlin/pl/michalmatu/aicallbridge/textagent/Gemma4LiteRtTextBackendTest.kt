package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gemma4LiteRtTextBackendTest {
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
        var closeCalls = 0

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
