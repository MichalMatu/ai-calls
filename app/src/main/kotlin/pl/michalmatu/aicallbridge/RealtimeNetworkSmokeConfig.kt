package pl.michalmatu.aicallbridge

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.IOException
import java.io.StringReader

/**
 * One-shot ADB diagnostic configuration for the physical Realtime network smoke.
 *
 * The config is deliberately loaded from app-private storage rather than exported Intent extras.
 * Loading always attempts to delete the source file, including malformed/oversized inputs, so the
 * development tunnel bearer is not left behind as persistent app state after a probe attempt.
 */
class RealtimeNetworkSmokeConfig private constructor(
    val credentialEndpoint: String,
    val brokerToken: String,
) {
    override fun toString(): String =
        "RealtimeNetworkSmokeConfig(credentialEndpoint=REDACTED, brokerToken=REDACTED)"

    companion object {
        const val FILE_NAME = "realtime-network-smoke.json"
        const val MAX_CONFIG_BYTES = 4 * 1024

        @JvmStatic
        fun loadAndDelete(file: File): RealtimeNetworkSmokeConfig {
            require(file.isFile) { "Realtime network smoke config file is missing" }

            try {
                val declaredLength = file.length()
                require(declaredLength in 0..MAX_CONFIG_BYTES.toLong()) {
                    "Realtime network smoke config exceeds size limit"
                }

                val bytes = file.readBytes()
                require(bytes.size <= MAX_CONFIG_BYTES) {
                    "Realtime network smoke config exceeds size limit"
                }
                return parse(bytes.toString(Charsets.UTF_8))
            } finally {
                if (file.exists() && !file.delete() && file.exists()) {
                    throw IllegalStateException("failed to delete Realtime network smoke config")
                }
            }
        }

        private fun parse(raw: String): RealtimeNetworkSmokeConfig {
            try {
                JsonReader(StringReader(raw)).use { reader ->
                    reader.isLenient = false
                    reader.beginObject()

                    var endpointSeen = false
                    var tokenSeen = false
                    var endpoint: String? = null
                    var token: String? = null

                    while (reader.hasNext()) {
                        when (val name = reader.nextName()) {
                            "credential_endpoint" -> {
                                require(!endpointSeen) { "duplicate credential_endpoint" }
                                endpointSeen = true
                                endpoint = readRequiredString(reader, name)
                            }
                            "broker_token" -> {
                                require(!tokenSeen) { "duplicate broker_token" }
                                tokenSeen = true
                                token = readRequiredString(reader, name)
                            }
                            else -> throw IllegalArgumentException(
                                "unexpected Realtime network smoke config field: $name",
                            )
                        }
                    }
                    reader.endObject()
                    if (reader.peek() != JsonToken.END_DOCUMENT) {
                        throw IllegalArgumentException(
                            "Realtime network smoke config contains trailing JSON",
                        )
                    }
                    if (!endpointSeen || !tokenSeen || endpoint == null || token == null) {
                        throw IllegalArgumentException(
                            "Realtime network smoke config requires credential_endpoint and broker_token",
                        )
                    }
                    return RealtimeNetworkSmokeConfig(endpoint, token)
                }
            } catch (error: IllegalArgumentException) {
                throw error
            } catch (error: IOException) {
                throw IllegalArgumentException("invalid Realtime network smoke config JSON", error)
            } catch (error: RuntimeException) {
                throw IllegalArgumentException("invalid Realtime network smoke config JSON", error)
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
