package pl.michalmatu.aicallbridge.session

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

class CallRealtimeMediaSessionTest {
    @Test
    fun manualTakeoverCleansLocalMediaBeforeRealtimeControl() {
        val order = CopyOnWriteArrayList<String>()
        val fixture = Fixture(order)
        val session = CallRealtimeMediaSession(
            fixture.coordinator,
            fixture.generation,
            fixture.transport,
        )
        session.start()

        session.takeOverNow()

        assertEquals(CallMediaSessionState.IDLE, fixture.coordinator.snapshot().state())
        assertEquals(CallRealtimeMediaSessionState.TAKEN_OVER, session.snapshot().state)
        assertEquals(1, fixture.backend.abortCalls.get())
        assertEquals(1, fixture.backend.unbindCalls.get())
        assertEquals(1, fixture.transport.cancelCalls.get())
        assertEquals(1, fixture.transport.closeCalls.get())
        assertTrue(order.indexOf("lease-close") < order.indexOf("realtime-cancel"))
        assertTrue(order.indexOf("abort") < order.indexOf("realtime-cancel"))
        assertTrue(order.indexOf("realtime-cancel") < order.indexOf("realtime-close"))
    }

    @Test
    fun pumpFailureTriggersWholeGenerationCleanupBeforeTransportClose() {
        val order = CopyOnWriteArrayList<String>()
        val fixture = Fixture(order)
        fixture.transport.sendFailure = IOException("socket lost")
        val session = CallRealtimeMediaSession(
            fixture.coordinator,
            fixture.generation,
            fixture.transport,
        )
        session.start()

        fixture.endpoint.feed(pattern(TelephonyPcmStreamFramer.FRAME_BYTES_20_MS))

        assertTrue(fixture.endpoint.closed.await(1, TimeUnit.SECONDS))
        assertTrue(fixture.transport.closed.await(1, TimeUnit.SECONDS))
        assertEquals(CallRealtimeMediaSessionState.FAILED, session.snapshot().state)
        assertTrue(session.snapshot().failureReason!!.contains("socket lost"))
        assertEquals(CallMediaSessionState.IDLE, fixture.coordinator.snapshot().state())
        assertTrue(order.indexOf("lease-close") < order.indexOf("realtime-close"))
    }

    @Test
    fun staleGenerationCannotAttachToNewActiveMedia() {
        val order = CopyOnWriteArrayList<String>()
        val backend = FakeBackend(FakeEndpoint(order), order)
        val coordinator = newCoordinator(backend)
        val first = coordinator.start(16_000)
        coordinator.takeOverNow()
        val secondEndpoint = FakeEndpoint(order)
        backend.endpoint = secondEndpoint
        val second = coordinator.start(16_000)
        assertTrue(first != second)

        assertThrows(IllegalStateException::class.java) {
            CallRealtimeMediaSession(coordinator, first, FakeTransport(order))
        }

        coordinator.takeOverNow()
    }

    @Test
    fun closeIsIdempotentSafetyTakeover() {
        val order = CopyOnWriteArrayList<String>()
        val fixture = Fixture(order)
        val session = CallRealtimeMediaSession(
            fixture.coordinator,
            fixture.generation,
            fixture.transport,
        )
        session.start()

        session.close()
        session.close()

        assertEquals(CallRealtimeMediaSessionState.TAKEN_OVER, session.snapshot().state)
        assertEquals(1, fixture.backend.abortCalls.get())
        assertEquals(1, fixture.backend.unbindCalls.get())
        assertEquals(1, fixture.transport.cancelCalls.get())
        assertEquals(1, fixture.transport.closeCalls.get())
    }

    private class Fixture(val order: CopyOnWriteArrayList<String>) {
        val endpoint = FakeEndpoint(order)
        val backend = FakeBackend(endpoint, order)
        val coordinator = newCoordinator(backend)
        val generation = coordinator.start(16_000)
        val transport = FakeTransport(order)
    }

    private class FakeBackend(
        var endpoint: FakeEndpoint,
        private val order: CopyOnWriteArrayList<String>,
    ) : CallMediaSessionBackend {
        val abortCalls = AtomicInteger()
        val unbindCalls = AtomicInteger()

        override fun bind(generation: Long, callback: CallMediaSessionBackend.BindCallback) {
            callback.onBound(generation)
        }

        override fun prepare(generation: Long, sampleRateHz: Int) = Unit

        override fun start(generation: Long): CallMediaEndpointLease = endpoint

        override fun heartbeat(generation: Long): Boolean = true

        override fun abort(generation: Long) {
            abortCalls.incrementAndGet()
            order += "abort"
        }

        override fun unbind(generation: Long) {
            unbindCalls.incrementAndGet()
            order += "unbind"
        }
    }

    private class FakeEndpoint(
        private val order: CopyOnWriteArrayList<String>,
    ) : CallMediaEndpointLease {
        private val source = PipedOutputStream()
        private val input = PipedInputStream(source, 4096)
        private val output = ByteArrayOutputStream()
        private val closeOnce = AtomicInteger()
        val closed = CountDownLatch(1)

        override fun downlink(): InputStream = input
        override fun uplink(): OutputStream = output

        override fun close() {
            if (closeOnce.getAndIncrement() != 0) return
            order += "lease-close"
            source.close()
            input.close()
            output.close()
            closed.countDown()
        }

        fun feed(bytes: ByteArray) {
            source.write(bytes)
            source.flush()
        }
    }

    private class FakeTransport(
        private val order: CopyOnWriteArrayList<String>,
    ) : RealtimeTransport {
        private var listener: RealtimeTransport.Listener? = null
        val cancelCalls = AtomicInteger()
        val closeCalls = AtomicInteger()
        val closed = CountDownLatch(1)
        var sendFailure: Throwable? = null

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> = Result.success(Unit)

        override fun sendAudio(frame: PcmFrame): Result<Unit> =
            sendFailure?.let { Result.failure(it) } ?: Result.success(Unit)

        override fun cancelResponse(): Result<Unit> {
            cancelCalls.incrementAndGet()
            order += "realtime-cancel"
            return Result.success(Unit)
        }

        override fun close() {
            closeCalls.incrementAndGet()
            order += "realtime-close"
            closed.countDown()
        }

        override fun setListener(listener: RealtimeTransport.Listener?) {
            this.listener = listener
        }
    }

    private class NoopHeartbeatScheduler : CallMediaHeartbeatScheduler {
        override fun start(generation: Long, heartbeat: Runnable): AutoCloseable = AutoCloseable { }
    }

    companion object {
        private fun newCoordinator(backend: FakeBackend): CallMediaSessionCoordinator =
            CallMediaSessionCoordinator(
                backend,
                NoopHeartbeatScheduler(),
                { 1L },
                { },
            )

        private fun pattern(size: Int): ByteArray = ByteArray(size) { (it and 0x7f).toByte() }
    }
}
