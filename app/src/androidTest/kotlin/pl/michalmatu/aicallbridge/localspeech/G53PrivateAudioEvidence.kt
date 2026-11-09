package pl.michalmatu.aicallbridge.localspeech

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import pl.michalmatu.aicallbridge.conversation.SkillTurnRoutingDecision

/** A completed, synthetic off-call turn only. Never read the microphone or a real Call. */
internal data class G53CapturedAudioTurn(
    val reference: String,
    val transcript: String,
    val assistant: String,
    val sourcePcm: ByteArray,
    val responsePcm: ByteArray,
    val sttMs: Long,
    val approvalMs: Long,
    val ttsEndMs: Long,
)

/**
 * Explicit AndroidTest-only private corpus for human quality review.
 * The default test writes nothing. No full text or WAV is sent to logcat/Local Agent/Git.
 */
internal object G53PrivateAudioEvidence {
    private const val SAMPLE_RATE = 16_000
    private const val SCHEMA = "g53-synthetic-audio-review-v1"

    fun save(
        context: Context,
        modelId: String,
        sttId: String,
        ttsId: String,
        turns: List<G53CapturedAudioTurn>,
        routing: List<SkillTurnRoutingDecision>,
    ): File {
        require(turns.isNotEmpty()) { "g53_empty_corpus" }
        require(turns.size == routing.size) { "g53_mismatched_routing" }
        turns.forEach { turn ->
            require(turn.reference.isNotBlank() && turn.transcript.isNotBlank() &&
                turn.assistant.isNotBlank()) { "g53_blank_speech_text" }
            require(turn.sttMs <= turn.approvalMs && turn.approvalMs <= turn.ttsEndMs) {
                "g53_invalid_turn_timing"
            }
            require(turn.sourcePcm.isNotEmpty() && turn.responsePcm.isNotEmpty() &&
                turn.sourcePcm.size % 2 == 0 && turn.responsePcm.size % 2 == 0) {
                "g53_invalid_pcm"
            }
        }
        val root = File(context.noBackupFilesDir, "g53-synthetic-audio")
        check(root.isDirectory || root.mkdirs()) { "g53_private_root_unavailable" }
        val id = "session-" + System.currentTimeMillis() + "-" +
            UUID.randomUUID().toString().substring(0, 8)
        val dir = File(root, id)
        check(dir.mkdir()) { "g53_private_session_unavailable" }
        val list = JSONArray()
        turns.forEachIndexed { index, turn ->
            val number = index + 1
            val rx = String.format(Locale.US, "turn-%02d-rx.wav", number)
            val tx = String.format(Locale.US, "turn-%02d-tx.wav", number)
            writeWav(File(dir, rx), turn.sourcePcm)
            writeWav(File(dir, tx), turn.responsePcm)
            val decision = routing[index]
            list.put(JSONObject()
                .put("turn", number)
                .put("reference_utterance", turn.reference)
                .put("stt_final", turn.transcript)
                .put("approved_response", turn.assistant)
                .put("stt_ms", turn.sttMs)
                .put("approval_ms", turn.approvalMs)
                .put("tts_end_ms", turn.ttsEndMs)
                .put("rx_wav", rx)
                .put("tx_wav", tx)
                .put("activated_skill_ids", JSONArray(decision.activatedIds.sorted()))
                .put("deactivated_skill_ids", JSONArray(decision.deactivatedIds.sorted())))
        }
        val record = JSONObject()
            .put("schema", SCHEMA)
            .put("source", "synthetic_offcall_test")
            .put("no_telecom", true)
            .put("model", modelId)
            .put("stt_provider", sttId)
            .put("tts_provider", ttsId)
            .put("language", "pl-PL")
            .put("sample_rate_hz", SAMPLE_RATE)
            .put("pcm_format", "pcm_s16le_mono")
            .put("skills_preset", "natural")
            .put("turns", list)
        // Manifest is the completion marker; incomplete sessions are never review-ready.
        val partial = File(dir, "manifest.partial")
        partial.writeText(record.toString(2) + "\n", Charsets.UTF_8)
        check(partial.renameTo(File(dir, "manifest.json"))) { "g53_manifest_commit_failed" }
        return dir
    }

    private fun writeWav(file: File, pcm: ByteArray) {
        require(pcm.size % 2 == 0 && pcm.isNotEmpty()) { "g53_pcm_alignment" }
        val riffSize = 36L + pcm.size
        require(riffSize <= Int.MAX_VALUE) { "g53_wav_unrepresentable" }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(riffSize.toInt())
        header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM fmt chunk
        header.putShort(1) // linear PCM
        header.putShort(1) // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        FileOutputStream(file).use {
            it.write(header.array())
            it.write(pcm)
        }
    }
}
