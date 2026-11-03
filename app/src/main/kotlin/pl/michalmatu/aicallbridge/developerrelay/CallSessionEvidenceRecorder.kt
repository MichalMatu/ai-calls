package pl.michalmatu.aicallbridge.developerrelay

import android.content.Context
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat

/**
 * Optional, once-armed user diagnostic. The frozen RX/TX media transports are untouched:
 * their already available PCM is copied only when explicitly enabled by the owner in UI.
 * Text and waveforms are APP-PRIVATE. Never print or upload either to GitHub/logcat.
 */
internal object CallSessionEvidenceConsent {
    private const val PREFS = "call_session_evidence_consent_v1"
    private const val KEY_UNTIL = "armed_until_ms"
    private const val ARM_TTL_MS = 10L * 60_000L

    @Synchronized
    fun armNextCall(context: Context, now: Long = System.currentTimeMillis()) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UNTIL, now + ARM_TTL_MS).commit())
    }

    @Synchronized
    fun cancel(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_UNTIL).commit()
    }

    @Synchronized
    fun isArmed(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_UNTIL, 0L) > now

    /** Consume for one qualifying local audio session. Never auto-arm another call. */
    @Synchronized
    fun consume(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getLong(KEY_UNTIL, 0L) > now
        if (!prefs.edit().remove(KEY_UNTIL).commit()) return false
        return enabled
    }
}

/** 16 kHz mono signed PCM16LE -> real RIFF WAV; never rescales or re-routes audio. */
internal object CallSessionPcmWav {
    fun write(file: File, bytes: ByteArray) {
        require(bytes.size % 2 == 0) { "pcm16_alignment" }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = LocalSpeechFormat.SAMPLE_RATE_HZ
        val channels = LocalSpeechFormat.CHANNEL_COUNT
        val bits = LocalSpeechFormat.BYTES_PER_SAMPLE * 8
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + bytes.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 2)
        header.putShort((channels * 2).toShort())
        header.putShort(bits.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(bytes.size)
        FileOutputStream(file).use { output ->
            output.write(header.array())
            output.write(bytes)
        }
    }
}

/**
 * Each turn gets RX-consultant and TX-agent audio as separate WAV files and a JSONL timeline.
 * The WAVs are intentionally NOT merged: timestamped entries allow accurate later alignment
 * without faking speech/silence or losing which stream was actually sent/received.
 */
internal class CallSessionEvidenceRecorder private constructor(val directory: File) {
    private val initialElapsedMs = SystemClock.elapsedRealtime()
    private val completed = AtomicBoolean(false)
    private val audioBytes = AtomicLong(0L)
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PrivateCallEvidence").apply { isDaemon = true }
    }
    private val timeline = File(directory, "timeline.jsonl")

    fun recordTranscript(turn: Int, fromAgent: Boolean, text: String) {
        if (completed.get()) return
        val event = JSONObject()
            .put("type", if (fromAgent) "AGENT_APPROVED_TEXT" else "COUNTERPART_STT")
            .put("turn", turn)
            .put("elapsed_ms", elapsed())
            .put("speaker", if (fromAgent) "agent" else "counterparty")
            .put("text", text.take(MAX_TEXT_CHARS))
        submit { appendEvent(event) }
    }

    fun openRxTurn(turn: Int): RxTurn = RxTurn(turn)

    inner class RxTurn(private val turn: Int) {
        private val data = ByteArrayOutputStream()
        private val closed = AtomicBoolean(false)
        private var truncated = false
        private val startedAtMs = elapsed()

        @Synchronized
        fun append(frame: ByteArray, length: Int) {
            if (closed.get() || length <= 0) return
            if (length > frame.size || data.size() + length > MAX_TURN_BYTES) {
                truncated = true
                return
            }
            data.write(frame, 0, length)
        }

        @Synchronized
        fun finish() {
            if (!closed.compareAndSet(false, true)) return
            val copied = data.toByteArray()
            saveWave(turn, "rx", startedAtMs, copied, truncated)
        }
    }

    fun recordTransmittedAudio(turn: Int, pcm: ByteArray, sentBytes: Int, txAtMs: Long) {
        if (completed.get() || sentBytes <= 0) return
        // Record bytes actually accepted by the call TX writer, never unsent synthetic TTS.
        val size = minOf(sentBytes, pcm.size, MAX_TURN_BYTES)
        val aligned = size - (size % 2)
        val copied = pcm.copyOf(aligned)
        saveWave(turn, "tx", txAtMs, copied, aligned < sentBytes)
    }

    fun elapsed(): Long = (SystemClock.elapsedRealtime() - initialElapsedMs).coerceAtLeast(0L)

    private fun saveWave(turn: Int, direction: String, atMs: Long, pcm: ByteArray, truncated: Boolean) {
        if (completed.get()) return
        submit {
            val available = MAX_SESSION_BYTES - audioBytes.get()
            val limited = minOf(pcm.size.toLong(), available.coerceAtLeast(0L)).toInt() and -2
            val part = if (limited == pcm.size) pcm else pcm.copyOf(limited)
            val fileName = "turn-" + turn.toString().padStart(2, '0') + "-" + direction + ".wav"
            if (part.isNotEmpty()) {
                CallSessionPcmWav.write(File(directory, fileName), part)
                audioBytes.addAndGet(part.size.toLong())
            }
            appendEvent(JSONObject()
                .put("type", if (direction == "rx") "RX_PCM" else "TX_PCM")
                .put("turn", turn)
                .put("elapsed_ms", atMs)
                .put("file", if (part.isNotEmpty()) fileName else JSONObject.NULL)
                .put("pcm_bytes", part.size)
                .put("truncated", truncated || part.size != pcm.size))
        }
    }

    fun finish(reasonCode: String?) {
        if (!completed.compareAndSet(false, true)) return
        io.execute {
            appendEvent(JSONObject()
                .put("type", "END")
                .put("elapsed_ms", elapsed())
                .put("reason_code", reasonCode ?: "completed"))
            File(directory, "COMPLETE").writeText("local_complete\n", Charsets.UTF_8)
        }
        io.shutdown()
    }

    private fun submit(task: () -> Unit) {
        if (completed.get()) return
        runCatching { io.execute { runCatching(task) } }
    }

    private fun appendEvent(event: JSONObject) {
        timeline.appendText(event.toString() + "\n", Charsets.UTF_8)
    }

    companion object {
        const val ROOT = "call-session-evidence"
        private const val MAX_TEXT_CHARS = 2_000
        private const val MAX_TURN_BYTES = 2_000_000
        private const val MAX_SESSION_BYTES = 24_000_000L
        private const val RETAIN_SESSIONS = 3

        fun create(context: Context): CallSessionEvidenceRecorder? = runCatching {
            val root = File(context.filesDir, ROOT)
            check(root.isDirectory || root.mkdirs())
            val name = System.currentTimeMillis().toString() + "-" +
                UUID.randomUUID().toString().take(8)
            val session = File(root, name)
            check(session.mkdir())
            root.listFiles()?.filter { it.isDirectory && it != session }
                ?.sortedByDescending { it.name }?.drop(RETAIN_SESSIONS - 1)
                ?.forEach { it.deleteRecursively() }
            val recorder = CallSessionEvidenceRecorder(session)
            recorder.submit {
                recorder.appendEvent(JSONObject()
                    .put("type", "START")
                    .put("format", 1)
                    .put("pcm_rate_hz", LocalSpeechFormat.SAMPLE_RATE_HZ)
                    .put("pcm_channels", 1)
                    .put("rx_source", "stt_capture_windows")
                    .put("tx_source", "written_call_uplink"))
            }
            recorder
        }.getOrNull()
    }
}
