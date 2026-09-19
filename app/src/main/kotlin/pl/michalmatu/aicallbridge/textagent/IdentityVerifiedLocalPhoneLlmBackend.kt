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

/** Lifecycle boundary that must make the selected phone-local LLM runtime usable before HTTP. */
internal interface LocalPhoneLlmRuntimeGate : AutoCloseable {
    interface Listener {
        fun onReady()
        fun onError(reason: String)
    }

    fun ensureReady(listener: Listener)
    fun cancel()

    override fun close() = cancel()
}

/** Keeps unit/local-network callers backward compatible until production supplies a runtime owner. */
internal object AlreadyReadyLocalPhoneLlmRuntimeGate : LocalPhoneLlmRuntimeGate {
    override fun ensureReady(listener: LocalPhoneLlmRuntimeGate.Listener) = listener.onReady()
    override fun cancel() = Unit
}

/**
 * Fail-closed adapter for the phone-local llama.cpp server.
 *
 * Every turn first asks the runtime owner to make the selected server available, then verifies
 * `/props` before inference. A stale process listening on the expected port therefore cannot
 * masquerade as the selected model merely by passing `/health`.
 */
internal class IdentityVerifiedLocalPhoneLlmBackend(
    baseUrl: String,
    expectedAlias: String,
    expectedModelPath: String,
    systemPrompt: String? = null,
    private val callFactory: Call.Factory = defaultClient(),
    private val runtimeGate: LocalPhoneLlmRuntimeGate = AlreadyReadyLocalPhoneLlmRuntimeGate,
) : TextCallAgentBackend {
    private val expectedAlias = expectedAlias.trim()
    private val expectedModelPath = expectedModelPath.trim()
    private val propsUrl: HttpUrl
    private val delegate: LocalOpenAiCompatibleTextBackend
    private val lock = Any()
    private var generation = 0L
    private var activePropsCall: Call? = null

    init {
        require(this.expectedAlias.isNotEmpty()) { "expected local phone model alias must not be blank" }
        require(this.expectedModelPath.startsWith('/')) { "expected local phone model path must be absolute" }

        val backendConfig = LocalOpenAiTextBackendConfig(
            baseUrl = baseUrl,
            model = this.expectedAlias,
            systemPrompt = systemPrompt,
        )
        val parsedBaseUrl = baseUrl.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("local phone LLM endpoint URL is invalid")
        propsUrl = parsedBaseUrl.newBuilder()
            .encodedPath("/props")
            .query(null)
            .fragment(null)
            .build()
        delegate = LocalOpenAiCompatibleTextBackend(backendConfig, callFactory)
    }

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }

        val requestGeneration = synchronized(lock) {
            generation += 1
            activePropsCall?.cancel()
            activePropsCall = null
            delegate.cancel()
            generation
        }
        runtimeGate.cancel()

        try {
            runtimeGate.ensureReady(object : LocalPhoneLlmRuntimeGate.Listener {
                override fun onReady() {
                    if (!isCurrent(requestGeneration)) return
                    beginPropsVerification(userText, listener, requestGeneration)
                }

                override fun onError(reason: String) {
                    if (isCurrent(requestGeneration)) {
                        listener.onError(reason.ifBlank { "runtime_readiness_failed" })
                    }
                }
            })
        } catch (error: RuntimeException) {
            if (isCurrent(requestGeneration)) {
                listener.onError("runtime_readiness_${error.javaClass.simpleName}")
            }
        }
    }

    override fun cancel() {
        val propsCall = synchronized(lock) {
            generation += 1
            val call = activePropsCall
            activePropsCall = null
            delegate.cancel()
            call
        }
        runtimeGate.cancel()
        propsCall?.cancel()
    }

    override fun close() {
        cancel()
        runtimeGate.close()
        delegate.close()
    }

    private fun beginPropsVerification(
        userText: String,
        listener: TextCallAgentBackend.Listener,
        requestGeneration: Long,
    ) {
        val request = Request.Builder()
            .url(propsUrl)
            .get()
            .build()
        val call = callFactory.newCall(request)
        call.timeout().timeout(PROPS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val accepted = synchronized(lock) {
            if (generation != requestGeneration) {
                false
            } else {
                activePropsCall?.cancel()
                activePropsCall = call
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
                if (!claimPropsCall(call, requestGeneration)) return
                listener.onError("server_props_network_${e.javaClass.simpleName}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!claimPropsCall(call, requestGeneration)) return
                    if (!response.isSuccessful) {
                        listener.onError("server_props_http_${response.code}")
                        return
                    }
                    val body = try {
                        response.body.string()
                    } catch (_: IOException) {
                        listener.onError("server_props_read_failed")
                        return
                    }
                    if (body.length > MAX_PROPS_CHARS) {
                        listener.onError("server_props_too_large")
                        return
                    }
                    if (!matchesExpectedIdentity(body)) {
                        listener.onError("server_identity_mismatch")
                        return
                    }

                    synchronized(lock) {
                        if (generation != requestGeneration) return
                        delegate.generate(
                            userText,
                            guardedListener(requestGeneration, listener),
                        )
                    }
                }
            }
        })
    }

    private fun claimPropsCall(call: Call, requestGeneration: Long): Boolean = synchronized(lock) {
        if (generation != requestGeneration || activePropsCall !== call) return@synchronized false
        activePropsCall = null
        true
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

    private fun matchesExpectedIdentity(body: String): Boolean {
        val root = try {
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        } ?: return false

        val alias = root.get("model_alias")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?: return false
        val modelPath = root.get("model_path")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?: return false
        return alias == expectedAlias && modelPath == expectedModelPath
    }

    private companion object {
        const val MAX_PROPS_CHARS = 64 * 1024
        const val PROPS_TIMEOUT_SECONDS = 5L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(70, TimeUnit.SECONDS)
            .build()
    }
}
