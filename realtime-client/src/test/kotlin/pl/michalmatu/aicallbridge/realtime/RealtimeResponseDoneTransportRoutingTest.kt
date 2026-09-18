package pl.michalmatu.aicallbridge.realtime

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFrame

class RealtimeResponseDoneTransportRoutingTest {
    @Test
    fun routesResponseDoneIdentityAndStatusToTypedListener() {
        val connector = FakeConnector()
        val transport = RealtimeWebSocketTransport(
            connector = connector,
            epochSeconds = { 1_000L },
        )
        val listener = CapturingListener()
        transport.setListener(listener)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)

        connector.listener!!.onText(
            """{"type":"response.done","response":{"id":"resp_1","status":"completed"}}""",
        )
        connector.listener!!.onText(
            """{"type":"response.done","response":{"id":"resp_2","status":"cancelled"}}""",
        )

        assertEquals(
            listOf(
                "resp_1" to RealtimeResponseStatus.COMPLETED,
                "resp_2" to RealtimeResponseStatus.CANCELLED,
            ),
            listener.responseDone,
        )
    }

    private class CapturingListener : RealtimeTransport.Listener {
        val responseDone = mutableListOf<Pair<String, RealtimeResponseStatus>>()

        override fun onAudio(frame: PcmFrame) = Unit
        override fun onRemoteSpeechStarted() = Unit
        override fun onRemoteSpeechStopped() = Unit
        override fun onResponseDone(responseId: String, status: RealtimeResponseStatus) {
            responseDone += responseId to status
        }
        override fun onError(error: Throwable) = Unit
    }

    private class FakeConnector : RealtimeSocketConnector {
        var listener: RealtimeSocketConnector.Listener? = null
        private val socket = object : RealtimeSocket {
            override fun send(text: String): Boolean = true
            override fun close(code: Int, reason: String): Boolean = true
        }

        override fun connect(
            handshake: RealtimeWebSocketHandshake,
            listener: RealtimeSocketConnector.Listener,
        ) {
            this.listener = listener
            listener.onOpen(socket)
        }
    }

    private fun config() = RealtimeSessionConfig(
        sessionEndpoint = "wss://api.openai.com/v1/realtime",
        clientSecret = RealtimeClientSecret("eph_response_done", 2_000L),
        model = "gpt-realtime-2.1",
        instructions = "Stay inside explicit user authority.",
    )

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
