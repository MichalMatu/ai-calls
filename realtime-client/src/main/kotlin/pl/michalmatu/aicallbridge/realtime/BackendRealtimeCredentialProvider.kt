package pl.michalmatu.aicallbridge.realtime

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches one short-lived Realtime client secret from the developer-controlled backend.
 *
 * Authentication of the app/user stays outside this class: [requestFactory] must create the
 * already-authenticated backend request. This provider deliberately refuses the OpenAI API host so
 * a standard OpenAI API key cannot be smuggled into the Android credential-minting path.
 */
class BackendRealtimeCredentialProvider(
    private val requestFactory: () -> Request,
    private val callFactory: Call.Factory = OkHttpClient(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : RealtimeCredentialProvider {
    override suspend fun fetchClientSecret(): Result<RealtimeClientSecret> = runCatching {
        val request = requestFactory()
        validateRequest(request)

        callFactory.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Realtime credential backend returned HTTP ${response.code}")
            }

            val body = response.body.string()
            if (body.length > MAX_RESPONSE_CHARS) {
                throw IOException("Realtime credential backend response is too large")
            }

            val root = parseResponse(body)
            val value = requiredString(root, "value")
            val expiresAt = requiredPositiveLong(root, "expires_at")
            val secret = RealtimeClientSecret(value, expiresAt)
            if (secret.isExpired(nowEpochSeconds())) {
                throw IOException("Realtime credential backend returned an expired client secret")
            }
            secret
        }
    }

    private fun validateRequest(request: Request) {
        if (!request.url.scheme.equals("https", ignoreCase = true)) {
            throw IllegalArgumentException("Realtime credential developer backend must use HTTPS")
        }
        if (request.method != "POST") {
            throw IllegalArgumentException("Realtime credential developer backend request must use POST")
        }
        if (request.url.host.equals(OPENAI_API_HOST, ignoreCase = true)) {
            throw IllegalArgumentException(
                "Realtime client secrets must be fetched through the developer backend, not minted directly on device",
            )
        }
        if (request.url.username.isNotEmpty() || request.url.password.isNotEmpty()) {
            throw IllegalArgumentException("Realtime credential developer backend URL must not contain user info")
        }
        if (request.url.fragment != null) {
            throw IllegalArgumentException("Realtime credential developer backend URL must not contain a fragment")
        }

        val authorization = request.header("Authorization")
        if (authorization != null && STANDARD_OPENAI_BEARER.matches(authorization.trim())) {
            throw IllegalArgumentException("standard OpenAI API keys must never be sent from the Android client")
        }
    }

    private fun parseResponse(body: String): JsonObject {
        if (body.isBlank()) {
            throw IOException("Realtime credential backend returned an empty response")
        }
        val parsed = try {
            JsonParser.parseString(body)
        } catch (_: RuntimeException) {
            throw IOException("Realtime credential backend returned invalid JSON")
        }
        if (!parsed.isJsonObject) {
            throw IOException("Realtime credential backend response must be a JSON object")
        }
        return parsed.asJsonObject
    }

    private fun requiredString(root: JsonObject, name: String): String {
        val element = root.get(name)
            ?: throw IOException("Realtime credential backend response is missing $name")
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw IOException("Realtime credential backend response has invalid $name")
        }
        val value = element.asString.trim()
        if (value.isEmpty()) {
            throw IOException("Realtime credential backend response has blank $name")
        }
        return value
    }

    private fun requiredPositiveLong(root: JsonObject, name: String): Long {
        val element = root.get(name)
            ?: throw IOException("Realtime credential backend response is missing $name")
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            throw IOException("Realtime credential backend response has invalid $name")
        }
        val value = try {
            element.asLong
        } catch (_: RuntimeException) {
            throw IOException("Realtime credential backend response has invalid $name")
        }
        if (value <= 0L) {
            throw IOException("Realtime credential backend response has invalid $name")
        }
        return value
    }

    private companion object {
        const val OPENAI_API_HOST = "api.openai.com"
        const val MAX_RESPONSE_CHARS = 64 * 1024
        val STANDARD_OPENAI_BEARER = Regex("(?i)Bearer\\s+sk-[A-Za-z0-9_-]+")
    }
}
