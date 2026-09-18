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

/** Configuration for a local, OpenAI-compatible text endpoint such as Ollama on the user's Mac. */
internal class LocalOpenAiTextBackendConfig(
    baseUrl: String,
    model: String,
    bearerToken: String? = null,
) {
    val model: String = model.trim()
    val bearerToken: String? = bearerToken?.trim()?.takeIf { it.isNotEmpty() }
    val chatCompletionsUrl: HttpUrl

    init {
        require(this.model.isNotEmpty()) { "local text model must not be blank" }
        require(this.model.length <= MAX_MODEL_CHARS) { "local text model is too long" }
        if (this.bearerToken != null) {
            require(this.bearerToken.length <= MAX_TOKEN_CHARS) { "local text bearer token is too long" }
            require(!STANDARD_OPENAI_KEY.matches(this.bearerToken)) {
                "standard OpenAI API keys must never be stored in the local Mac backend config"
            }
        }

        val parsed = baseUrl.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("local text endpoint URL is invalid")
        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "local text endpoint must use http or https"
        }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
            "local text endpoint URL must not contain user info"
        }
        require(parsed.query == null && parsed.fragment == null) {
            "local text endpoint URL must not contain query or fragment"
        }
        require(isLocalHost(parsed.host)) {
            "local text endpoint host must be loopback, RFC1918, link-local, Tailscale CGNAT, or .local"
        }
        chatCompletionsUrl = parsed.newBuilder()
            .encodedPath("/v1/chat/completions")
            .query(null)
            .fragment(null)
            .build()
    }

    private companion object {
        const val MAX_MODEL_CHARS = 160
        const val MAX_TOKEN_CHARS = 512
        val STANDARD_OPENAI_KEY = Regex("(?i)sk-[A-Za-z0-9_-]{8,}")

        fun isLocalHost(host: String): Boolean {
            val value = host.lowercase()
            if (value == "localhost" || value.endsWith(".local")) return true
            if (value == "::1" || value.startsWith("fe80:") || value.startsWith("fc") || value.startsWith("fd")) {
                return true
            }
            val parts = value.split('.')
            if (parts.size != 4) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            return when {
                octets[0] == 10 -> true
                octets[0] == 127 -> true
                octets[0] == 169 && octets[1] == 254 -> true
                octets[0] == 172 && octets[1] in 16..31 -> true
                octets[0] == 192 && octets[1] == 168 -> true
                octets[0] == 100 && octets[1] in 64..127 -> true
                else -> false
            }
        }
    }
}

/** Complete-response adapter for a local OpenAI-compatible `/v1/chat/completions` endpoint. */
internal class LocalOpenAiCompatibleTextBackend(
    private val config: LocalOpenAiTextBackendConfig,
    private val callFactory: Call.Factory = defaultClient(),
) : TextCallAgentBackend {
    private val lock = Any()
    private var activeCall: Call? = null

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        cancel()
        val request = Request.Builder()
            .url(config.chatCompletionsUrl)
            .post(requestJson(userText).toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                config.bearerToken?.let { header("Authorization", "Bearer $it") }
            }
            .build()
        val call = callFactory.newCall(request)
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
            val value = activeCall
            activeCall = null
            value
        }
        call?.cancel()
    }

    private fun claim(call: Call): Boolean = synchronized(lock) {
        if (activeCall !== call) return@synchronized false
        activeCall = null
        true
    }

    private fun requestJson(userText: String): String {
        val root = JsonObject().apply {
            addProperty("model", config.model)
            addProperty("stream", false)
            add("messages", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userText)
                })
            })
        }
        return root.toString()
    }

    private fun parseCompleteText(body: String): String? {
        if (body.isBlank()) return null
        val root = try {
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        } ?: return null
        val choices = root.getAsJsonArray("choices") ?: return null
        if (choices.size() == 0) return null
        val first = choices[0].takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val message = first.getAsJsonObject("message") ?: return null
        val content = message.get("content") ?: return null
        if (!content.isJsonPrimitive || !content.asJsonPrimitive.isString) return null
        return content.asString.trim().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 64 * 1024
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(70, TimeUnit.SECONDS)
            .build()
    }
}
