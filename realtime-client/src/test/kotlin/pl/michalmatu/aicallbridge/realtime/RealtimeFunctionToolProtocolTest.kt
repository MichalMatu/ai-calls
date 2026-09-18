package pl.michalmatu.aicallbridge.realtime

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeFunctionToolProtocolTest {
    private val protocol = RealtimeWebSocketProtocol()

    @Test
    fun sessionUpdateIncludesTypedFunctionToolsWithoutModelOrCredential() {
        val tool = RealtimeFunctionTool(
            name = "evaluate_proposal",
            description = "Ask the app whether a concrete proposal is within user authority.",
            parametersJson = """{"type":"object","properties":{"provider":{"type":"string"}},"required":["provider"]}""",
        )
        val config = RealtimeSessionConfig(
            sessionEndpoint = "wss://api.openai.com/v1/realtime",
            clientSecret = RealtimeClientSecret("eph_tool_test", 9_999_999_999L),
            model = "gpt-realtime-2",
            instructions = "Use the evaluation tool before committing.",
            tools = listOf(tool),
        )

        val json = JsonParser.parseString(protocol.sessionUpdate(config)).asJsonObject
        val session = json.getAsJsonObject("session")
        val tools = session.getAsJsonArray("tools")

        assertEquals("auto", session.get("tool_choice").asString)
        assertEquals(1, tools.size())
        val encoded = tools[0].asJsonObject
        assertEquals("function", encoded.get("type").asString)
        assertEquals("evaluate_proposal", encoded.get("name").asString)
        assertEquals("object", encoded.getAsJsonObject("parameters").get("type").asString)
        assertFalse(session.has("model"))
        assertFalse(protocol.sessionUpdate(config).contains("eph_tool_test"))
    }

    @Test
    fun completedFunctionCallParsesAsTypedServerEvent() {
        val event = protocol.parseServerEvent(
            """{"type":"response.output_item.done","response_id":"resp_1","output_index":0,"item":{"type":"function_call","id":"fc_1","call_id":"call_1","name":"evaluate_proposal","arguments":"{\"provider\":\"Clinic A\"}"}}""",
            123L,
        )

        assertEquals(RealtimeServerEvent.Type.FUNCTION_CALL, event.type())
        val call = event.functionCall()!!
        assertEquals("call_1", call.callId)
        assertEquals("evaluate_proposal", call.name)
        assertEquals("{\"provider\":\"Clinic A\"}", call.argumentsJson)
        assertFalse(call.toString().contains("Clinic A"))
        assertTrue(call.toString().contains("REDACTED"))
    }

    @Test
    fun nonFunctionOutputItemRemainsForwardCompatibleOtherEvent() {
        val event = protocol.parseServerEvent(
            """{"type":"response.output_item.done","item":{"type":"message","id":"msg_1"}}""",
            1L,
        )

        assertEquals(RealtimeServerEvent.Type.OTHER, event.type())
    }

    @Test
    fun functionOutputAndFollowupResponseUseOfficialEventShapes() {
        val output = JsonParser.parseString(
            protocol.functionCallOutput("call_1", "{\"decision\":\"allowed\"}"),
        ).asJsonObject

        assertEquals("conversation.item.create", output.get("type").asString)
        val item = output.getAsJsonObject("item")
        assertEquals("function_call_output", item.get("type").asString)
        assertEquals("call_1", item.get("call_id").asString)
        assertEquals("{\"decision\":\"allowed\"}", item.get("output").asString)
        assertEquals("{\"type\":\"response.create\"}", protocol.responseCreate())
    }

    @Test(expected = IllegalArgumentException::class)
    fun toolParametersMustBeJsonObjectSchema() {
        RealtimeFunctionTool("bad", "bad", "[]")
    }
}
