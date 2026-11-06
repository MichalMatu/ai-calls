package pl.michalmatu.aicallbridge.conversation

import com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

internal enum class ConversationResponseFormat { SPOKEN_TEXT, TOOL_REPLY }
internal data class ConversationReply(val text: String, val tool: ConversationToolId?) {
    override fun toString() = "ConversationReply(text=REDACTED, tool=$tool)"
}

internal object ConversationReplyProtocol {
    const val INSTRUCTION = """
        Zwróć wyłącznie obiekt JSON z polami reply (naturalna wypowiedź do TTS) oraz tool.
        tool="none" oznacza dalszy dialog. tool="end_call" proponuje zakończenie bieżącej rozmowy PO odtworzeniu reply.
        Wybierz end_call, gdy rozmówca w swojej ostatniej wypowiedzi wyraźnie kończy rozmowę
        albo żąda rozłączenia. Po przyjęciu i potwierdzeniu wiadomości pożegnaj się krótko.
        Rozmówca może też zakończyć bez zostawiania wiadomości; nie zmuszaj go do dalszych odpowiedzi.
        Cytowane pożegnanie, zaprzeczenie zakończenia, poprawka, podsumowanie lub kolejna sprawa to nie żądanie końca.
        Przy niejasności użyj none i dopytaj. Nie kończ wyłącznie na podstawie pojedynczych słów.
        Narzędzie jest propozycją; aplikacja sprawdza aktualne upoważnienie i wykonanie.
        Używaj tylko wartości tool dostępnych w schemacie. Nie twórz innych pól, argumentów ani narzędzi.
    """

    fun schema(enabled: Set<ConversationToolId>): String {
        val names = (listOf("none") + enabled.map { it.wireName }).joinToString(",") { "\"$it\"" }
        return """{"type":"object","properties":{"reply":{"type":"string"},"tool":{"type":"string","enum":[$names]}},"required":["reply","tool"],"additionalProperties":false}"""
    }

    fun decode(raw: String): ConversationReply {
        val root = JsonParser.parseString(raw)
        require(root.isJsonObject) { "conversation_reply_invalid" }
        val json = root.asJsonObject
        require(json.keySet() == setOf("reply", "tool")) { "conversation_reply_invalid" }
        fun string(key: String): String {
            val value = json.get(key)
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "conversation_reply_invalid" }
            return value.asString
        }
        val text = string("reply").trim()
        require(text.isNotBlank()) { "conversation_reply_invalid" }
        val tool = when (val name = string("tool")) {
            "none" -> null
            else -> ConversationToolId.entries.singleOrNull { it.wireName == name }
                ?: error("conversation_tool_unknown")
        }
        return ConversationReply(text, tool)
    }
}

/** Only decoded speech reaches the existing approval/TTS pipeline. A proposal is not execution. */
internal class ConversationReplyBackend(
    private val model: TextCallAgentBackend,
    private val decision: (ConversationReply?, Long, Boolean) -> Unit,
) : TextCallAgentBackend {
    private val generation = AtomicLong(0)
    private val closed = AtomicBoolean(false)
    private val tool = AtomicReference<ConversationToolId?>(null)

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        check(!closed.get())
        val request = generation.incrementAndGet()
        val started = System.nanoTime()
        tool.set(null)
        model.generate(userText, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                if (closed.get() || generation.get() != request) return
                val reply = runCatching { ConversationReplyProtocol.decode(text) }.getOrElse {
                    decision(null, (System.nanoTime() - started) / 1_000_000L, true)
                    listener.onError("conversation_reply_invalid"); return
                }
                tool.set(reply.tool)
                decision(reply, (System.nanoTime() - started) / 1_000_000L, false)
                if (!closed.get() && generation.get() == request) listener.onComplete(reply.text)
                else tool.set(null)
            }
            override fun onError(reason: String) {
                if (!closed.get() && generation.get() == request) {
                    decision(null, (System.nanoTime() - started) / 1_000_000L, true)
                    listener.onError(reason)
                }
            }
        })
    }
    fun takeTool(): ConversationToolId? = tool.getAndSet(null)
    override fun cancel() { generation.incrementAndGet(); tool.set(null); model.cancel() }
    override fun close() {
        if (closed.compareAndSet(false, true)) { generation.incrementAndGet(); tool.set(null); model.close() }
    }
}
