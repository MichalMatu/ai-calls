package pl.michalmatu.aicallbridge.session

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
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
import pl.michalmatu.aicallbridge.realtime.RealtimeClientSecret
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

class CallRealtimeAudioPumpTest {
    private val realtimeFormat = PcmFormat(24_000, 1, 16)

    @Test
    fun downlinkRunsOnWorkerAndForwardsTwentyMsRealtimeFrames() {
        val downlink = BlockingPipe()
        val lease = FakeLease(downlink.input, ByteArrayOutputStream())
        val transport = FakeTransport()
        val terminal = AtomicReference<Throwable?>()
        val pump = CallRealtimeAudioPump(
            CallRealtimePcmBridge(lease),
            transport,
            { 123_456L },
        ) { terminal.compareAndSet(null, it) }

        pump.start()
        downlink.write(pattern(TelephonyPcmStreamFramer.FRAME_BYTES_20_MS))

        assertTrue(transport.sentLatch.await(1, TimeUnit.SECONDS))
        val sent = transport.sentFrames.single()
        assertEquals(realtimeFormat, sent.format)
        assertEquals(960, sent.data.size)
        assertEquals(123_456L, sent.monotonicTimestampNs)

        pump.close()
        downlink.close()
        assertNull(terminal.get())
    }

    @Test
    fun realtimeOutputIsWrittenOffTheCallbackThreadInTwentyMsChunks() {
        val downlink = BlockingPipe()
        val output = ThreadRecordingOutputStream()
        val lease = FakeLease(downlink.input, output)
        val transport = FakeTransport()
        val pump = CallRealtimeAudioPump(CallRealtimePcmBridge(lease), transport) { }
        val callbackThread = Thread.currentThread().name

        pump.start()
        transport.emitAudio(PcmFrame(realtimeFormat, pattern(960 * 2), 10L))

        assertTrue(output.twoWrites.await(1, TimeUnit.SECONDS))
        assertEquals(2, output.writeCount.get())
        assertEquals(640 * 2, output.bytes.size())
        assertTrue(output.threadNames.all { it.startsWith("call-realtime-tx") })
        assertTrue(output.threadNames.none { it == callbackThread })

        pump.close()
        downlink.close()
    }

    @Test
    fun outputBacklogOverFiveHundredMsFailsClosedOnce() {
        val downlink = BlockingPipe()
        val output = BlockingOutputStream()
        val lease = FakeLease(downlink.input, output)
        val transport = FakeTransport()
        val terminalCount = AtomicInteger()
        val terminal = AtomicReference<Throwable?>()
        val terminalLatch = CountDownLatch(1)
        val pump = CallRealtimeAudioPump(CallRealtimePcmBridge(lease), transport) {
            terminal.set(it)
            terminalCount.incrementAndGet()
            terminalLatch.countDown()
        }

        pump.start()
        transport.emitAudio(PcmFrame(realtimeFormat, pattern(960), 1L))
        assertTrue(output.writeEntered.await(1, TimeUnit.SECONDS))

        repeat(26) {
            transport.emitAudio(PcmFrame(realtimeFormat, pattern(960), 2L + it))
        }

        assertTrue(terminalLatch.await(1, TimeUnit.SECONDS))
        assertEquals(1, terminalCount.get())
        assertTrue(terminal.get()!!.message!!.contains("backlog", ignoreCase = true))
        assertFalse(pump.snapshot().running)
        assertEquals(0L, pump.snapshot().queuedOutputBytes)

        output.release()
        pump.close()
        downlink.close()
    }

    @Test
    fun remoteSpeechStartClearsBufferedAssistantAudioAndCancelsResponse() {
        val downlink = BlockingPipe()
        val output = BlockingOutputStream()
        val lease = FakeLease(downlink.input, output)
        val transport = FakeTransport()
        val pump = CallRealtimeAudioPump(CallRealtimePcmBridge(lease), transport) { }

        pump.start()
        transport.emitAudio(PcmFrame(realtimeFormat, pattern(960), 1L))
        assertTrue(output.writeEntered.await(1, TimeUnit.SECONDS))
        repeat(5) {
            transport.emitAudio(PcmFrame(realtimeFormat, pattern(960), 10L + it))
        }
        assertTrue(pump.snapshot().queuedOutputBytes > 0L)

        transport.emitRemoteSpeechStarted()

        assertEquals(1, transport.cancelCalls.get())
        assertEquals(0L, pump.snapshot().queuedOutputBytes)
        output.release()
        assertTrue(output.firstWriteDone.await(1, TimeUnit.SECONDS))
        Thread.sleep(50L)
        assertEquals(1, output.writeCount.get())

        pump.close()
        downlink.close()
    }

    @Test
    fun realtimeSendFailureBecomesSingleTerminalSignal() {
        val downlink = BlockingPipe()
        val lease = FakeLease(downlink.input, ByteArrayOutputStream())
        val transport = FakeTransport().apply {
            sendFailure = IOException("socket lost")
        }
        val terminal = AtomicReference<Throwable?>()
        val terminalLatch = CountDownLatch(1)
        val pump = CallRealtimeAudioPump(CallRealtimePcmBridge(lease), transport) {
            terminal.compareAndSet(null, it)
            terminalLatch.countDown()
        }

        pump.start()
        downlink.write(pattern(TelephonyPcmStreamFramer.FRAME_BYTES_20_MS))

        assertTrue(terminalLatch.await(1, TimeUnit.SECONDS))
        assertEquals("socket lost", terminal.get()!!.message)
        assertFalse(pump.snapshot().running)

        pump.close()
        downlink.close()
    }

    @Test
    fun closeIsIdempotentAndDoesNotOwnLeaseOrTransport() {
        val downlink = BlockingPipe()
        val lease = FakeLease(downlink.input, ByteArrayOutputStream())
        val transport = FakeTransport()
        val pump = CallRealtimeAudioPump(CallRealtimePcmBridge(lease), transport) { }

        pump.start()
        pump.close()
        pump.close()
        downlink.close()

        assertEquals(0, lease.closeCalls.get())
        assertEquals(0, transport.closeCalls.get())
        assertFalse(pump.snapshot().running)
    }

    private class FakeTransport : RealtimeTransport {
        private var listener: RealtimeTransport.Listener? = null
        val sentFrames = mutableListOf<PcmFrame>()
        val sentLatch = CountDownLatch(1)
        val cancelCalls = AtomicInteger()
        val closeCalls = AtomicInteger()
        var sendFailure: Throwable? = null

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> = Result.success(Unit)

        override fun sendAudio(frame: PcmFrame): Result<Unit> {
            val failure = sendFailure
            if (failure != null) {
                return Result.failure(failure)
            }
            synchronized(sentFrames) {
                sentFrames += frame
            }
            sentLatch.countDown()
            return Result.success(Unit)
        }

        override fun cancelResponse(): Result<Unit> {
            cancelCalls.incrementAndGet()
            return Result.success(Unit)
        }

        override fun close() {
            closeCalls.incrementAndGet()
        }

        override fun setListener(listener: RealtimeTransport.Listener?) {
            this.listener = listener
        }

        fun emitAudio(frame: PcmFrame) {
            listener?.onAudio(frame)
        }

        fun emitRemoteSpeechStarted() {
            listener?.onRemoteSpeechStarted()
        }
    }

    private class FakeLease(
        private val input: InputStream,
        private val output: OutputStream,
    ) : CallMediaEndpointLease {
        val closeCalls = AtomicInteger()

        override fun downlink(): InputStream = input
        override fun uplink(): OutputStream = output
        override fun close() {
            closeCalls.incrementAndGet()
        }
    }

    private class BlockingPipe {
        private val source = PipedOutputStream()
        val input = PipedInputStream(source, 4096)

        fun write(bytes: ByteArray) {
            source.write(bytes)
            source.flush()
        }

        fun close() {
            source.close()
            input.close()
        }
    }

    private class ThreadRecordingOutputStream : OutputStream() {
        val bytes = ByteArrayOutputStream()
        val writeCount = AtomicInteger()
        val threadNames = mutableListOf<String>()
        val twoWrites = CountDownLatch(2)

        @Synchronized
        override fun write(b: ByteArray, off: Int, len: Int) {
            bytes.write(b, off, len)
            threadNames += Thread.currentThread().name
            writeCount.incrementAndGet()
            twoWrites.countDown()
        }

        override fun write(b: Int) {
            error("single-byte writes are not expected")
        }
    }

    private class BlockingOutputStream : OutputStream() {
        val writeEntered = CountDownLatch(1)
        val firstWriteDone = CountDownLatch(1)
        val writeCount = AtomicInteger()
        private val releaseWrite = CountDownLatch(1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            writeEntered.countDown()
            try {
                if (!releaseWrite.await(2, TimeUnit.SECONDS)) {
                    throw IOException("test write release timeout")
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("test write interrupted")
            }
            writeCount.incrementAndGet()
            firstWriteDone.countDown()
        }

        override fun write(b: Int) {
            error("single-byte writes are not expected")
        }

        fun release() {
            releaseWrite.countDown()
        }
    }

    private fun pattern(size: Int): ByteArray = ByteArray(size) { (it and 0x7f).toByte() }
}
