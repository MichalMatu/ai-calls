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
    fun of(file: File): String? = if (file.isFile && file.canRead() && file.length() > 0)
        "0.19.0:reply-audio-v1:${file.canonicalPath}:${file.length()}:${file.lastModified()}" else null
}

internal class ModelCompatibilityStore(context: Context) {
    private val prefs = context.getSharedPreferences("model_compatibility_v1", Context.MODE_PRIVATE)
    private fun key(file: File, audio: Boolean): String = (if (audio) "audio-" else "text-") +
        MessageDigest.getInstance("SHA-256").digest(file.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
    fun verified(file: File, audio: Boolean): Boolean {
        val stamp = ModelCompatibilityStamp.of(file) ?: return false
        return prefs.getString(key(file, audio), null) == stamp
    }
    fun confirm(file: File, audio: Boolean, stamp: String): Boolean =
        ModelCompatibilityStamp.of(file) == stamp && prefs.edit().apply {
            putString(key(file, audio), stamp)
            if (audio) putString(key(file, false), stamp)
        }.commit()
}

/** Synthetic, off-call only. The same native engine handles text or audio and its cancellation. */
internal class ModelCompatibilityProbe(private val context: Context) : AutoCloseable {
    private val generation = AtomicLong()
    private var model: TextCallAgentBackend? = null
    private var voice: LocalTtsSpeechOutput? = null

    fun check(file: File, profile: AudioEngineProfile, audio: Boolean, done: (String?) -> Unit) {
        close()
        val token = generation.incrementAndGet()
        val stamp = ModelCompatibilityStamp.of(file)
        if (stamp == null) { done("dialogue_model_missing_or_invalid"); return }
        if (!LiteRtModelContainer.hasHeader(file)) { done("model_container_invalid"); return }
        fun current() = token == generation.get()
        fun finish(reason: String?) {
            context.mainExecutor.execute {
                if (!current()) return@execute
                val result = reason ?: if (ModelCompatibilityStore(context).confirm(file, audio, stamp)) null
                    else "model_changed_during_check"
                close()
                done(result)
            }
        }
        val backend = Gemma4LiteRtTextBackendFactory.createCompatibilityProbe(file, audio)
        model = backend
        val listener = object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                if (!current()) return
                val valid = runCatching {
                    if (audio) {
                        val (transcript, reply) = ConversationReplyProtocol.decodeAudio(text)
                        val normalized = Normalizer.normalize(transcript.lowercase(), Normalizer.Form.NFD)
                            .replace(Regex("\\p{M}"), "")
                        require(normalized.contains("prosze") && normalized.contains("pomoc"))
                        require(reply.tool == null)
                    } else require(ConversationReplyProtocol.decode(text).tool == null)
                }.isSuccess
                finish(if (valid) null else "model_reply_incompatible")
            }
            override fun onError(reason: String) = finish("model_native_check_failed")
        }
        if (!audio) {
            backend.generate("To lokalna próba zgodności. Odpowiedz krótko w wymaganym formacie, tool=none.", listener)
        } else {
            val tts = LocalTtsSpeechOutput(context, profile.languageTag, profile.voiceId,
                profile.effectiveTtsEnginePackage)
            voice = tts
            tts.synthesize("Dzień dobry, proszę o pomoc.", object : LocalTtsSpeechOutput.Listener {
                override fun onPcm16Mono16k(pcm: ByteArray) {
                    if (current()) backend.generateAudio(pcm, listener)
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
