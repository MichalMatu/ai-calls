package pl.michalmatu.aicallbridge.realtime

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFormat
import pl.michalmatu.aicallbridge.audio.PcmFrame

class RealtimeWebSocketTransportTest {
    private val pcm24 = PcmFormat(24_000, 1, 16)

    @Test
    fun connectUsesHardenedHandshakeAndSendsSessionUpdateOnOpen() {
        val connector = FakeConnector(autoOpen = true)
        val transport = transport(connector)

        val result = runSuspend { transport.connect(config()) }

        assertTrue(result.isSuccess)
        assertEquals(
            "wss://api.openai.com/v1/realtime?model=gpt-realtime-2.1",
            connector.handshake?.requestUrl(),
        )
        assertEquals(
            listOf("realtime", "openai-insecure-api-key.eph_transport"),
            connector.handshake?.protocols(),
        )
        assertTrue(connector.socket.sent.single().contains("\"type\":\"session.update\""))
        assertFalse(connector.socket.sent.single().contains("\"model\""))
    }

    @Test
    fun audioCancelAndServerEventsFlowThroughTypedProtocol() {
        val connector = FakeConnector(autoOpen = true)
        val transport = transport(connector)
        val events = CapturingListener()
        transport.setListener(events)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)

        val audio = PcmFrame(pcm24, byteArrayOf(1, 2, 3, 4), 10L)
        assertTrue(runSuspend { transport.sendAudio(audio) }.isSuccess)
        assertTrue(runSuspend { transport.cancelResponse() }.isSuccess)
        assertTrue(connector.socket.sent[1].contains("input_audio_buffer.append"))
        assertEquals("{\"type\":\"response.cancel\"}", connector.socket.sent[2])

        connector.listener!!.onText(
            "{\"type\":\"response.output_audio.delta\",\"delta\":\"BQYHCA==\"}",
        )
        connector.listener!!.onText("{\"type\":\"input_audio_buffer.speech_started\"}")
        connector.listener!!.onText("{\"type\":\"input_audio_buffer.speech_stopped\"}")
        connector.listener!!.onText(
            "{\"type\":\"error\",\"error\":{\"message\":\"recoverable\"}}",
        )
        connector.listener!!.onText("{\"type\":\"future.event\"}")

        assertEquals(1, events.audio.size)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), events.audio.single().data)
        assertEquals(777L, events.audio.single().monotonicTimestampNs)
        assertEquals(1, events.speechStarted)
        assertEquals(1, events.speechStopped)
        assertEquals("recoverable", events.errors.single().message)
    }

    @Test
    fun expiredCredentialFailsBeforeConnectorCanSeeIt() {
        val connector = FakeConnector(autoOpen = true)
        val transport = transport(connector, epochSeconds = 2_000L)
        val expired = config(RealtimeClientSecret("eph_expired", 2_000L))

        val result = runSuspend { transport.connect(expired) }

        assertTrue(result.isFailure)
        assertNull(connector.handshake)
        assertNull(connector.listener)
    }

    @Test
    fun connectionFailureAndPostConnectFailureAreFailClosed() {
        val connector = FakeConnector(autoOpen = false)
        val transport = transport(connector)
        val events = CapturingListener()
        transport.setListener(events)

        val connectFuture = startSuspend { transport.connect(config()) }
        assertNotNull(connector.listener)
        connector.listener!!.onFailure(IllegalStateException("handshake failed"))
        assertTrue(connectFuture.get(1, TimeUnit.SECONDS).isFailure)

        val secondConnector = FakeConnector(autoOpen = true)
        val second = transport(secondConnector)
        val secondEvents = CapturingListener()
        second.setListener(secondEvents)
        assertTrue(runSuspend { second.connect(config()) }.isSuccess)
        secondConnector.listener!!.onFailure(IllegalStateException("socket lost"))

        assertEquals("socket lost", secondEvents.errors.single().message)
        assertTrue(runSuspend { second.sendAudio(PcmFrame(pcm24, byteArrayOf(1, 2), 1L)) }.isFailure)
    }

    @Test
    fun duplicateConnectIsRejectedAndCloseIsIdempotent() {
        val connector = FakeConnector(autoOpen = true)
        val transport = transport(connector)

        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)
        assertTrue(runSuspend { transport.connect(config()) }.isFailure)
        runSuspend { transport.close() }
        runSuspend { transport.close() }

        assertEquals(1, connector.socket.closeCount)
        assertTrue(runSuspend { transport.sendAudio(PcmFrame(pcm24, byteArrayOf(1, 2), 1L)) }.isFailure)
    }

    private fun transport(
        connector: RealtimeSocketConnector,
        epochSeconds: Long = 1_000L,
    ) = RealtimeWebSocketTransport(
        connector = connector,
        epochSeconds = { epochSeconds },
        monotonicNs = { 777L },
    )

    private fun config(
        secret: RealtimeClientSecret = RealtimeClientSecret("eph_transport", 2_000L),
    ) = RealtimeSessionConfig(
        sessionEndpoint = "wss://api.openai.com/v1/realtime",
        clientSecret = secret,
        model = "gpt-realtime-2.1",
        instructions = "Stay inside explicit user authority.",
    )

    private class FakeConnector(
        private val autoOpen: Boolean,
    ) : RealtimeSocketConnector {
        var handshake: RealtimeWebSocketHandshake? = null
        var listener: RealtimeSocketConnector.Listener? = null
        val socket = FakeSocket()

        override fun connect(
            handshake: RealtimeWebSocketHandshake,
            listener: RealtimeSocketConnector.Listener,
        ) {
            this.handshake = handshake
            this.listener = listener
            if (autoOpen) {
                listener.onOpen(socket)
            }
        }
    }

    private class FakeSocket : RealtimeSocket {
        val sent = mutableListOf<String>()
        var closeCount = 0

        override fun send(text: String): Boolean {
            sent += text
            return true
        }

        override fun close(code: Int, reason: String): Boolean {
            closeCount++
            return true
        }
    }

    private class CapturingListener : RealtimeTransport.Listener {
        val audio = mutableListOf<PcmFrame>()
        var speechStarted = 0
        var speechStopped = 0
        val errors = mutableListOf<Throwable>()

        override fun onAudio(frame: PcmFrame) {
            audio += frame
        }

        override fun onRemoteSpeechStarted() {
            speechStarted++
        }

        override fun onRemoteSpeechStopped() {
            speechStopped++
        }

        override fun onError(error: Throwable) {
            errors += error
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T =
        startSuspend(block).get(1, TimeUnit.SECONDS)

    private fun <T> startSuspend(block: suspend () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    result.fold(future::complete, future::completeExceptionally)
                }
            },
        )
        return future
    }
}
