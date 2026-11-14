package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicLong
import pl.michalmatu.aicallbridge.conversation.ConversationReplyProtocol
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.LiteRtModelContainer

/** A native probe belongs to the exact installed bytes and protocol, not a file extension. */
internal object ModelCompatibilityStamp {
    /** A native audio PASS is valid only for the exact model AND selected TTS settings. */
    fun of(file: File, profile: AudioEngineProfile? = null, audio: Boolean = false): String? {
        if (!file.isFile || !file.canRead() || file.length() <= 0) return null
        val model = "0.19.0:reply-audio-v1:${file.canonicalPath}:${file.length()}:${file.lastModified()}"
        if (!audio) return model
        val selected = requireNotNull(profile) { "audio_profile_required" }
        val settings = listOf(selected.languageTag, selected.effectiveTtsEnginePackage.orEmpty(),
            selected.voiceId.orEmpty()).joinToString(":") { "${it.length}:$it" }
        // Older single-pass audio proof cannot authorize a new two-stage engine.
        return "$model:tts-v2-staged:$settings"
    }
}

internal class ModelCompatibilityStore(context: Context) {
    private val prefs = context.getSharedPreferences("model_compatibility_v1", Context.MODE_PRIVATE)
    private fun key(file: File, audio: Boolean): String = (if (audio) "audio-" else "text-") +
        MessageDigest.getInstance("SHA-256").digest(file.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
    fun verified(file: File, audio: Boolean, profile: AudioEngineProfile? = null): Boolean {
        val stamp = ModelCompatibilityStamp.of(file, profile, audio) ?: return false
        return prefs.getString(key(file, audio), null) == stamp
    }
    fun confirm(file: File, audio: Boolean, stamp: String, profile: AudioEngineProfile? = null): Boolean {
        if (ModelCompatibilityStamp.of(file, profile, audio) != stamp) return false
        val textStamp = ModelCompatibilityStamp.of(file) ?: return false
        return prefs.edit().apply {
            putString(key(file, audio), stamp)
            // Audio success also qualifies the same model for text, with the TEXT stamp.
            if (audio) putString(key(file, false), textStamp)
        }.commit()
    }
    fun invalidate(file: File, audio: Boolean): Boolean = prefs.edit().remove(key(file, audio)).commit()
}

/** Reject bad synthetic TTS payloads before invoking the native model.
 * This check has no authority to change the selected audio profile.
 */
internal object ModelAudioProbePcm {
    fun failure(pcm: ByteArray): String? = when {
        pcm.isEmpty() || pcm.size % 2 != 0 -> "polish_offline_voice_invalid_pcm"
        pcm.size > GemmaAudioPcm16.MAX_PCM_BYTES -> "polish_offline_voice_audio_too_long"
        else -> null
    }
}

/** Synthetic, off-call only. The same native engine handles text or audio and its cancellation. */
/** Distinguish incompatible audio schema from recognizer errors without logging any text. */
internal object ModelAudioCompatibilityReply {
    fun failure(raw: String): String? {
        val decoded = runCatching { ConversationReplyProtocol.decodeAudio(raw) }
            .getOrElse { return "model_audio_reply_format_failed" }
        val normalized = Normalizer.normalize(decoded.first.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        if (!normalized.contains("prosze") || !normalized.contains("pomoc"))
            return "model_audio_transcript_mismatch"
        if (decoded.second.tool != null) return "model_audio_tool_unexpected"
        return null
    }
}

internal class ModelCompatibilityProbe(private val context: Context) : AutoCloseable {
    private val generation = AtomicLong()
    private var model: TextCallAgentBackend? = null
    private var voice: LocalTtsSpeechOutput? = null

    fun check(file: File, profile: AudioEngineProfile, audio: Boolean, done: (String?) -> Unit) {
        close()
        val token = generation.incrementAndGet()
        val stamp = ModelCompatibilityStamp.of(file, profile, audio)
        if (stamp == null) { done("dialogue_model_missing_or_invalid"); return }
        if (!ModelCompatibilityStore(context).invalidate(file, audio)) {
            done("model_check_state_unavailable"); return
        }
        if (!LiteRtModelContainer.hasHeader(file)) { done("model_container_invalid"); return }
        fun current() = token == generation.get()
        fun finish(reason: String?) {
            context.mainExecutor.execute {
                if (!current()) return@execute
                val result = reason ?: if (ModelCompatibilityStore(context).confirm(file, audio, stamp, profile)) null
                    else "model_changed_during_check"
                close()
                done(result)
            }
        }
        val backend = if (audio)
            Gemma4LiteRtTextBackendFactory.createStagedCompatibilityProbe(file)
        else Gemma4LiteRtTextBackendFactory.createCompatibilityProbe(file, false)
        model = backend
        val listener = object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                if (!current()) return
                val failure = if (audio) ModelAudioCompatibilityReply.failure(text)
                else if (runCatching { ConversationReplyProtocol.decode(text).tool == null }
                        .getOrDefault(false)) null else "model_reply_incompatible"
                finish(failure)
            }
            override fun onError(reason: String) = finish("model_native_check_failed")
        }
        if (!audio) {
            backend.generate("To lokalna próba zgodności. Odpowiedz krótko w wymaganym formacie, tool=none.", listener)
        } else {
            // Overlap TTS preparation with native Gemma startup. Both run off-call;
            // generateAudio is serialized behind prewarm on the same native worker.
            // No synthetic text turn is added to the model conversation.
            backend.prewarm()
            val tts = LocalTtsSpeechOutput(context, profile.languageTag, profile.voiceId,
                profile.effectiveTtsEnginePackage)
            voice = tts
            tts.synthesize("Dzień dobry, proszę o pomoc.", object : LocalTtsSpeechOutput.Listener {
                override fun onPcm16Mono16k(pcm: ByteArray) {
                    if (!current()) return
                    val invalid = ModelAudioProbePcm.failure(pcm)
                    if (invalid != null) {
                        finish(invalid)
                        return
                    }
                    // A native submission may reject its input synchronously.
                    // Always complete the owner's off-call check; never leave it stuck.
                    try {
                        backend.generateAudio(pcm, listener)
                    } catch (_: IllegalArgumentException) {
                        finish("polish_offline_voice_invalid_pcm")
                    } catch (_: IllegalStateException) {
                        finish("model_native_check_failed")
                    }
                }
                override fun onError(reason: String) = finish("polish_offline_voice_missing")
            })
        }
    }

    override fun close() {
        generation.incrementAndGet()
        model?.close(); model = null
        voice?.close(); voice = null
    }
}
