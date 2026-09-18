package pl.michalmatu.aicallbridge.session

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

class CallRealtimeMediaSessionListenerTest {
    @Test
    fun pumpFailurePublishesFailedTerminalStateOnce() {
        val fixture = Fixture(sendFailure = IOException("socket lost"))
        val terminal = AtomicReference<CallRealtimeMediaSessionSnapshot?>()
        val terminalCount = AtomicInteger()
        val terminalLatch = CountDownLatch(1)
        val session = CallRealtimeMediaSession(
            coordinator = fixture.coordinator,
            generation = fixture.generation,
            transport = fixture.transport,
            onTerminalState = { snapshot ->
                terminal.set(snapshot)
                terminalCount.incrementAndGet()
                terminalLatch.countDown()
            },
        )

        session.start()
        fixture.endpoint.feed(pattern(TelephonyPcmStreamFramer.FRAME_BYTES_20_MS))

        assertTrue(terminalLatch.await(1, TimeUnit.SECONDS))
        assertEquals(CallRealtimeMediaSessionState.FAILED, terminal.get()!!.state)
        assertTrue(terminal.get()!!.failureReason!!.contains("socket lost"))
        assertEquals(1, terminalCount.get())
    }

    @Test
    fun manualTakeoverPublishesTakenOverTerminalStateOnce() {
        val fixture = Fixture()
        val terminal = AtomicReference<CallRealtimeMediaSessionSnapshot?>()
        val terminalCount = AtomicInteger()
        val session = CallRealtimeMediaSession(
            coordinator = fixture.coordinator,
            generation = fixture.generation,
            transport = fixture.transport,
            onTerminalState = { snapshot ->
                terminal.set(snapshot)
                terminalCount.incrementAndGet()
            },
        )
        session.start()

        session.takeOverNow()
        session.takeOverNow()

        assertEquals(CallRealtimeMediaSessionState.TAKEN_OVER, terminal.get()!!.state)
        assertEquals(1, terminalCount.get())
    }

    private class Fixture(sendFailure: Throwable? = null) {
        val endpoint = FakeEndpoint()
        private val backend = FakeBackend(endpoint)
        val coordinator = CallMediaSessionCoordinator(
            backend,
            NoopHeartbeatScheduler(),
            { 1L },
            { },
        )
        val generation = coordinator.start(16_000)
        val transport = FakeTransport(sendFailure)
    }

    private class FakeBackend(
        private val endpoint: FakeEndpoint,
    ) : CallMediaSessionBackend {
        override fun bind(generation: Long, callback: CallMediaSessionBackend.BindCallback) {
            callback.onBound(generation)
        }

        override fun prepare(generation: Long, sampleRateHz: Int) = Unit
        override fun start(generation: Long): CallMediaEndpointLease = endpoint
        override fun heartbeat(generation: Long): Boolean = true
        override fun abort(generation: Long) = Unit
        override fun unbind(generation: Long) = Unit
    }

    private class FakeEndpoint : CallMediaEndpointLease {
        private val source = PipedOutputStream()
        private val input = PipedInputStream(source, 4096)
        private val output = ByteArrayOutputStream()

        override fun downlink(): InputStream = input
        override fun uplink(): OutputStream = output

        override fun close() {
            source.close()
            input.close()
            output.close()
        }

        fun feed(bytes: ByteArray) {
            source.write(bytes)
            source.flush()
        }
    }

    private class FakeTransport(
        private val sendFailure: Throwable?,
    ) : RealtimeTransport {
        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> = Result.success(Unit)
        override fun sendAudio(frame: PcmFrame): Result<Unit> =
            sendFailure?.let { Result.failure(it) } ?: Result.success(Unit)
        override fun cancelResponse(): Result<Unit> = Result.success(Unit)
        override fun close() = Unit
        override fun setListener(listener: RealtimeTransport.Listener?) = Unit
    }

    private class NoopHeartbeatScheduler : CallMediaHeartbeatScheduler {
        override fun start(generation: Long, heartbeat: Runnable): AutoCloseable = AutoCloseable { }
    }

    companion object {
        private fun pattern(size: Int): ByteArray = ByteArray(size) { (it and 0x7f).toByte() }
    }
}
