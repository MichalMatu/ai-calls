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

class RealtimeOutputTranscriptTransportRoutingTest {
    @Test
    fun routesIdentifiedAudioTranscriptAndDoneToTypedListener() {
        val connector = FakeConnector()
        val transport = RealtimeWebSocketTransport(
            connector = connector,
            epochSeconds = { 1_000L },
            monotonicNs = { 777L },
        )
        val listener = CapturingListener()
        transport.setListener(listener)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)

        connector.listener!!.onText(
            """{"type":"response.output_audio.delta","response_id":"resp_1","item_id":"item_1","output_index":0,"content_index":0,"delta":"AQACAA=="}""",
        )
        connector.listener!!.onText(
            """{"type":"response.output_audio_transcript.delta","response_id":"resp_1","item_id":"item_1","output_index":0,"content_index":0,"delta":"Dzień"}""",
        )
        connector.listener!!.onText(
            """{"type":"response.output_audio_transcript.done","response_id":"resp_1","item_id":"item_1","output_index":0,"content_index":0,"transcript":"Dzień dobry"}""",
        )
        connector.listener!!.onText(
            """{"type":"response.output_audio.done","response_id":"resp_1","item_id":"item_1","output_index":0,"content_index":0}""",
        )

        val id = RealtimeOutputPartId("resp_1", "item_1", 0, 0)
        assertEquals(listOf(id), listener.identifiedAudio.map { it.first })
        assertEquals(listOf(id to "Dzień"), listener.transcriptDeltas)
        assertEquals(listOf(id to "Dzień dobry"), listener.transcriptDone)
        assertEquals(listOf(id), listener.audioDone)
        assertEquals(0, listener.legacyAudio.size)
    }

    @Test
    fun legacyAudioWithoutIdentityStillUsesExistingAudioCallback() {
        val connector = FakeConnector()
        val transport = RealtimeWebSocketTransport(
            connector = connector,
            epochSeconds = { 1_000L },
            monotonicNs = { 888L },
        )
        val listener = CapturingListener()
        transport.setListener(listener)
        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)

        connector.listener!!.onText(
            """{"type":"response.output_audio.delta","delta":"AQACAA=="}""",
        )

        assertEquals(1, listener.legacyAudio.size)
        assertEquals(0, listener.identifiedAudio.size)
    }

    private class CapturingListener : RealtimeTransport.Listener {
        val legacyAudio = mutableListOf<PcmFrame>()
        val identifiedAudio = mutableListOf<Pair<RealtimeOutputPartId, PcmFrame>>()
        val transcriptDeltas = mutableListOf<Pair<RealtimeOutputPartId, String>>()
        val transcriptDone = mutableListOf<Pair<RealtimeOutputPartId, String>>()
        val audioDone = mutableListOf<RealtimeOutputPartId>()

        override fun onAudio(frame: PcmFrame) {
            legacyAudio += frame
        }

        override fun onOutputAudio(partId: RealtimeOutputPartId, frame: PcmFrame) {
            identifiedAudio += partId to frame
        }

        override fun onOutputAudioTranscriptDelta(partId: RealtimeOutputPartId, delta: String) {
            transcriptDeltas += partId to delta
        }

        override fun onOutputAudioTranscriptDone(partId: RealtimeOutputPartId, transcript: String) {
            transcriptDone += partId to transcript
        }

        override fun onOutputAudioDone(partId: RealtimeOutputPartId) {
            audioDone += partId
        }

        override fun onRemoteSpeechStarted() = Unit
        override fun onRemoteSpeechStopped() = Unit
        override fun onError(error: Throwable) = Unit
    }

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
        override fun send(text: String): Boolean = true
        override fun close(code: Int, reason: String): Boolean = true
    }

    private fun config() = RealtimeSessionConfig(
        sessionEndpoint = "wss://api.openai.com/v1/realtime",
        clientSecret = RealtimeClientSecret("eph_output_routing", 2_000L),
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
