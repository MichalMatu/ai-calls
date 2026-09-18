package pl.michalmatu.aicallbridge.realtime

import com.google.gson.JsonParser
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeFunctionFollowupTransportTest {
    @Test
    fun forcedFunctionFollowupIsSentOnlyAfterFunctionOutput() {
        val connector = FakeConnector()
        val transport = transport(connector)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)
        connector.socket.sent.clear()

        val result = transport.submitFunctionOutput(
            "call_eval",
            "{\"decision\":\"autonomously_allowed\"}",
            RealtimeFunctionFollowup.ForceFunction("commit_proposal"),
        )

        assertTrue(result.isSuccess)
        assertEquals(2, connector.socket.sent.size)
        assertTrue(connector.socket.sent[0].contains("function_call_output"))
        val followup = JsonParser.parseString(connector.socket.sent[1]).asJsonObject
        val choice = followup.getAsJsonObject("response").getAsJsonObject("tool_choice")
        assertEquals("function", choice.get("type").asString)
        assertEquals("commit_proposal", choice.get("name").asString)
    }

    @Test
    fun noToolsFollowupPreventsAnotherToolInImmediateResponse() {
        val connector = FakeConnector()
        val transport = transport(connector)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)
        connector.socket.sent.clear()

        val result = transport.submitFunctionOutput(
            "call_commit",
            "{\"commitment\":\"authorized\"}",
            RealtimeFunctionFollowup.NoTools,
        )

        assertTrue(result.isSuccess)
        val followup = JsonParser.parseString(connector.socket.sent[1]).asJsonObject
        assertEquals("none", followup.getAsJsonObject("response").get("tool_choice").asString)
    }

    @Test
    fun legacyFunctionOutputRemainsBareAutoFollowup() {
        val connector = FakeConnector()
        val transport = transport(connector)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)
        connector.socket.sent.clear()

        assertTrue(transport.submitFunctionOutput("call_legacy", "{\"ok\":true}").isSuccess)

        assertEquals("{\"type\":\"response.create\"}", connector.socket.sent[1])
    }

    @Test
    fun rejectedFunctionOutputNeverSendsForcedFollowup() {
        val connector = FakeConnector()
        val transport = transport(connector)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)
        connector.socket.sent.clear()
        connector.socket.acceptSends = false

        val result = transport.submitFunctionOutput(
            "call_eval",
            "{\"decision\":\"allowed\"}",
            RealtimeFunctionFollowup.ForceFunction("commit_proposal"),
        )

        assertTrue(result.isFailure)
        assertEquals(1, connector.socket.sendAttemptsAfterReset)
        assertTrue(connector.socket.sent.isEmpty())
    }

    private fun transport(connector: RealtimeSocketConnector) =
        RealtimeWebSocketTransport(
            connector = connector,
            epochSeconds = { 1_000L },
            monotonicNs = { 123L },
        )

    private fun config() = RealtimeSessionConfig(
        sessionEndpoint = "wss://api.openai.com/v1/realtime",
        clientSecret = RealtimeClientSecret("eph_followup", 2_000L),
        model = "gpt-realtime-2",
        instructions = "Use app-owned tools.",
        tools = emptyList(),
    )

    private class FakeConnector : RealtimeSocketConnector {
        var listener: RealtimeSocketConnector.Listener? = null
        val socket = FakeSocket()

        override fun connect(
            handshake: RealtimeWebSocketHandshake,
            listener: RealtimeSocketConnector.Listener,
        ) {
            this.listener = listener
            listener.onOpen(socket)
            socket.resetAttemptCounter()
        }
    }

    private class FakeSocket : RealtimeSocket {
        val sent = mutableListOf<String>()
        var acceptSends = true
        private var sendAttempts = 0
        private var baselineAttempts = 0

        val sendAttemptsAfterReset: Int
            get() = sendAttempts - baselineAttempts

        fun resetAttemptCounter() {
            baselineAttempts = sendAttempts
        }

        override fun send(text: String): Boolean {
            sendAttempts++
            if (!acceptSends) return false
            sent += text
            return true
        }

        override fun close(code: Int, reason: String): Boolean = true
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        val future = CompletableFuture<T>()
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<T>) {
                    result.fold(future::complete, future::completeExceptionally)
                }
            },
        )
        return future.get(1, TimeUnit.SECONDS)
    }
}
