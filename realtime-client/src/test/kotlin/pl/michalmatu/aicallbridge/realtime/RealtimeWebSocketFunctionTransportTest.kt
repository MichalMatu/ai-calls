package pl.michalmatu.aicallbridge.realtime

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeWebSocketFunctionTransportTest {
    @Test
    fun incomingFunctionCallReachesTypedListener() {
        val connector = FakeConnector()
        val transport = transport(connector)
        val listener = CapturingListener()
        transport.setListener(listener)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)

        connector.listener!!.onText(
            """{"type":"response.output_item.done","response_id":"resp_1","item":{"type":"function_call","call_id":"call_1","name":"evaluate_proposal","arguments":"{\"provider\":\"Clinic A\"}"}}""",
        )

        assertEquals(1, listener.functionCalls.size)
        assertEquals("resp_1", listener.functionCalls.single().responseId)
        assertEquals("call_1", listener.functionCalls.single().callId)
        assertEquals("evaluate_proposal", listener.functionCalls.single().name)
    }

    @Test
    fun submitFunctionOutputSendsOutputThenRequestsFollowupResponse() {
        val connector = FakeConnector()
        val transport = transport(connector)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)
        connector.socket.sent.clear()

        val result = transport.submitFunctionOutput(
            "call_1",
            "{\"decision\":\"allowed\"}",
        )

        assertTrue(result.isSuccess)
        assertEquals(2, connector.socket.sent.size)
        assertTrue(connector.socket.sent[0].contains("conversation.item.create"))
        assertTrue(connector.socket.sent[0].contains("function_call_output"))
        assertTrue(connector.socket.sent[0].contains("call_1"))
        assertEquals("{\"type\":\"response.create\"}", connector.socket.sent[1])
    }

    @Test
    fun rejectedFirstFunctionOutputEventDoesNotSendResponseCreate() {
        val connector = FakeConnector()
        val transport = transport(connector)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)
        connector.socket.sent.clear()
        connector.socket.sendAttempts = 0
        connector.socket.acceptSends = false

        val result = transport.submitFunctionOutput("call_1", "{\"decision\":\"allowed\"}")

        assertTrue(result.isFailure)
        assertEquals(1, connector.socket.sendAttempts)
        assertTrue(connector.socket.sent.isEmpty())
    }

    @Test
    fun functionOutputFailsAfterSocketFailure() {
        val connector = FakeConnector()
        val transport = transport(connector)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)
        connector.listener!!.onFailure(IllegalStateException("socket lost"))

        assertTrue(
            transport.submitFunctionOutput("call_1", "{\"decision\":\"allowed\"}").isFailure,
        )
    }

    @Test
    fun malformedFunctionCallNotifiesErrorInsteadOfBusinessListener() {
        val connector = FakeConnector()
        val transport = transport(connector)
        val listener = CapturingListener()
        transport.setListener(listener)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)

        connector.listener!!.onText(
            """{"type":"response.output_item.done","response_id":"resp_bad","item":{"type":"function_call","call_id":"call_1","name":"evaluate_proposal"}}""",
        )

        assertTrue(listener.functionCalls.isEmpty())
        assertEquals(1, listener.errors.size)
    }

    private fun transport(connector: RealtimeSocketConnector) =
        RealtimeWebSocketTransport(
            connector = connector,
            epochSeconds = { 1_000L },
            monotonicNs = { 123L },
        )

    private fun config() = RealtimeSessionConfig(
        sessionEndpoint = "wss://api.openai.com/v1/realtime",
        clientSecret = RealtimeClientSecret("eph_function", 2_000L),
        model = "gpt-realtime-2",
        instructions = "Use application-owned tools for decisions.",
        tools = listOf(
            RealtimeFunctionTool(
                "evaluate_proposal",
                "Evaluate proposal",
                "{\"type\":\"object\",\"properties\":{}}",
            ),
        ),
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
        }
    }

    private class FakeSocket : RealtimeSocket {
        val sent = mutableListOf<String>()
        var sendAttempts = 0
        var acceptSends = true

        override fun send(text: String): Boolean {
            sendAttempts++
            if (!acceptSends) return false
            sent += text
            return true
        }

        override fun close(code: Int, reason: String): Boolean = true
    }

    private class CapturingListener : RealtimeTransport.Listener {
        val functionCalls = mutableListOf<RealtimeFunctionCall>()
        val errors = mutableListOf<Throwable>()

        override fun onAudio(frame: pl.michalmatu.aicallbridge.audio.PcmFrame) = Unit
        override fun onRemoteSpeechStarted() = Unit
        override fun onRemoteSpeechStopped() = Unit
        override fun onFunctionCall(call: RealtimeFunctionCall) {
            functionCalls += call
        }
        override fun onError(error: Throwable) {
            errors += error
        }
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
