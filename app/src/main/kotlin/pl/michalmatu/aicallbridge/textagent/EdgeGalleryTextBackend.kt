package pl.michalmatu.aicallbridge.textagent

import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Fail-closed adapter for a phone-local Google AI Edge Gallery OpenAI-compatible endpoint.
 *
 * Edge Gallery remains an inference provider only. Every generation proves the server is healthy
 * and that the exact expected model is advertised before delegating the completion to the shared
 * OpenAI-compatible backend. Product READY_TO_DIAL still performs the final warm-up.
 */
internal class EdgeGalleryTextBackend(
    baseUrl: String,
    expectedModelId: String,
    bearerToken: String? = null,
    systemPrompt: String? = null,
    private val callFactory: Call.Factory = defaultClient(),
) : TextCallAgentBackend {
    private val expectedModelId = expectedModelId.trim()
    private val config = LocalOpenAiTextBackendConfig(
        baseUrl = baseUrl,
        model = this.expectedModelId,
        bearerToken = bearerToken,
        systemPrompt = systemPrompt,
    )
    private val healthUrl: HttpUrl
    private val modelsUrl: HttpUrl
    private val delegate = LocalOpenAiCompatibleTextBackend(config, callFactory)
    private val lock = Any()
    private var generation = 0L
    private var activeReadinessCall: Call? = null

    init {
        require(this.expectedModelId.isNotEmpty()) { "Edge Gallery model id must not be blank" }
        val parsed = baseUrl.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Edge Gallery endpoint URL is invalid")
        require(isLoopback(parsed.host)) {
            "Edge Gallery endpoint must use phone loopback only"
        }
        healthUrl = parsed.newBuilder()
            .encodedPath("/health")
            .query(null)
            .fragment(null)
            .build()
        modelsUrl = parsed.newBuilder()
            .encodedPath("/v1/models")
            .query(null)
            .fragment(null)
            .build()
    }

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        val requestGeneration = synchronized(lock) {
            generation += 1
            activeReadinessCall?.cancel()
            activeReadinessCall = null
            delegate.cancel()
            generation
        }
        beginHealthCheck(userText, listener, requestGeneration)
    }

    override fun cancel() {
        val call = synchronized(lock) {
            generation += 1
            val current = activeReadinessCall
            activeReadinessCall = null
            delegate.cancel()
            current
        }
        call?.cancel()
    }

    override fun close() {
        cancel()
        delegate.close()
    }

    private fun beginHealthCheck(
        userText: String,
        listener: TextCallAgentBackend.Listener,
        requestGeneration: Long,
    ) {
        enqueueReadiness(
            request = getRequest(healthUrl),
            requestGeneration = requestGeneration,
            networkErrorPrefix = "edge_gallery_health_network_",
            listener = listener,
        ) { response ->
            if (!response.isSuccessful) {
                listener.onError("edge_gallery_health_http_${response.code}")
                return@enqueueReadiness
            }
            val body = readBoundedBody(response, listener, "edge_gallery_health")
                ?: return@enqueueReadiness
            if (!isHealthy(body)) {
                listener.onError("edge_gallery_health_invalid")
                return@enqueueReadiness
            }
            beginModelCheck(userText, listener, requestGeneration)
        }
    }

    private fun beginModelCheck(
        userText: String,
        listener: TextCallAgentBackend.Listener,
        requestGeneration: Long,
    ) {
        enqueueReadiness(
            request = getRequest(modelsUrl),
            requestGeneration = requestGeneration,
            networkErrorPrefix = "edge_gallery_models_network_",
            listener = listener,
        ) { response ->
            if (!response.isSuccessful) {
                listener.onError("edge_gallery_models_http_${response.code}")
                return@enqueueReadiness
            }
            val body = readBoundedBody(response, listener, "edge_gallery_models")
                ?: return@enqueueReadiness
            if (!containsExpectedModel(body)) {
                listener.onError("edge_gallery_model_not_available")
                return@enqueueReadiness
            }
            synchronized(lock) {
                if (generation != requestGeneration) return@enqueueReadiness
                delegate.generate(userText, guardedListener(requestGeneration, listener))
            }
        }
    }

    private fun enqueueReadiness(
        request: Request,
        requestGeneration: Long,
        networkErrorPrefix: String,
        listener: TextCallAgentBackend.Listener,
        onResponse: (Response) -> Unit,
    ) {
        val call = callFactory.newCall(request)
        val accepted = synchronized(lock) {
            if (generation != requestGeneration) {
                false
            } else {
                activeReadinessCall?.cancel()
                activeReadinessCall = call
                true
            }
        }
        if (!accepted) {
            call.cancel()
            return
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                if (!claimReadinessCall(call, requestGeneration)) return
                listener.onError(networkErrorPrefix + e.javaClass.simpleName)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!claimReadinessCall(call, requestGeneration)) return
                    onResponse(response)
                }
            }
        })
    }

    private fun claimReadinessCall(call: Call, requestGeneration: Long): Boolean = synchronized(lock) {
        if (generation != requestGeneration || activeReadinessCall !== call) return@synchronized false
        activeReadinessCall = null
        true
    }

    private fun getRequest(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .get()
        .apply {
            config.bearerToken?.let { header("Authorization", "Bearer $it") }
        }
        .build()

    private fun readBoundedBody(
        response: Response,
        listener: TextCallAgentBackend.Listener,
        prefix: String,
    ): String? {
        val body = try {
            response.body.string()
        } catch (_: IOException) {
            listener.onError("${prefix}_read_failed")
            return null
        }
        if (body.length > MAX_READINESS_CHARS) {
            listener.onError("${prefix}_too_large")
            return null
        }
        return body
    }

    private fun isHealthy(body: String): Boolean {
        val root = parseObject(body) ?: return false
        val status = root.get("status") ?: return false
        return status.isJsonPrimitive && status.asJsonPrimitive.isString && status.asString == "ok"
    }

    private fun containsExpectedModel(body: String): Boolean {
        val root = parseObject(body) ?: return false
        val data = root.getAsJsonArray("data") ?: return false
        return data.any { element ->
            if (!element.isJsonObject) return@any false
            val id = element.asJsonObject.get("id") ?: return@any false
            id.isJsonPrimitive && id.asJsonPrimitive.isString && id.asString == expectedModelId
        }
    }

    private fun parseObject(body: String) = try {
        JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
    } catch (_: RuntimeException) {
        null
    }

    private fun guardedListener(
        requestGeneration: Long,
        listener: TextCallAgentBackend.Listener,
    ) = object : TextCallAgentBackend.Listener {
        override fun onComplete(text: String) {
            if (isCurrent(requestGeneration)) listener.onComplete(text)
        }

        override fun onError(reason: String) {
            if (isCurrent(requestGeneration)) listener.onError(reason)
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) {
        generation == requestGeneration
    }

    private companion object {
        const val MAX_READINESS_CHARS = 64 * 1024

        fun isLoopback(host: String): Boolean =
            host.equals("localhost", ignoreCase = true) || host == "127.0.0.1" || host == "::1"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(70, TimeUnit.SECONDS)
            .build()
    }
}

/** Production defaults for the explicitly selected Edge Gallery/Gemma provider. */
internal object EdgeGalleryTextBackendFactory {
    const val BASE_URL = "http://127.0.0.1:8080/v1/"
    const val MODEL = "Gemma-3n-E2B-it"

    fun create(bearerToken: String? = null): TextCallAgentBackend = EdgeGalleryTextBackend(
        baseUrl = BASE_URL,
        expectedModelId = MODEL,
        bearerToken = bearerToken,
        systemPrompt = LocalPhoneLlmBackendFactory.SYSTEM_PROMPT,
    )
}
