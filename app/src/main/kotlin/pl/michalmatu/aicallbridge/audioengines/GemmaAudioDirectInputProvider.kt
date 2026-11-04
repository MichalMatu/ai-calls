package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** An isolated 16-kHz mono signed PCM16 utterance, never a recording file. */
internal object GemmaAudioPcm16 {
    const val SAMPLE_RATE = 16_000
    const val MAX_SECONDS = 30
    const val MAX_PCM_BYTES = SAMPLE_RATE * 2 * MAX_SECONDS

    fun toWave(pcm: ByteArray): ByteArray {
        require(pcm.isNotEmpty() && pcm.size % 2 == 0) { "invalid_pcm16" }
        require(pcm.size <= MAX_PCM_BYTES) { "audio_exceeds_model_window" }
        val out = ByteArrayOutputStream(pcm.size + 44)
        fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun u16(value: Int) {
            out.write(value and 255)
            out.write((value ushr 8) and 255)
        }
        fun u32(value: Int) {
            u16(value and 65535)
            u16((value ushr 16) and 65535)
        }
        ascii("RIFF")
        u32(pcm.size + 36)
        ascii("WAVEfmt ")
        u32(16)
        u16(1)
        u16(1)
        u32(SAMPLE_RATE)
        u32(SAMPLE_RATE * 2)
        u16(2)
        u16(16)
        ascii("data")
        u32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}

/**
 * Experimental direct audio→text input behind the existing app-owned approval/TTS pipeline.
 * Disabled in AudioEngineRegistry until a real S22 native model and memory/perf acceptance.
 *
 * Unlike text-only Gemma, the native engine enables CPU audio backend, sends WAV audio as
 * Content.AudioBytes, and requests transcription in Polish. The whole utterance is kept in
 * private process memory and invalidated by STOP/cancel; no microphone capture, network,
 * grant change, private logs, or public artifact.
 */
internal class GemmaAudioDirectInputProvider(context: Context) : SpeechInputProvider {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var active: Run? = null

    private class Run(val listener: SpeechInputProvider.Listener) {
        val cancelled = AtomicBoolean(false)
        val pcm = ByteArrayOutputStream()
        var complete = false
        val worker = Executors.newSingleThreadExecutor { work ->
            Thread(work, "aicalls-gemma-audio-direct").apply { isDaemon = true }
        }
    }

    override fun start(listener: SpeechInputProvider.Listener) {
        cancel()
        val run = Run(listener)
        synchronized(lock) { active = run }
        run.worker.execute { inference(run) }
    }

    override fun writePcm(bytes: ByteArray, offset: Int, length: Int): Boolean {
        require(offset >= 0 && length >= 0 && offset <= bytes.size &&
            length <= bytes.size - offset) { "invalid_pcm_range" }
        return synchronized(lock) {
            val run = active ?: return@synchronized false
            if (run.cancelled.get() || run.complete) return@synchronized false
            if (run.pcm.size() + length > GemmaAudioPcm16.MAX_PCM_BYTES) return@synchronized false
            run.pcm.write(bytes, offset, length)
            true
        }
    }

    override fun finishInput() {
        synchronized(lock) { active?.complete = true }
    }

    override fun cancel() {
        val run = synchronized(lock) { active.also { active = null } }
        run?.cancelled?.set(true)
        run?.worker?.shutdownNow()
        run?.pcm?.reset()
    }

    override fun close() = cancel()

    private fun current(run: Run): Boolean =
        !run.cancelled.get() && synchronized(lock) { active === run }

    private fun inference(run: Run) {
        var engine: Engine? = null
        var conversation: Conversation? = null
        try {
            val model = Gemma4LiteRtTextBackendFactory.modelFile(appContext)
            if (!model.isFile || model.length() == 0L) {
                if (current(run)) run.listener.onError("gemma_audio_model_missing")
                return
            }
            val cache = File(model.parentFile, ".litert-audio-cache").apply { mkdirs() }
            engine = Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.GPU(),
                    visionBackend = null,
                    audioBackend = Backend.CPU(),
                    maxNumTokens = 2048,
                    cacheDir = cache.absolutePath,
                ),
            )
            engine.initialize()
            if (!current(run)) return
            conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "Rozpoznawaj polską mowę. Zwróć jedynie dokładną transkrypcję, bez odpowiedzi ani komentarza.",
                    ),
                    samplerConfig = SamplerConfig(
                        topK = 1, topP = 1.0, temperature = 0.0, seed = 0,
                    ),
                    automaticToolCalling = false,
                    channels = emptyList(),
                    maxOutputToken = 256,
                ),
            )
            run.listener.onReady()
            while (current(run)) {
                val complete = synchronized(lock) { run.complete }
                if (complete) break
                Thread.sleep(15)
            }
            if (!current(run)) return
            val samples = synchronized(lock) {
                run.pcm.toByteArray().also { run.pcm.reset() }
            }
            val wave = GemmaAudioPcm16.toWave(samples)
            val answer = conversation.sendMessage(
                Contents.of(
                    Content.AudioBytes(wave),
                    Content.Text("Zapisz dokładnie usłyszaną wypowiedź po polsku."),
                ),
            ).toString().trim()
            if (!current(run)) return
            if (answer.isBlank()) run.listener.onError("gemma_audio_empty_transcript")
            else run.listener.onFinalTranscript(answer)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (current(run)) run.listener.onError(
                "gemma_audio_native_" + error.javaClass.simpleName,
            )
        } finally {
            try { conversation?.close() } catch (_: Throwable) {}
            try { engine?.close() } catch (_: Throwable) {}
            run.worker.shutdown()
        }
    }
}
