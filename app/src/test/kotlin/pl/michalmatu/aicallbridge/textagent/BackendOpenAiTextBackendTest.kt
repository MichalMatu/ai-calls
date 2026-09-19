package pl.michalmatu.aicallbridge.textagent

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackendOpenAiTextBackendTest {
    private val sessionToken = "android-session-token-abcdefghijklmnopqrstuvwxyz"

    @Test
    fun `builds narrow authenticated developer backend request`() {
        val config = BackendOpenAiTextConfig(
            "https://bridge.example.test/v1/call-text-turn",
            sessionToken,
        )
        val backend = BackendOpenAiTextBackend(config)
        val request = backend.buildRequest("  Dzień dobry  ")
        val buffer = Buffer()
        request.body!!.writeTo(buffer)

        assertEquals("https://bridge.example.test/v1/call-text-turn", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("Bearer $sessionToken", request.header("Authorization"))
        assertEquals("no-store", request.header("Cache-Control"))
        assertEquals("{\"input\":\"  Dzień dobry  \"}", buffer.readUtf8())
        assertFalse(config.toString().contains(sessionToken))
        backend.close()
    }

    @Test
    fun `parses only bounded complete text`() {
        val backend = BackendOpenAiTextBackend(
            BackendOpenAiTextConfig("https://bridge.example.test/v1/call-text-turn", sessionToken),
        )
        assertEquals("Krótka odpowiedź.", backend.parseCompleteText("""{"text":" Krótka odpowiedź. ","model":"gpt-5.6-luna"}"""))
        assertEquals(null, backend.parseCompleteText("{}"))
        assertEquals(null, backend.parseCompleteText("{\"text\":7}"))
        backend.close()
    }

    @Test
    fun `rejects direct OpenAI endpoint`() {
        try {
            BackendOpenAiTextConfig("https://api.openai.com/v1/call-text-turn", sessionToken)
            fail("Android must never target api.openai.com for OPENAI_TEXT")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("developer backend", ignoreCase = true))
        }
    }

    @Test
    fun `rejects standard OpenAI key shaped Android bearer`() {
        try {
            BackendOpenAiTextConfig(
                "https://bridge.example.test/v1/call-text-turn",
                "sk-this-key-must-never-live-on-android",
            )
            fail("standard OpenAI API key shaped bearer must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("standard OpenAI", ignoreCase = true))
        }
    }

    @Test
    fun `requires https exact broker path and no query`() {
        val invalid = listOf(
            "http://bridge.example.test/v1/call-text-turn",
            "https://bridge.example.test/wrong",
            "https://bridge.example.test/v1/call-text-turn?secret=no",
        )
        invalid.forEach { endpoint ->
            try {
                BackendOpenAiTextConfig(endpoint, sessionToken)
                fail("invalid backend endpoint must be rejected: $endpoint")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }
}
