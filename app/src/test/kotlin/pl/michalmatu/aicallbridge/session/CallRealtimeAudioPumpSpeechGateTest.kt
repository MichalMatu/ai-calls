package pl.michalmatu.aicallbridge.session

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFormat
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeOutputPartId
import pl.michalmatu.aicallbridge.realtime.RealtimeResponseStatus
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

class CallRealtimeAudioPumpSpeechGateTest {
    private val realtimeFormat = PcmFormat(24_000, 1, 16)
    private val partId = RealtimeOutputPartId("resp_1", "item_1", 0, 0)

    @Test
    fun identifiedAudioIsHeldUntilTranscriptAudioAndResponseAreCompleteThenReleased() {
        val fixture = fixture(CallRealtimeOutputDecision.RELEASE)

        fixture.transport.emitOutputAudio(partId, frame(960, 10L))
        Thread.sleep(50L)
        assertEquals(0, fixture.output.writeCount.get())

        fixture.transport.emitTranscriptDelta(partId, "Dzień ")
        fixture.transport.emitTranscriptDone(partId, "Dzień dobry")
        fixture.transport.emitOutputAudioDone(partId)
        Thread.sleep(50L)
        assertEquals(0, fixture.output.writeCount.get())
        assertTrue(fixture.seenTranscripts.isEmpty())

        fixture.transport.emitResponseDone("resp_1", RealtimeResponseStatus.COMPLETED)

        assertTrue(
            "snapshot=${fixture.pump.snapshot()} terminal=${fixture.terminal.get()} " +
                "seen=${fixture.seenTranscripts} writes=${fixture.output.writeCount.get()}",
            fixture.output.firstWrite.await(1, TimeUnit.SECONDS),
        )
        assertEquals(listOf("Dzień dobry"), fixture.seenTranscripts)
        assertTrue(fixture.output.bytes.size() > 0)
        assertNull(fixture.terminal.get())
        fixture.close()
    }

    @Test
    fun rejectedTranscriptDropsEntireCompletedResponseWithoutTelephonyTx() {
        val fixture = fixture(CallRealtimeOutputDecision.DROP)

        fixture.transport.emitOutputAudio(partId, frame(960, 1L))
        fixture.transport.emitTranscriptDone(partId, "Nieautoryzowane potwierdzenie")
        fixture.transport.emitOutputAudioDone(partId)
        fixture.transport.emitResponseDone("resp_1", RealtimeResponseStatus.COMPLETED)

        Thread.sleep(100L)
        assertEquals(0, fixture.output.writeCount.get())
        assertEquals(listOf("Nieautoryzowane potwierdzenie"), fixture.seenTranscripts)
        assertTrue(fixture.pump.snapshot().running)
        assertNull(fixture.terminal.get())
        fixture.close()
    }

    @Test
    fun cancelledResponseDropsReadyBufferedAudioWithoutConsultingPolicy() {
        val fixture = fixture(CallRealtimeOutputDecision.RELEASE)

        fixture.transport.emitOutputAudio(partId, frame(960, 1L))
        fixture.transport.emitTranscriptDone(partId, "Nie powinno zostać odtworzone")
        fixture.transport.emitOutputAudioDone(partId)
        fixture.transport.emitResponseDone("resp_1", RealtimeResponseStatus.CANCELLED)

        Thread.sleep(100L)
        assertEquals(0, fixture.output.writeCount.get())
        assertTrue(fixture.seenTranscripts.isEmpty())
        assertTrue(fixture.pump.snapshot().running)
        assertNull(fixture.terminal.get())
        fixture.close()
    }

    @Test
    fun unknownResponseStatusFailsClosedWithoutTelephonyTx() {
        val fixture = fixture(CallRealtimeOutputDecision.RELEASE)

        fixture.transport.emitOutputAudio(partId, frame(960, 1L))
        fixture.transport.emitTranscriptDone(partId, "unknown lifecycle")
        fixture.transport.emitOutputAudioDone(partId)
        fixture.transport.emitResponseDone("resp_1", RealtimeResponseStatus.UNKNOWN)

        assertTrue(fixture.terminalLatch.await(1, TimeUnit.SECONDS))
        assertFalse(fixture.pump.snapshot().running)
        assertEquals(0, fixture.output.writeCount.get())
        fixture.close()
    }

    @Test
    fun unidentifiedAudioFailsClosedWhenSpeechGateIsEnabled() {
        val fixture = fixture(CallRealtimeOutputDecision.RELEASE)

        fixture.transport.emitLegacyAudio(frame(960, 1L))

        assertTrue(fixture.terminalLatch.await(1, TimeUnit.SECONDS))
        assertTrue(fixture.terminal.get()!!.message!!.contains("identity", ignoreCase = true))
        assertFalse(fixture.pump.snapshot().running)
        assertEquals(0, fixture.output.writeCount.get())
        fixture.close()
    }

    @Test
    fun bargeInDiscardsPendingGatedResponseAndIgnoresItsLateCompletion() {
        val fixture = fixture(CallRealtimeOutputDecision.RELEASE)

        fixture.transport.emitOutputAudio(partId, frame(960, 1L))
        fixture.transport.emitRemoteSpeechStarted()
        fixture.transport.emitTranscriptDone(partId, "late cancelled transcript")
        fixture.transport.emitOutputAudioDone(partId)
        fixture.transport.emitResponseDone("resp_1", RealtimeResponseStatus.CANCELLED)

        Thread.sleep(100L)
        assertEquals(1, fixture.transport.cancelCalls.get())
        assertEquals(0, fixture.output.writeCount.get())
        assertTrue(fixture.seenTranscripts.isEmpty())
        assertTrue(fixture.pump.snapshot().running)
        assertNull(fixture.terminal.get())
        fixture.close()
    }

    private fun fixture(decision: CallRealtimeOutputDecision): Fixture {
        val downlink = BlockingPipe()
        val output = RecordingOutputStream()
        val transport = FakeTransport()
        val terminal = AtomicReference<Throwable?>()
        val terminalLatch = CountDownLatch(1)
        val seenTranscripts = mutableListOf<String>()
        val pump = CallRealtimeAudioPump(
            bridge = CallRealtimePcmBridge(FakeLease(downlink.input, output)),
            transport = transport,
            outputApprovalPolicy = CallRealtimeOutputApprovalPolicy { _, transcript ->
                seenTranscripts += transcript
                decision
            },
            onTerminalFailure = {
                terminal.compareAndSet(null, it)
                terminalLatch.countDown()
            },
        )
        pump.start()
        return Fixture(
            pump,
            downlink,
            output,
            transport,
            terminal,
            terminalLatch,
            seenTranscripts,
        )
    }

    private fun frame(byteCount: Int, timestampNs: Long): PcmFrame =
        PcmFrame(realtimeFormat, ByteArray(byteCount) { (it and 0x7f).toByte() }, timestampNs)

    private data class Fixture(
        val pump: CallRealtimeAudioPump,
        val downlink: BlockingPipe,
        val output: RecordingOutputStream,
        val transport: FakeTransport,
        val terminal: AtomicReference<Throwable?>,
        val terminalLatch: CountDownLatch,
        val seenTranscripts: MutableList<String>,
    ) {
        fun close() {
            pump.close()
            downlink.close()
        }
    }

    private class FakeTransport : RealtimeTransport {
        private var listener: RealtimeTransport.Listener? = null
        val cancelCalls = AtomicInteger()

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> = Result.success(Unit)
        override fun sendAudio(frame: PcmFrame): Result<Unit> = Result.success(Unit)
        override fun cancelResponse(): Result<Unit> {
            cancelCalls.incrementAndGet()
            return Result.success(Unit)
        }
        override fun close() = Unit
        override fun setListener(listener: RealtimeTransport.Listener?) {
            this.listener = listener
        }

        fun emitLegacyAudio(frame: PcmFrame) {
            listener?.onAudio(frame)
        }

        fun emitOutputAudio(partId: RealtimeOutputPartId, frame: PcmFrame) {
            listener?.onOutputAudio(partId, frame)
        }

        fun emitTranscriptDelta(partId: RealtimeOutputPartId, delta: String) {
            listener?.onOutputAudioTranscriptDelta(partId, delta)
        }

        fun emitTranscriptDone(partId: RealtimeOutputPartId, transcript: String) {
            listener?.onOutputAudioTranscriptDone(partId, transcript)
        }

        fun emitOutputAudioDone(partId: RealtimeOutputPartId) {
            listener?.onOutputAudioDone(partId)
        }

        fun emitResponseDone(responseId: String, status: RealtimeResponseStatus) {
            listener?.onResponseDone(responseId, status)
        }

        fun emitRemoteSpeechStarted() {
            listener?.onRemoteSpeechStarted()
        }
    }

    private class FakeLease(
        private val input: InputStream,
        private val output: OutputStream,
    ) : CallMediaEndpointLease {
        override fun downlink(): InputStream = input
        override fun uplink(): OutputStream = output
        override fun close() = Unit
    }

    private class BlockingPipe {
        private val source = PipedOutputStream()
        val input = PipedInputStream(source, 4096)

        fun close() {
            source.close()
            input.close()
        }
    }

    private class RecordingOutputStream : OutputStream() {
        val bytes = ByteArrayOutputStream()
        val writeCount = AtomicInteger()
        val firstWrite = CountDownLatch(1)

        @Synchronized
        override fun write(b: ByteArray, off: Int, len: Int) {
            bytes.write(b, off, len)
            writeCount.incrementAndGet()
            firstWrite.countDown()
        }

        override fun write(b: Int) {
            error("single-byte writes are not expected")
        }
    }
}
