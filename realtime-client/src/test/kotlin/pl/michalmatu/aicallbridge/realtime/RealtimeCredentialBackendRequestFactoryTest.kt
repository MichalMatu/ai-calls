package pl.michalmatu.aicallbridge.realtime

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeCredentialBackendRequestFactoryTest {
    @Test
    fun buildsAuthenticatedHttpsPostWithEmptyServerControlledBody() {
        val factory = RealtimeCredentialBackendRequestFactory(
            endpoint = "https://broker.example.test/v1/realtime/client-secret",
            bearerTokenProvider = { "broker-token-" + "x".repeat(24) },
        )

        val request = factory.create()
        val buffer = Buffer()
        request.body!!.writeTo(buffer)

        assertEquals("POST", request.method)
        assertEquals("https", request.url.scheme)
        assertEquals("broker.example.test", request.url.host)
        assertEquals("/v1/realtime/client-secret", request.url.encodedPath)
        assertEquals("Bearer broker-token-${"x".repeat(24)}", request.header("Authorization"))
        assertEquals("application/json; charset=utf-8", request.body!!.contentType().toString())
        assertEquals("{}", buffer.readUtf8())
        assertEquals("no-store", request.header("Cache-Control"))
        assertFalse(factory.toString().contains("broker-token"))
    }

    @Test
    fun fetchesFreshClientBearerForEveryRequest() {
        var counter = 0
        val factory = RealtimeCredentialBackendRequestFactory(
            endpoint = "https://broker.example.test/v1/realtime/client-secret",
            bearerTokenProvider = {
                counter++
                "token-$counter-" + "x".repeat(32)
            },
        )

        val first = factory.create().header("Authorization")
        val second = factory.create().header("Authorization")

        assertTrue(first!!.contains("token-1-"))
        assertTrue(second!!.contains("token-2-"))
    }

    @Test
    fun rejectsUnsafeBackendTargetsAndCredentials() {
        assertFails<IllegalArgumentException> {
            RealtimeCredentialBackendRequestFactory(
                "http://broker.example.test/token",
                { "x".repeat(32) },
            )
        }
        assertFails<IllegalArgumentException> {
            RealtimeCredentialBackendRequestFactory(
                "https://api.openai.com/v1/realtime/client_secrets",
                { "x".repeat(32) },
            )
        }
        assertFails<IllegalArgumentException> {
            RealtimeCredentialBackendRequestFactory(
                "https://user:pass@broker.example.test/token",
                { "x".repeat(32) },
            )
        }
        assertFails<IllegalArgumentException> {
            RealtimeCredentialBackendRequestFactory(
                "https://broker.example.test/token#secret",
                { "x".repeat(32) },
            )
        }

        val shortToken = RealtimeCredentialBackendRequestFactory(
            "https://broker.example.test/token",
            { "too-short" },
        )
        assertTrue(shortToken.create().let { Result.success(it) }.isSuccess.not())
    }

    @Test
    fun standardOpenAiKeyIsRejectedEvenWhenSuppliedAsBrokerToken() {
        val factory = RealtimeCredentialBackendRequestFactory(
            "https://broker.example.test/token",
            { "sk-proj-this-must-never-leave-the-device-path" },
        )

        assertFails<IllegalArgumentException> { factory.create() }
    }

    companion object {
        private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
            try {
                block()
            } catch (error: Throwable) {
                if (error is T) return error
                throw AssertionError("expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}", error)
            }
            throw AssertionError("expected ${T::class.java.simpleName}")
        }
    }
}
