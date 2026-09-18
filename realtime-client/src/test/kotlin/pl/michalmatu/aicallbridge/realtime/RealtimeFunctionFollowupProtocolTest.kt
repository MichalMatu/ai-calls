package pl.michalmatu.aicallbridge.realtime

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeFunctionFollowupProtocolTest {
    private val protocol = RealtimeWebSocketProtocol()

    @Test
    fun autoFollowupKeepsDefaultBareResponseCreate() {
        assertEquals(
            "{\"type\":\"response.create\"}",
            protocol.responseCreate(RealtimeFunctionFollowup.Auto),
        )
    }

    @Test
    fun noToolsFollowupUsesResponseLevelToolChoiceNone() {
        val json = JsonParser.parseString(
            protocol.responseCreate(RealtimeFunctionFollowup.NoTools),
        ).asJsonObject

        assertEquals("response.create", json.get("type").asString)
        assertEquals("none", json.getAsJsonObject("response").get("tool_choice").asString)
    }

    @Test
    fun forcedFunctionFollowupUsesOfficialRealtimeToolChoiceShape() {
        val json = JsonParser.parseString(
            protocol.responseCreate(RealtimeFunctionFollowup.ForceFunction("commit_proposal")),
        ).asJsonObject
        val toolChoice = json.getAsJsonObject("response").getAsJsonObject("tool_choice")

        assertEquals("function", toolChoice.get("type").asString)
        assertEquals("commit_proposal", toolChoice.get("name").asString)
        assertFalse(json.toString().contains("authorization"))
    }

    @Test
    fun forcedFunctionNameIsValidatedBeforeSerialization() {
        assertFails<IllegalArgumentException> {
            RealtimeFunctionFollowup.ForceFunction("contains space")
        }
        assertFails<IllegalArgumentException> {
            RealtimeFunctionFollowup.ForceFunction("")
        }
    }

    @Test
    fun legacyResponseCreateRemainsAutoForSourceCompatibility() {
        assertEquals(protocol.responseCreate(RealtimeFunctionFollowup.Auto), protocol.responseCreate())
        assertTrue(protocol.responseCreate().contains("response.create"))
    }

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
