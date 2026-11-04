package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriber
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriberResult
import com.google.mediapipe.tasks.core.BaseOptions
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Official MediaPipe Parakeet stateful INT8 bundle. Never download or run in a Call by discovery. */
internal object ParakeetModelFile {
    const val NAME = "parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm"
    const val MIN_SIZE_BYTES = 614_588_752L
    const val EXPECTED_SHA256 = "66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504"
    const val HF_REVISION = "1f78e5eb6175b1b2cce35f16bcf9e2027b83e8bd"
    const val DOWNLOAD_URL = "https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/resolve/" +
        HF_REVISION + "/" + NAME + "?download=true"
    private val fingerprintLock = Any()
    private var lastVerified: Triple<String, Long, Long>? = null
    fun validPinnedModel(file: File): Boolean {
        if (!file.isFile || file.length() != MIN_SIZE_BYTES) return false
        val stamp = Triple(file.absolutePath, file.length(), file.lastModified())
        synchronized(fingerprintLock) { if (stamp == lastVerified) return true }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val chunk = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                digest.update(chunk, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != EXPECTED_SHA256) return false
        synchronized(fingerprintLock) { lastVerified = stamp }
        return true
    }
    fun resolve(context: Context): File {
        val app = context.applicationContext
        val root = app.getExternalFilesDir("models") ?: File(app.filesDir, "models")
        return File(root, NAME)
    }
}

/** Stateless for the outside world, with one byte carry to prevent odd-sized RX chunks losing PCM. */
internal class Pcm16FloatDecoder {
    private var lowByte: Int? = null
    fun convert(data: ByteArray): FloatArray {
        val out = FloatArray((data.size + if (lowByte == null) 0 else 1) / 2)
        var index = 0
        var sample = 0
        val carry = lowByte
        lowByte = null
        if (carry != null && data.isNotEmpty()) {
            out[sample++] = (((data[index++].toInt() and 255) shl 8) or carry)
                .toShort().toInt() / 32768f
        } else if (carry != null) lowByte = carry
        while (index + 1 < data.size) {
            val bits = (data[index].toInt() and 255) or ((data[index + 1].toInt() and 255) shl 8)
            out[sample++] = bits.toShort().toInt() / 32768f
            index += 2
        }
        if (index < data.size) lowByte = data[index].toInt() and 255
        return if (sample == out.size) out else out.copyOf(sample)
    }
    fun hasIncompleteSample() = lowByte != null
}

/**
 * PCM RX is only enqueued/copy-owned on the caller thread. All blocking MediaPipe calls, model
 * prewarm, finishStream and native close execute on the same private transcription worker.
 * No audio/text is logged, exported, or sent to a server. Backpressure reports false instead
 * of blocking the frozen Shizuku RX thread or silently losing samples.
 */
internal class ParakeetSpeechInputProvider(context: Context) : SpeechInputProvider {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var current: Run? = null

    private class Run(val listener: SpeechInputProvider.Listener) {
        val queue = ArrayBlockingQueue<ByteArray>(128)
        val ended = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor { job ->
            Thread(job, "aicalls-parakeet-asr").apply { isDaemon = true }
        }
    }

    override fun start(listener: SpeechInputProvider.Listener) {
        cancel()
        val run = Run(listener)
        synchronized(lock) { current = run }
        run.worker.execute { transcribe(run) }
    }

    override fun writePcm(bytes: ByteArray, offset: Int, length: Int): Boolean {
        require(offset >= 0 && length >= 0 && offset <= bytes.size &&
            length <= bytes.size - offset) { "invalid_pcm_range" }
        val run = synchronized(lock) { current } ?: return false
        if (run.cancelled.get() || run.ended.get()) return false
        if (length == 0) return true
        return run.queue.offer(bytes.copyOfRange(offset, offset + length))
    }

    override fun finishInput() {
        synchronized(lock) { current }?.ended?.set(true)
    }

    override fun cancel() {
        val run = synchronized(lock) { current.also { current = null } }
        run?.cancelled?.set(true)
        run?.worker?.shutdownNow()
        run?.queue?.clear()
    }

    override fun close() = cancel()

    private fun isCurrent(run: Run): Boolean =
        !run.cancelled.get() && synchronized(lock) { current === run }

    private fun transcribe(run: Run) {
        var task: AudioTranscriber? = null
        try {
            val model = ParakeetModelFile.resolve(appContext)
            if (!model.isFile || model.length() != ParakeetModelFile.MIN_SIZE_BYTES) {
                if (isCurrent(run)) run.listener.onError("parakeet_model_missing_or_incomplete")
                return
            }
            if (!ParakeetModelFile.validPinnedModel(model)) {
                if (isCurrent(run)) run.listener.onError("parakeet_model_sha256_mismatch")
                return
            }
            val options = AudioTranscriber.AudioTranscriberOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(model.absolutePath).build())
                .setSampleRateHz(16000)
                .setEnableInterimResults(true)
                .build()
            task = AudioTranscriber.createFromOptions(appContext, options)
            task.prewarm()
            if (!isCurrent(run)) return
            run.listener.onReady()
            val converter = Pcm16FloatDecoder()
            val finalParts = mutableListOf<String>()
            fun collect(result: AudioTranscriberResult) {
                result.segments().filter { it.isFinal }.map { it.transcript().trim() }
                    .filter { it.isNotEmpty() }.forEach { finalParts += it }
            }
            while (isCurrent(run)) {
                val chunk = run.queue.poll(100, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    val samples = converter.convert(chunk)
                    if (samples.isNotEmpty()) collect(task.transcribe(samples))
                } else if (run.ended.get()) break
            }
            if (!isCurrent(run)) return
            if (converter.hasIncompleteSample()) {
                run.listener.onError("parakeet_truncated_pcm16")
                return
            }
            collect(task.finishStream())
            if (!isCurrent(run)) return
            val finalText = finalParts.joinToString(" ").trim()
            if (finalText.isBlank()) run.listener.onError("parakeet_empty_transcript")
            else run.listener.onFinalTranscript(finalText)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (isCurrent(run)) {
                run.listener.onError("parakeet_native_" + error.javaClass.simpleName)
            }
        } finally {
            try { task?.close() } catch (_: Throwable) {}
            run.worker.shutdown()
        }
    }
}
