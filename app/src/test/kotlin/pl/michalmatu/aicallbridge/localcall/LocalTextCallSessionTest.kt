package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class LocalTextCallSessionTest {
    @Test
    fun `session claims prepared backend once and delegates turn lifecycle`() {
        val backend = FakeBackend()
        val prepared = PreparedLocalTextCall(readyWorkflow(), backend)
        val pipeline = FakePipeline()
        val session = LocalTextCallSession(
            prepared = prepared,
            pipelineFactory = LocalTextCallSession.PipelineFactory { claimed ->
                assertTrue(claimed === backend)
                pipeline
            },
        )
        val listener = RecordingPipelineListener()

        session.start(listener)
        assertTrue(pipeline.started)
        assertTrue(session.writeInputPcm(byteArrayOf(1, 2, 3)))
        session.finishInput()
        assertEquals(1, pipeline.finishInputCalls)

        session.close()
        assertTrue(pipeline.closed)
        assertFalse(backend.closed)
    }

    @Test(expected = IllegalStateException::class)
    fun `prepared resources cannot open a second session`() {
        val prepared = PreparedLocalTextCall(readyWorkflow(), FakeBackend())
        val factory = LocalTextCallSession.PipelineFactory { FakePipeline() }

        LocalTextCallSession(prepared, factory)
        LocalTextCallSession(prepared, factory)
    }

    private fun readyWorkflow(): CallWorkflow {
        val task = CallTask(
            "diagnostic target",
            "diagnostic action",
            "diagnostic service",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        return CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(CallResolvedTarget("diagnostic", "000"))
        }
    }

    private class FakeBackend : TextCallAgentBackend {
        var closed = false

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) = Unit
        override fun cancel() = Unit
        override fun close() {
            closed = true
        }
    }

    private class FakePipeline : LocalTextCallSession.Pipeline {
        var started = false
        var finishInputCalls = 0
        var closed = false

        override fun start(listener: LocalSpeechTextPipeline.Listener) {
            started = true
        }

        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int): Boolean = true

        override fun finishInput() {
            finishInputCalls += 1
        }

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }
    }

    private class RecordingPipelineListener : LocalSpeechTextPipeline.Listener {
        override fun onSpeechInputReady() = Unit
        override fun onUserTranscript(text: String) = Unit
        override fun onApprovedText(text: String) = Unit
        override fun onOutputPcm16Mono16k(pcm: ByteArray) = Unit
        override fun onDroppedText() = Unit
        override fun onError(reason: String) = Unit
    }
}
