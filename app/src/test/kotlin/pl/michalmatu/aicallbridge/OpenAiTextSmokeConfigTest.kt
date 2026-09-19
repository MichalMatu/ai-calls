package pl.michalmatu.aicallbridge

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiTextSmokeConfigTest {
    @Test
    fun `loads strict config and deletes source file`() {
        val file = Files.createTempFile("openai-text-smoke", ".json").toFile()
        val token = "android-session-token-abcdefghijklmnopqrstuvwxyz"
        file.writeText(
            """{"text_endpoint":"https://bridge.example.test/v1/call-text-turn","broker_token":"$token"}""",
        )

        val config = OpenAiTextSmokeConfig.loadAndDelete(file)

        assertEquals("https://bridge.example.test/v1/call-text-turn", config.textEndpoint)
        assertEquals(token, config.brokerToken)
        assertFalse(file.exists())
        assertFalse(config.toString().contains(token))
        assertFalse(config.toString().contains("bridge.example.test"))
    }

    @Test
    fun `malformed config is deleted even when parsing fails`() {
        val file = Files.createTempFile("openai-text-smoke-bad", ".json").toFile()
        file.writeText("""{"text_endpoint":"https://bridge.example.test/v1/call-text-turn","broker_token":"x","unexpected":true}""")

        try {
            OpenAiTextSmokeConfig.loadAndDelete(file)
            throw AssertionError("malformed config must fail")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertFalse(file.exists())
    }

    @Test
    fun `oversized config is deleted before use`() {
        val file = Files.createTempFile("openai-text-smoke-big", ".json").toFile()
        file.writeText("x".repeat(OpenAiTextSmokeConfig.MAX_CONFIG_BYTES + 1))

        try {
            OpenAiTextSmokeConfig.loadAndDelete(file)
            throw AssertionError("oversized config must fail")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertFalse(file.exists())
    }

    @Test
    fun `parser rejects duplicate secret fields`() {
        try {
            OpenAiTextSmokeConfig.parse(
                """{"text_endpoint":"https://bridge.example.test/v1/call-text-turn","broker_token":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","broker_token":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}""",
            )
            throw AssertionError("duplicate broker token must fail")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }
}
