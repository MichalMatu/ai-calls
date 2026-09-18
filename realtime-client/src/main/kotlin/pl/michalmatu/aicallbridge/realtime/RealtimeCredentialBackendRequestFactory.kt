package pl.michalmatu.aicallbridge.realtime

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Builds the authenticated Android -> developer-backend request used to mint one short-lived
 * Realtime client secret.
 *
 * The backend target is fixed when this factory is created, while the app bearer is fetched for
 * every request so a development/session token can be rotated without recreating the provider.
 * Standard OpenAI API keys are explicitly rejected from this client-side path.
 */
class RealtimeCredentialBackendRequestFactory(
    endpoint: String,
    private val bearerTokenProvider: () -> String,
) {
    private val endpointUrl: HttpUrl = endpoint.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Realtime credential backend endpoint must be a valid URL")

    init {
        require(endpointUrl.scheme.equals("https", ignoreCase = true)) {
            "Realtime credential backend must use HTTPS"
        }
        require(!endpointUrl.host.equals(OPENAI_API_HOST, ignoreCase = true)) {
            "Realtime credential backend must not target the OpenAI API directly"
        }
        require(endpointUrl.username.isEmpty() && endpointUrl.password.isEmpty()) {
            "Realtime credential backend URL must not contain user info"
        }
        require(endpointUrl.fragment == null) {
            "Realtime credential backend URL must not contain a fragment"
        }
    }

    fun create(): Request {
        val token = bearerTokenProvider().trim()
        require(token.length in MIN_BEARER_CHARS..MAX_BEARER_CHARS) {
            "Realtime credential backend bearer token has invalid length"
        }
        require(!token.startsWith(STANDARD_OPENAI_KEY_PREFIX, ignoreCase = true)) {
            "standard OpenAI API keys must never be used as the Android backend bearer"
        }
        require(token.all { character -> character.code in PRINTABLE_ASCII_START..PRINTABLE_ASCII_END }) {
            "Realtime credential backend bearer token contains unsupported characters"
        }

        return Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "Bearer $token")
            .header("Cache-Control", "no-store")
            .post(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    override fun toString(): String =
        "RealtimeCredentialBackendRequestFactory(endpoint=" +
            endpointUrl.scheme + "://" + endpointUrl.host + endpointUrl.encodedPath +
            ", bearerToken=REDACTED)"

    private companion object {
        const val OPENAI_API_HOST = "api.openai.com"
        const val STANDARD_OPENAI_KEY_PREFIX = "sk-"
        const val MIN_BEARER_CHARS = 32
        const val MAX_BEARER_CHARS = 512
        const val PRINTABLE_ASCII_START = 0x21
        const val PRINTABLE_ASCII_END = 0x7E
        const val EMPTY_JSON = "{}"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
