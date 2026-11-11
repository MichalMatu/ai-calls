package pl.michalmatu.aicallbridge.conversation

import com.google.gson.JsonObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Keeps host-owned transcript routing identical for text and the shared model's audio input. */
internal class ConversationInputRoutingBackend(
    private val model: TextCallAgentBackend,
    private val authorized: () -> Boolean,
    private val revokedReason: String,
    private val intercept: (String, TextCallAgentBackend.Listener) -> Boolean = { _, _ -> false },
) : TextCallAgentBackend {
    private val generation = AtomicLong()
    private val closed = AtomicBoolean()

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        request(listener) { guarded, permitted ->
            if (!intercept(userText, plainReply(null, guarded)) && permitted()) model.generate(userText, guarded)
        }
    }

    override fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) {
        request(listener) { guarded, permitted ->
            model.generateAudio(pcm16, object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    if (!permitted()) return
                    val transcript = runCatching { ConversationReplyProtocol.decodeAudio(text).first }
                        .getOrElse { guarded.onError("conversation_reply_invalid"); return }
                    guarded.onUserTranscript(transcript)
                    if (!permitted()) return
                    if (!intercept(transcript, plainReply(transcript, guarded))) guarded.onComplete(text)
                }
                override fun onError(reason: String) = guarded.onError(reason)
            })
        }
    }

    private fun plainReply(transcript: String?, listener: TextCallAgentBackend.Listener) =
        object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                listener.onComplete(JsonObject().apply {
                    addProperty("reply", text); addProperty("tool", "none")
                    if (transcript != null) addProperty("transcript", transcript)
                }.toString())
            }
            override fun onError(reason: String) = listener.onError(reason)
        }

    private fun request(listener: TextCallAgentBackend.Listener,
        send: (TextCallAgentBackend.Listener, () -> Boolean) -> Unit) {
        check(!closed.get())
        val token = generation.incrementAndGet()
        fun current() = !closed.get() && token == generation.get()
        fun permitted(): Boolean {
            if (!current()) return false
            if (authorized()) return true
            listener.onError(revokedReason)
            return false
        }
        if (!permitted()) return
        send(object : TextCallAgentBackend.Listener {
            override fun onUserTranscript(text: String) {
                if (permitted()) listener.onUserTranscript(text)
            }
            override fun onComplete(text: String) {
                if (permitted()) listener.onComplete(text)
            }
            override fun onError(reason: String) {
                if (current()) listener.onError(reason)
            }
        }, ::permitted)
    }

    override fun cancel() { generation.incrementAndGet(); model.cancel() }
    override fun close() {
        if (closed.compareAndSet(false, true)) { generation.incrementAndGet(); model.close() }
    }
}
