package pl.michalmatu.aicallbridge.developerrelay

internal data class ChatRelayEnvelope(
    val sessionId: String,
    val turnId: Long,
    val text: String,
) {
    fun validate(maxTextChars: Int = ChatRelayEnvelopeCodec.MAX_TEXT_CHARS) {
        require(SESSION_ID.matches(sessionId)) { "invalid_relay_session_id" }
        require(turnId in 1..MAX_TURN_ID) { "invalid_relay_turn_id" }
        require(text.isNotBlank()) { "relay_text_must_not_be_blank" }
        require(text.length <= maxTextChars) { "relay_text_too_long" }
    }

    private companion object {
        val SESSION_ID = Regex("[A-Za-z0-9._-]{1,80}")
        const val MAX_TURN_ID = 10_000L
    }
}

/**
 * Tiny line-oriented developer-relay protocol intentionally shared with the host Python bridge.
 *
 * Text starts after the first blank line and is never placed in ADB argv. This keeps the protocol
 * human-readable in the transient relay branch while allowing arbitrary UTF-8/newlines in speech
 * transcripts and responses.
 */
internal object ChatRelayEnvelopeCodec {
    const val MAGIC = "AICALL_CHAT_RELAY_V1"
    const val MAX_TEXT_CHARS = 4_000

    fun encode(kind: String, envelope: ChatRelayEnvelope): String {
        validateKind(kind)
        envelope.validate()
        return buildString {
            append(MAGIC).append('\n')
            append("kind=").append(kind).append('\n')
            append("session=").append(envelope.sessionId).append('\n')
            append("turn=").append(envelope.turnId).append('\n')
            append("text_chars=").append(envelope.text.length).append('\n')
            append('\n')
            append(envelope.text)
        }
    }

    fun decode(expectedKind: String, raw: String): ChatRelayEnvelope {
        validateKind(expectedKind)
        val normalized = raw.replace("\r\n", "\n")
        val separator = normalized.indexOf("\n\n")
        require(separator >= 0) { "relay_envelope_missing_body" }
        val header = normalized.substring(0, separator).lines()
        require(header.firstOrNull() == MAGIC) { "relay_envelope_bad_magic" }

        val fields = linkedMapOf<String, String>()
        for (line in header.drop(1)) {
            val index = line.indexOf('=')
            require(index > 0) { "relay_envelope_bad_header" }
            val key = line.substring(0, index)
            val value = line.substring(index + 1)
            require(key in REQUIRED_FIELDS) { "relay_envelope_unknown_header" }
            require(fields.put(key, value) == null) { "relay_envelope_duplicate_header" }
        }
        require(fields.keys == REQUIRED_FIELDS) { "relay_envelope_missing_header" }
        require(fields.getValue("kind") == expectedKind) { "relay_envelope_wrong_kind" }

        val text = normalized.substring(separator + 2)
        val expectedChars = fields.getValue("text_chars").toIntOrNull()
            ?: throw IllegalArgumentException("relay_envelope_bad_text_length")
        require(text.length == expectedChars) { "relay_envelope_text_length_mismatch" }
        val turnId = fields.getValue("turn").toLongOrNull()
            ?: throw IllegalArgumentException("relay_envelope_bad_turn")
        return ChatRelayEnvelope(
            sessionId = fields.getValue("session"),
            turnId = turnId,
            text = text,
        ).also { it.validate() }
    }

    private fun validateKind(kind: String) {
        require(kind == "request" || kind == "response") { "invalid_relay_kind" }
    }

    private val REQUIRED_FIELDS = linkedSetOf("kind", "session", "turn", "text_chars")
}
