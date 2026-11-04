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
import org.json.JSONObject
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat

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
            .put("text", text)
        submit { appendEvent(event) }
    }

    /**
     * Private, bounded diagnostic metadata. No model argument, target, phone number,
     * unapproved text or arbitrary exception string can enter this event.
     */
    fun recordDialogueDecision(
        turn: Int,
        actionId: String?,
        responseSource: String?,
        classifierWallMs: Long?,
        localErrorPresent: Boolean,
    ) {
        if (completed.get()) return
        val safeCode = Regex("[A-Z][A-Z0-9_]{0,63}")
        val action = actionId?.takeIf(safeCode::matches)
        val source = responseSource?.takeIf(safeCode::matches)
        if (action == null && source == null && classifierWallMs == null && !localErrorPresent) return
        val event = JSONObject()
            .put("type", "AGENT_DECISION")
            .put("turn", turn)
            .put("elapsed_ms", elapsed())
            .put("action_id", action ?: JSONObject.NULL)
            .put("response_source", source ?: JSONObject.NULL)
            .put("classifier_wall_ms", classifierWallMs?.coerceAtLeast(0L) ?: JSONObject.NULL)
            .put("local_error_present", localErrorPresent)
        submit { appendEvent(event) }
    }

    /** Written-to-uplink evidence is not proof of remote acoustic delivery. */
    fun recordFullTxAttempt(turn: Int, acceptedBytes: Int, requestedBytes: Int) {
        if (completed.get()) return
        val accepted = acceptedBytes.coerceIn(0, requestedBytes.coerceAtLeast(0))
        val event = JSONObject()
            .put("type", "AGENT_FULL_TX")
            .put("turn", turn)
            .put("elapsed_ms", elapsed())
            .put("pcm_bytes_accepted", accepted)
            .put("pcm_bytes_requested", requestedBytes.coerceAtLeast(0))
            .put("transport_status", if (accepted == requestedBytes && requestedBytes > 0) {
                "WRITE_COMPLETE"
            } else "WRITE_PARTIAL")
        submit { appendEvent(event) }
    }

    fun openRxTurn(turn: Int): RxTurn = RxTurn(turn)

    inner class RxTurn(private val turn: Int) {
        private val data = ByteArrayOutputStream()
        private val closed = AtomicBoolean(false)
        private val startedAtMs = elapsed()

        @Synchronized
        fun append(frame: ByteArray, length: Int) {
            if (closed.get() || length <= 0) return
            require(length <= frame.size) { "invalid_capture_frame_size" }
            data.write(frame, 0, length)
        }

        @Synchronized
        fun finish() {
            if (!closed.compareAndSet(false, true)) return
            val copied = data.toByteArray()
            saveWave(turn, "rx", startedAtMs, copied)
        }
    }

    fun recordTransmittedAudio(turn: Int, pcm: ByteArray, sentBytes: Int, txAtMs: Long) {
        if (completed.get() || sentBytes <= 0) return
        // Record bytes actually accepted by the call TX writer, never unsent synthetic TTS.
        val aligned = minOf(sentBytes, pcm.size).let { it - it % 2 }
        val copied = pcm.copyOf(aligned)
        saveWave(turn, "tx", txAtMs, copied)
    }

    /** Keep neutral filler in its own WAV; never overwrite the approved answer TX WAV. */
    fun recordAcknowledgementAudio(turn: Int, pcm: ByteArray, sentBytes: Int, txAtMs: Long) {
        if (completed.get() || sentBytes <= 0) return
        val aligned = minOf(sentBytes, pcm.size).let { it - it % 2 }
        saveWave(turn, "tx-ack", txAtMs, pcm.copyOf(aligned))
        // The filler is TTS text too, but is not an AGENT_APPROVED_TEXT final answer.
        val event = JSONObject()
            .put("type", "AGENT_ACK_TX")
            .put("turn", turn)
            .put("elapsed_ms", txAtMs)
            .put("speaker", "agent")
            .put("text", QuickAcknowledgementPolicy.TEXT)
            .put("pcm_bytes_accepted", aligned)
            .put("pcm_bytes_requested", pcm.size)
            .put("transport_status", if (aligned == pcm.size) "WRITE_COMPLETE" else "WRITE_PARTIAL")
        submit { appendEvent(event) }
    }

    fun elapsed(): Long = (SystemClock.elapsedRealtime() - initialElapsedMs).coerceAtLeast(0L)

    private fun saveWave(turn: Int, direction: String, atMs: Long, pcm: ByteArray) {
        if (completed.get()) return
        submit {
            val fileName = "turn-" + turn.toString().padStart(2, '0') + "-" + direction + ".wav"
            val destination = File(directory, fileName)
            // No arbitrary per-turn, per-session or last-N-recordings truncation.
            // Only actual free-space pressure can stop writing, without stopping the call.
            val saved = pcm.isNotEmpty() && ensureFreeSpace(pcm.size.toLong() + 44L) &&
                runCatching { CallSessionPcmWav.write(destination, pcm) }.isSuccess
            if (!saved) destination.delete()
            appendEvent(JSONObject()
                .put("type", when (direction) {
                    "rx" -> "RX_PCM"
                    "tx-ack" -> "TX_ACK_PCM"
                    else -> "TX_PCM"
                })
                .put("turn", turn)
                .put("elapsed_ms", atMs)
                .put("file", if (saved) fileName else JSONObject.NULL)
                .put("pcm_bytes", if (saved) pcm.size else 0)
                .put("storage_error", pcm.isNotEmpty() && !saved))
        }
    }

    private fun ensureFreeSpace(requiredBytes: Long): Boolean {
        val needed = requiredBytes + MIN_OS_FREE_BYTES
        if (directory.usableSpace >= needed) return true
        // Retire only older *completed* evidence under actual storage pressure.
        // This is resource recovery, not a fixed three-session retention ceiling.
        directory.parentFile?.listFiles()?.asSequence()
            ?.filter { it.isDirectory && it != directory && File(it, "COMPLETE").isFile }
            ?.sortedBy { it.name }
            ?.forEach { old ->
                if (directory.usableSpace < needed) old.deleteRecursively()
            }
        return directory.usableSpace >= needed
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
        // Preserve free space needed for Android; no fixed recording count/time cap.
        private const val MIN_OS_FREE_BYTES = 64L * 1024L * 1024L

        fun create(context: Context): CallSessionEvidenceRecorder? = runCatching {
            val root = File(context.filesDir, ROOT)
            check(root.isDirectory || root.mkdirs())
            val name = System.currentTimeMillis().toString() + "-" +
                UUID.randomUUID().toString().take(8)
            val session = File(root, name)
            check(session.mkdir())
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
