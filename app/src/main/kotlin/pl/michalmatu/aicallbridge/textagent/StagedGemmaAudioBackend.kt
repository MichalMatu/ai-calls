package pl.michalmatu.aicallbridge.textagent

import com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import pl.michalmatu.aicallbridge.conversation.ConversationReplyProtocol

/** One model owner, two serial inferences: audio -> transcript, transcript -> reply.
 * Retains the normal tool/approval boundary: this backend only proposes JSON.
 * No separate model is loaded. Call input and private transcripts never enter logs.
 */
internal class StagedGemmaAudioBackend(
    private val model: TextCallAgentBackend,
) : TextCallAgentBackend {
    private val generation = AtomicLong()
    private val closed = AtomicBoolean(false)

    private fun current(token: Long): Boolean =
        !closed.get() && generation.get() == token

    override fun prewarm() = model.prewarm()

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        check(!closed.get()) { "audio_backend_closed" }
        generation.incrementAndGet()
        model.generate(userText, listener)
    }

    override fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) {
        check(!closed.get()) { "audio_backend_closed" }
        val token = generation.incrementAndGet()
        model.generateAudio(pcm16, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                if (!current(token)) return
                val transcript = runCatching {
                    ConversationReplyProtocol.decodeTranscription(text)
                }.getOrElse {
                    listener.onError("audio_transcript_invalid")
                    return
                }
                if (!current(token)) return
                try {
                    model.generate(transcript, object : TextCallAgentBackend.Listener {
                        override fun onComplete(text: String) {
                            if (!current(token)) return
                            val reply = runCatching {
                                ConversationReplyProtocol.decode(text)
                                val json = JsonParser.parseString(text).asJsonObject
                                json.addProperty("transcript", transcript)
                                json.toString()
                            }.getOrElse {
                                listener.onError("conversation_reply_invalid")
                                return
                            }
                            if (current(token)) listener.onComplete(reply)
                        }

                        override fun onError(reason: String) {
                            if (current(token)) listener.onError(reason)
                        }
                    })
                } catch (_: Exception) {
                    if (current(token)) listener.onError("audio_text_stage_unavailable")
                }
            }

            override fun onError(reason: String) {
                if (current(token)) listener.onError(reason)
            }
        })
    }

    override fun cancel() {
        generation.incrementAndGet()
        model.cancel()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            generation.incrementAndGet()
            model.close()
        }
    }
}
