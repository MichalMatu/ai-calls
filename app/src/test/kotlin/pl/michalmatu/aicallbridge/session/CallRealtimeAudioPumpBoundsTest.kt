package pl.michalmatu.aicallbridge.session

import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFormat
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

class CallRealtimeAudioPumpBoundsTest {
    private val realtimeFormat = PcmFormat(24_000, 1, 16)

    @Test
    fun tinyDeltasCannotCreateUnboundedObjectBacklog() {
        val source = PipedOutputStream()
        val input = PipedInputStream(source, 4096)
        val output = BlockingOutputStream()
        val lease = FakeLease(input, output)
        val transport = FakeTransport()
        val terminal = AtomicReference<Throwable?>()
        val terminalLatch = CountDownLatch(1)
        val pump = CallRealtimeAudioPump(CallRealtimePcmBridge(lease), transport) {
            terminal.compareAndSet(null, it)
            terminalLatch.countDown()
        }

        pump.start()
        transport.emitAudio(PcmFrame(realtimeFormat, byteArrayOf(1, 0), 1L))
        assertTrue(output.writeEntered.await(1, TimeUnit.SECONDS))

        repeat(65) {
            transport.emitAudio(PcmFrame(realtimeFormat, byteArrayOf(1, 0), 2L + it))
        }

        assertTrue("tiny-frame backlog was not bounded", terminalLatch.await(1, TimeUnit.SECONDS))
        assertTrue(terminal.get()!!.message!!.contains("backlog", ignoreCase = true))

        output.release()
        pump.close()
        source.close()
        input.close()
    }

    private class FakeTransport : RealtimeTransport {
        private var listener: RealtimeTransport.Listener? = null

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> = Result.success(Unit)
        override fun sendAudio(frame: PcmFrame): Result<Unit> = Result.success(Unit)
        override fun cancelResponse(): Result<Unit> = Result.success(Unit)
        override fun close() = Unit
        override fun setListener(listener: RealtimeTransport.Listener?) {
            this.listener = listener
        }

        fun emitAudio(frame: PcmFrame) {
            listener?.onAudio(frame)
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

    private class BlockingOutputStream : OutputStream() {
        val writeEntered = CountDownLatch(1)
        private val releaseWrite = CountDownLatch(1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            writeEntered.countDown()
            try {
                releaseWrite.await(2, TimeUnit.SECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("test write interrupted")
            }
        }

        override fun write(b: Int) {
            error("single-byte writes are not expected")
        }

        fun release() {
            releaseWrite.countDown()
        }
    }
}
