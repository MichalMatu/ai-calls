package pl.michalmatu.aicallbridge.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeCredentialBackendProviderFactoryTest {
    @Test
    fun createsProviderThroughTransportNeutralPublicBoundary() {
        val token = "broker-token-" + "x".repeat(24)

        val provider: RealtimeCredentialProvider = RealtimeCredentialBackendProviderFactory.create(
            endpoint = "https://broker.example.test/v1/realtime/client-secret",
            bearerTokenProvider = { token },
        )

        assertNotNull(provider)
        assertTrue(provider is RealtimeCredentialProvider)
        assertFalse(provider.toString().contains(token))
    }

    @Test
    fun rejectsUnsafeEndpointBeforeAnyCredentialFetch() {
        var tokenReads = 0

        assertFails<IllegalArgumentException> {
            RealtimeCredentialBackendProviderFactory.create(
                endpoint = "http://broker.example.test/v1/realtime/client-secret",
                bearerTokenProvider = {
                    tokenReads++
                    "x".repeat(32)
                },
            )
        }

        assertTrue(tokenReads == 0)
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
