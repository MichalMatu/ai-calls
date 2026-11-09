package pl.michalmatu.aicallbridge.localspeech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.SpeechInputProvider
import pl.michalmatu.aicallbridge.audioengines.SpeechOutputProvider
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision

/** Pure off-call, deterministic adapter boundary: STOP revokes each in-flight generation. */
@RunWith(AndroidJUnit4::class)
class AndroidG52SpeechStopGateTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class PendingInput : SpeechInputProvider {
        val listeners = mutableListOf<SpeechInputProvider.Listener>()
        var cancels = 0
        override fun start(listener: SpeechInputProvider.Listener) { listeners.add(listener) }
        override fun writePcm(bytes: ByteArray, offset: Int, length: Int) = true
        override fun finishInput() {}
        override fun cancel() { cancels++ }
        override fun close() { cancels++ }
    }

    private class PendingModel : TextCallAgentBackend {
        val callbacks = mutableListOf<TextCallAgentBackend.Listener>()
        val submitted = mutableListOf<String>()
        var cancels = 0
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            submitted.add(userText)
            callbacks.add(listener)
        }
        override fun cancel() { cancels++ }
        override fun close() { cancels++ }
    }

    private class PendingOutput : SpeechOutputProvider {
        val callbacks = mutableListOf<SpeechOutputProvider.Listener>()
        val submitted = mutableListOf<String>()
        var cancels = 0
        override fun synthesize(text: String, listener: SpeechOutputProvider.Listener) {
            submitted.add(text)
            callbacks.add(listener)
        }
        override fun cancel() { cancels++ }
        override fun close() { cancels++ }
    }

    private class Observer : LocalSpeechTextPipeline.Listener {
        var ready = 0
        var transcript = 0
        var approved = 0
        var pcm = 0
        var dropped = 0
        var errors = 0
        override fun onSpeechInputReady() { ready++ }
        override fun onUserTranscript(text: String) { transcript++ }
        override fun onApprovedText(text: String) { approved++ }
        override fun onOutputPcm16Mono16k(pcm: ByteArray) { this.pcm++ }
        override fun onDroppedText() { dropped++ }
        override fun onError(reason: String) { errors++ }
        fun events() = listOf(ready, transcript, approved, pcm, dropped, errors)
    }

    private data class Fixture(
        val pipeline: LocalSpeechTextPipeline,
        val input: PendingInput,
        val model: PendingModel,
        val output: PendingOutput,
        val observer: Observer
    )
    private fun setup(): Fixture {
        val input = PendingInput()
        val model = PendingModel()
        val output = PendingOutput()
        val observer = Observer()
        return Fixture(LocalSpeechTextPipeline(
            context, model, TextOutputApprovalPolicy { TextOutputDecision.RELEASE },
            input = input, output = output
        ), input, model, output, observer)
    }

    @Test fun stopDuringPendingSttDropsLateReadyFinalAndError() {
        val f = setup()
        try {
            f.pipeline.start(f.observer)
            val original = f.input.listeners.single()
            f.pipeline.cancel()
            original.onReady()
            original.onFinalTranscript("Zapisz testową wiadomość")
            original.onError("late")
            assertEquals(listOf(0, 0, 0, 0, 0, 0), f.observer.events())
            assertTrue(f.model.submitted.isEmpty())
            assertTrue(f.output.submitted.isEmpty())
        } finally { f.pipeline.close() }
    }

    @Test fun stopDuringGemmaInferenceDropsLateCompletionAndError() {
        val f = setup()
        try {
            f.pipeline.start(f.observer)
            f.input.listeners.single().onFinalTranscript("Zapisz testową wiadomość")
            assertEquals(1, f.model.submitted.size)
            val callback = f.model.callbacks.single()
            f.pipeline.cancel()
            callback.onComplete("spóźniony model")
            callback.onError("late")
            assertEquals(0, f.observer.approved)
            assertEquals(0, f.observer.pcm)
            assertEquals(0, f.observer.errors)
            assertTrue(f.output.submitted.isEmpty())
        } finally { f.pipeline.close() }
    }

    @Test fun stopDuringTtsDropsLatePcmAndDoesNotRevokeCall() {
        val f = setup()
        try {
            f.pipeline.start(f.observer)
            f.input.listeners.single().onFinalTranscript("Test wiadomości")
            f.model.callbacks.single().onComplete("Zatwierdzony syntetyczny tekst")
            assertEquals(1, f.observer.approved)
            assertEquals(1, f.output.submitted.size)
            val stale = f.output.callbacks.single()
            f.pipeline.cancel()
            stale.onPcm16Mono16k(ByteArray(12_800))
            stale.onError("late")
            assertEquals(0, f.observer.pcm)
            assertEquals(0, f.observer.errors)
            assertTrue(f.output.cancels > 0)
        } finally { f.pipeline.close() }
    }

    @Test fun restartedTurnCannotReceivePreviousSttOrModelCallbacks() {
        val f = setup()
        try {
            f.pipeline.start(f.observer)
            val stale = f.input.listeners.single()
            f.pipeline.start(f.observer)
            val active = f.input.listeners.last()
            stale.onFinalTranscript("Stara wiadomość")
            stale.onError("late")
            assertTrue(f.model.submitted.isEmpty())
            active.onFinalTranscript("Nowa wiadomość")
            assertEquals(1, f.model.submitted.size)
            val modelCallback = f.model.callbacks.single()
            f.pipeline.cancel()
            modelCallback.onComplete("nieaktualna odpowiedź")
            assertTrue(f.output.submitted.isEmpty())
            assertEquals(0, f.observer.approved)
            assertEquals(0, f.observer.pcm)
            assertEquals(0, f.observer.errors)
        } finally { f.pipeline.close() }
    }

    @Test fun consecutiveGenerationsProduceOnePcmEachAndRejectDuplicateOutput() {
        val f = setup()
        try {
            repeat(2) { idx ->
                f.pipeline.start(f.observer)
                f.input.listeners.last().onFinalTranscript("Wiadomość numer " + idx)
                f.model.callbacks.last().onComplete("Wiadomość przyjęta")
                val out = f.output.callbacks.last()
                out.onPcm16Mono16k(ByteArray(4_800))
                out.onPcm16Mono16k(ByteArray(4_800))
                assertEquals(idx + 1, f.observer.pcm)
            }
            assertEquals(2, f.observer.approved)
            assertEquals(2, f.observer.transcript)
            assertEquals(2, f.model.submitted.size)
        } finally { f.pipeline.close() }
    }
}
