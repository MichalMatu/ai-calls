package pl.michalmatu.aicallbridge.realtime

import com.google.gson.JsonParser

/** Completed function call emitted by Realtime. Arguments are opaque to the transport layer. */
data class RealtimeFunctionCall @JvmOverloads constructor(
    val callId: String,
    val name: String,
    val argumentsJson: String,
    val responseId: String? = null,
) {
    init {
        require(callId.isNotBlank()) { "function call id must not be blank" }
        require(name.isNotBlank()) { "function call name must not be blank" }
        require(argumentsJson.isNotBlank()) { "function call arguments must not be blank" }
        if (responseId != null) {
            require(responseId.isNotBlank()) { "function call response id must be null or non-blank" }
        }
        val parsed = try {
            JsonParser.parseString(argumentsJson)
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("function call arguments must be valid JSON", error)
        }
        require(parsed.isJsonObject) { "function call arguments must be a JSON object" }
    }

    override fun toString(): String =
        "RealtimeFunctionCall(responseId=REDACTED, callId=REDACTED, " +
            "name=${RealtimeDiagnosticLabel.sanitize(name)}, arguments=REDACTED)"
}
