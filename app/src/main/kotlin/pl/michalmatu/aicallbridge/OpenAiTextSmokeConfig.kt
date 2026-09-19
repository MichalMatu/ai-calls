package pl.michalmatu.aicallbridge

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.IOException
import java.io.StringReader

/** One-shot app-private config for the physical OPENAI_TEXT off-call proof. */
internal class OpenAiTextSmokeConfig private constructor(
    val textEndpoint: String,
    val brokerToken: String,
) {
    override fun toString(): String =
        "OpenAiTextSmokeConfig(textEndpoint=REDACTED, brokerToken=REDACTED)"

    companion object {
        const val FILE_NAME = "openai-text-smoke.json"
        const val MAX_CONFIG_BYTES = 4 * 1024

        fun loadAndDelete(file: File): OpenAiTextSmokeConfig {
            require(file.isFile) { "OpenAI text smoke config file is missing" }
            try {
                require(file.length() in 0..MAX_CONFIG_BYTES.toLong()) {
                    "OpenAI text smoke config exceeds size limit"
                }
                val bytes = file.readBytes()
                require(bytes.size <= MAX_CONFIG_BYTES) { "OpenAI text smoke config exceeds size limit" }
                return parse(bytes.toString(Charsets.UTF_8))
            } finally {
                if (file.exists() && !file.delete() && file.exists()) {
                    throw IllegalStateException("failed to delete OpenAI text smoke config")
                }
            }
        }

        internal fun parse(raw: String): OpenAiTextSmokeConfig {
            try {
                JsonReader(StringReader(raw)).use { reader ->
                    reader.isLenient = false
                    reader.beginObject()
                    var endpoint: String? = null
                    var token: String? = null
                    var endpointSeen = false
                    var tokenSeen = false
                    while (reader.hasNext()) {
                        when (val name = reader.nextName()) {
                            "text_endpoint" -> {
                                require(!endpointSeen) { "duplicate text_endpoint" }
                                endpointSeen = true
                                endpoint = readRequiredString(reader, name)
                            }
                            "broker_token" -> {
                                require(!tokenSeen) { "duplicate broker_token" }
                                tokenSeen = true
                                token = readRequiredString(reader, name)
                            }
                            else -> throw IllegalArgumentException("unexpected OpenAI text smoke config field: $name")
                        }
                    }
                    reader.endObject()
                    if (reader.peek() != JsonToken.END_DOCUMENT) {
                        throw IllegalArgumentException("OpenAI text smoke config contains trailing JSON")
                    }
                    require(endpointSeen && tokenSeen && endpoint != null && token != null) {
                        "OpenAI text smoke config requires text_endpoint and broker_token"
                    }
                    return OpenAiTextSmokeConfig(endpoint, token)
                }
            } catch (error: IllegalArgumentException) {
                throw error
            } catch (error: IOException) {
                throw IllegalArgumentException("invalid OpenAI text smoke config JSON", error)
            } catch (error: RuntimeException) {
                throw IllegalArgumentException("invalid OpenAI text smoke config JSON", error)
            }
        }

        private fun readRequiredString(reader: JsonReader, name: String): String {
            if (reader.peek() != JsonToken.STRING) {
                throw IllegalArgumentException("$name must be a string")
            }
            val value = reader.nextString().trim()
            require(value.isNotEmpty()) { "$name must not be blank" }
            return value
        }
    }
}
