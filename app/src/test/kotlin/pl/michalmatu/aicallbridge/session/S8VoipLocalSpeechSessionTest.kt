package pl.michalmatu.aicallbridge.session

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** No Android microphone, model, external SIP service or real phone calls. */
class S8VoipLocalSpeechSessionTest {
    @Test fun twoCompleteApprovedPcmTurnsTraverseOwnedLocalSpeechAndCloseBeforeModel() {
        val events = CopyOnWriteArrayList<String>()
        val input = ByteArrayInputStream(twoUtterances())
        val endpoint = FakeEndpoint(input, events)
        val owner = coordinator(endpoint)
        val generation = owner.start(16_000)
        val speech = FakeSpeech(events)
        val ended = CountDownLatch(1)
        val bridge = S8VoipLocalSpeechSession(
            coordinator = owner,
            generation = generation,
            speech = speech,
            speechExecutor = java.util.concurrent.Executor { it.run() },
            maxTurns = 2,
            observer = { if (it.phase == S8VoipLocalSpeechSession.Phase.COMPLETE) ended.countDown() },
        )

        bridge.start()
        assertTrue("two local STT/LLM/TTS cycles should finish", ended.await(4, TimeUnit.SECONDS))
        val result = bridge.snapshot()
        assertEquals(S8VoipLocalSpeechSession.Phase.COMPLETE, result.phase)
        assertEquals(2, result.turn)
        assertEquals(2, result.transcripts)
        assertEquals(2, result.approvals)
        assertEquals(4, result.outgoingFrames)
        assertTrue(result.incomingFrames >= 90)
        assertEquals(4 * 640, endpoint.bytesSent.size())
        assertEquals(2, speech.starts.get())
        assertEquals(2, speech.finishes.get())
        assertEquals(CallMediaSessionState.IDLE, owner.snapshot().state())
        assertTrue(events.indexOf("lease-closed") < events.indexOf("speech-cancel"))
        bridge.close()
        assertEquals(1, endpoint.closes.get())
    }

    @Test fun immediateStopUnblocksBlockedRxAndClosesLeaseBeforeSpeech() {
        val events = CopyOnWriteArrayList<String>()
        val feeder = PipedOutputStream()
        val endpoint = FakeEndpoint(PipedInputStream(feeder), events)
        val owner = coordinator(endpoint)
        val speech = FakeSpeech(events)
        val generation = owner.start(16_000)
        val bridge = S8VoipLocalSpeechSession(
            coordinator = owner,
            generation = generation,
            speech = speech,
            speechExecutor = java.util.concurrent.Executor { it.run() },
            maxTurns = 6,
        )

        bridge.start()
        bridge.close()
        bridge.close()
        feeder.close()
        assertEquals(S8VoipLocalSpeechSession.Phase.STOPPED, bridge.snapshot().phase)
        assertEquals(CallMediaSessionState.IDLE, owner.snapshot().state())
        assertEquals(1, endpoint.closes.get())
        assertTrue(events.indexOf("lease-closed") < events.indexOf("speech-cancel"))
    }

    @Test fun staleSpeechCallbackCannotRestartAfterStop() {
        val events = CopyOnWriteArrayList<String>()
        val endpoint = FakeEndpoint(ByteArrayInputStream(ByteArray(0)), events)
        val owner = coordinator(endpoint)
        val speech = FakeSpeech(events)
        val generation = owner.start(16_000)
        val bridge = S8VoipLocalSpeechSession(
            owner, generation, speech, java.util.concurrent.Executor { it.run() },
            maxTurns = 2,
        )
        bridge.start()
        bridge.close()
        speech.lastListener?.onOutputPcm16(ByteArray(640))
        speech.lastListener?.onReady()
        assertEquals(S8VoipLocalSpeechSession.Phase.STOPPED, bridge.snapshot().phase)
        assertEquals(0, bridge.snapshot().outgoingFrames)
        assertEquals(1, speech.starts.get())
    }

    private fun coordinator(endpoint: FakeEndpoint) = CallMediaSessionCoordinator(
        object : CallMediaSessionBackend {
            override fun bind(generation: Long, callback: CallMediaSessionBackend.BindCallback) {
                callback.onBound(generation)
            }
            override fun prepare(generation: Long, sampleRateHz: Int) {
                assertEquals(16_000, sampleRateHz)
            }
            override fun start(generation: Long): CallMediaEndpointLease = endpoint
            override fun heartbeat(generation: Long): Boolean = endpoint.closes.get() == 0
            override fun abort(generation: Long) = Unit
            override fun unbind(generation: Long) = Unit
        },
        CallMediaHeartbeatScheduler { _, _ -> AutoCloseable { } },
        CallMediaMonotonicClock { System.nanoTime() / 1_000_000L },
        { },
    )

    private class FakeEndpoint(
        private val input: InputStream,
        private val events: CopyOnWriteArrayList<String>,
    ) : CallMediaEndpointLease {
        val bytesSent = ByteArrayOutputStream()
        val closes = AtomicInteger()
        override fun downlink(): InputStream = input
        override fun uplink(): OutputStream = bytesSent
        override fun close() {
            if (closes.incrementAndGet() == 1) {
                events += "lease-closed"
                input.close()
            }
        }
    }

    private class FakeSpeech(private val events: CopyOnWriteArrayList<String>) :
        S8VoipLocalSpeechSession.SpeechTurn {
        val starts = AtomicInteger()
        val finishes = AtomicInteger()
        @Volatile var lastListener: S8VoipLocalSpeechSession.SpeechTurn.Listener? = null

        override fun start(listener: S8VoipLocalSpeechSession.SpeechTurn.Listener) {
            starts.incrementAndGet()
            lastListener = listener
            listener.onReady()
        }
        override fun writeInputPcm(frame: ByteArray): Boolean = true
        override fun finishInput() {
            finishes.incrementAndGet()
            val listener = checkNotNull(lastListener)
            listener.onTranscript()
            listener.onApproved()
            val pcm16 = ByteArray(1280) { if (it % 2 == 0) 0x40 else 0x05 }
            listener.onOutputPcm16(pcm16)
        }
        override fun cancel() { events += "speech-cancel" }
        override fun close() { events += "speech-close" }
    }

    private fun twoUtterances(): ByteArray {
        val output = ByteArrayOutputStream()
        repeat(2) {
            repeat(12) { output.write(voiceFrame()) }
            repeat(36) { output.write(ByteArray(640)) }
        }
        return output.toByteArray()
    }

    private fun voiceFrame() = ByteArray(640).also { bytes ->
        for (i in 0 until 320) {
            val sample = if (i % 16 < 8) 2100 else -2100
            bytes[i * 2] = sample.toByte()
            bytes[i * 2 + 1] = (sample shr 8).toByte()
        }
    }
}
