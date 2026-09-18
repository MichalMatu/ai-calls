package pl.michalmatu.aicallbridge

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeNetworkSmokeConfigTest {
    @Test
    fun validPrivateConfigIsLoadedAndDeletedAfterRead() {
        val file = Files.createTempFile("realtime-network-smoke", ".json").toFile()
        val token = "broker-token-" + "x".repeat(24)
        file.writeText(
            """{"credential_endpoint":"https://broker.example.test/v1/realtime/client-secret","broker_token":"$token"}""",
        )

        val config = RealtimeNetworkSmokeConfig.loadAndDelete(file)

        assertEquals("https://broker.example.test/v1/realtime/client-secret", config.credentialEndpoint)
        assertEquals(token, config.brokerToken)
        assertFalse(file.exists())
        assertFalse(config.toString().contains(token))
        assertFalse(config.toString().contains("broker.example.test"))
    }

    @Test
    fun malformedOrUnexpectedConfigFailsClosedAndIsStillDeleted() {
        val malformed = Files.createTempFile("realtime-network-smoke", ".json").toFile()
        malformed.writeText("not-json")
        assertFails<IllegalArgumentException> { RealtimeNetworkSmokeConfig.loadAndDelete(malformed) }
        assertFalse(malformed.exists())

        val extra = Files.createTempFile("realtime-network-smoke", ".json").toFile()
        extra.writeText(
            """{"credential_endpoint":"https://broker.example.test/token","broker_token":"${"x".repeat(32)}","extra":"nope"}""",
        )
        assertFails<IllegalArgumentException> { RealtimeNetworkSmokeConfig.loadAndDelete(extra) }
        assertFalse(extra.exists())
    }

    @Test
    fun missingOrOversizedConfigIsRejectedWithoutCreatingPersistentSecrets() {
        val missing = Files.createTempDirectory("realtime-network-smoke-missing").resolve("config.json").toFile()
        assertFails<IllegalArgumentException> { RealtimeNetworkSmokeConfig.loadAndDelete(missing) }
        assertFalse(missing.exists())

        val oversized = Files.createTempFile("realtime-network-smoke", ".json").toFile()
        oversized.writeText("{" + "x".repeat(RealtimeNetworkSmokeConfig.MAX_CONFIG_BYTES + 1) + "}")
        assertFails<IllegalArgumentException> { RealtimeNetworkSmokeConfig.loadAndDelete(oversized) }
        assertFalse(oversized.exists())
    }

    @Test
    fun blankRequiredValuesAreRejectedAndDeleted() {
        val file = Files.createTempFile("realtime-network-smoke", ".json").toFile()
        file.writeText("""{"credential_endpoint":" ","broker_token":" "}""")

        assertFails<IllegalArgumentException> { RealtimeNetworkSmokeConfig.loadAndDelete(file) }
        assertFalse(file.exists())
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
