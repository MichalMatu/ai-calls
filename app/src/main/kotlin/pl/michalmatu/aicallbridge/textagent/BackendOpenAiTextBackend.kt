package pl.michalmatu.aicallbridge.textagent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Android-side configuration for the developer-owned OPENAI_TEXT broker.
 *
 * The long-lived OpenAI API key is never accepted here. Android authenticates to a separate HTTPS
 * backend with an app/session bearer that must not look like a standard OpenAI key.
 */
internal class BackendOpenAiTextConfig(
    endpoint: String,
    bearerToken: String,
) {
    val endpointUrl: HttpUrl
    val bearerToken: String = bearerToken.trim()

    init {
        val parsed = endpoint.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("OpenAI text backend endpoint must be a valid URL")
        require(parsed.scheme.equals("https", ignoreCase = true)) {
            "OpenAI text backend must use HTTPS"
        }
        require(!parsed.host.equals(OPENAI_API_HOST, ignoreCase = true)) {
            "OpenAI text must go through the developer backend, never api.openai.com from Android"
        }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
            "OpenAI text backend URL must not contain user info"
        }
        require(parsed.fragment == null) {
            "OpenAI text backend URL must not contain a fragment"
        }
        require(parsed.query == null) {
            "OpenAI text backend URL must not contain a query"
        }
        require(parsed.encodedPath == REQUIRED_PATH) {
            "OpenAI text backend endpoint must use $REQUIRED_PATH"
        }
        require(this.bearerToken.length in MIN_BEARER_CHARS..MAX_BEARER_CHARS) {
            "OpenAI text backend bearer token has invalid length"
        }
        require(!this.bearerToken.startsWith(STANDARD_OPENAI_KEY_PREFIX, ignoreCase = true)) {
            "standard OpenAI API keys must never be used as the Android text-backend bearer"
        }
        require(this.bearerToken.all { it.code in PRINTABLE_ASCII_START..PRINTABLE_ASCII_END }) {
            "OpenAI text backend bearer token contains unsupported characters"
        }
        endpointUrl = parsed
    }

    override fun toString(): String =
        "BackendOpenAiTextConfig(endpoint=${endpointUrl.scheme}://${endpointUrl.host}${endpointUrl.encodedPath}, bearerToken=REDACTED)"

    private companion object {
        const val OPENAI_API_HOST = "api.openai.com"
        const val REQUIRED_PATH = "/v1/call-text-turn"
        const val STANDARD_OPENAI_KEY_PREFIX = "sk-"
        const val MIN_BEARER_CHARS = 32
        const val MAX_BEARER_CHARS = 512
        const val PRINTABLE_ASCII_START = 0x21
        const val PRINTABLE_ASCII_END = 0x7E
    }
}

/** Complete-response TextCallAgentBackend backed by the developer-owned OPENAI_TEXT broker. */
internal class BackendOpenAiTextBackend(
    private val config: BackendOpenAiTextConfig,
    private val callFactory: Call.Factory = defaultClient(),
) : TextCallAgentBackend {
    private val lock = Any()
    private var activeCall: Call? = null

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        val normalized = userText.trim()
        require(normalized.isNotEmpty()) { "user_text_must_not_be_blank" }
        require(normalized.length <= MAX_INPUT_CHARS) { "user_text_too_large" }
        cancel()
        val call = callFactory.newCall(buildRequest(normalized))
        synchronized(lock) { activeCall = call }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                if (!claim(call)) return
                listener.onError("network_${e.javaClass.simpleName}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!claim(call)) return
                    if (!response.isSuccessful) {
                        listener.onError("http_${response.code}")
                        return
                    }
                    val body = try {
                        response.body.string()
                    } catch (_: IOException) {
                        listener.onError("response_read_failed")
                        return
                    }
                    if (body.length > MAX_RESPONSE_CHARS) {
                        listener.onError("response_too_large")
                        return
                    }
                    val text = parseCompleteText(body)
                    if (text == null) {
                        listener.onError("invalid_response")
                    } else {
                        listener.onComplete(text)
                    }
                }
            }
        })
    }

    override fun cancel() {
        val call = synchronized(lock) {
            val current = activeCall
            activeCall = null
            current
        }
        call?.cancel()
    }

    internal fun buildRequest(userText: String): Request {
        val payload = JsonObject().apply { addProperty("input", userText) }.toString()
        return Request.Builder()
            .url(config.endpointUrl)
            .header("Authorization", "Bearer ${config.bearerToken}")
            .header("Cache-Control", "no-store")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    internal fun parseCompleteText(body: String): String? {
        if (body.isBlank()) return null
        val root = try {
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        } ?: return null
        val text = root.get("text") ?: return null
        if (!text.isJsonPrimitive || !text.asJsonPrimitive.isString) return null
        return text.asString.trim().takeIf { it.isNotEmpty() && it.length <= MAX_OUTPUT_CHARS }
    }

    private fun claim(call: Call): Boolean = synchronized(lock) {
        if (activeCall !== call) return@synchronized false
        activeCall = null
        true
    }

    private companion object {
        const val MAX_INPUT_CHARS = 4_000
        const val MAX_OUTPUT_CHARS = 2_000
        const val MAX_RESPONSE_CHARS = 64 * 1024
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }
}
