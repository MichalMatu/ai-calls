package pl.michalmatu.aicallbridge.session

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeClientSecret
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialProvider
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

class CallRealtimeSessionOrchestratorTest {
    @Test
    fun connectsRealtimeBeforeStartingPrivilegedMediaAndBecomesActive() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val fixture = Fixture(events = events)
        val snapshots = mutableListOf<CallRealtimeSessionOrchestratorSnapshot>()
        val orchestrator = fixture.orchestrator { snapshots += it }

        orchestrator.start(request())

        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, orchestrator.snapshot().state)
        assertEquals(
            listOf("credential", "realtime_connect", "media_bind", "media_prepare", "media_start"),
            events.take(5),
        )
        assertTrue(snapshots.any { it.state == CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL })
        assertTrue(snapshots.any { it.state == CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME })
        assertTrue(snapshots.any { it.state == CallRealtimeSessionOrchestratorState.STARTING_MEDIA })
        assertTrue(snapshots.any { it.state == CallRealtimeSessionOrchestratorState.ACTIVE })

        orchestrator.takeOverNow()
        fixture.endpoint.closePipe()
    }

    @Test
    fun credentialFailureNeverStartsPrivilegedMedia() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val fixture = Fixture(
            events = events,
            credentialProvider = ImmediateCredentialProvider(Result.failure(IOException("credential down"))),
        )
        val orchestrator = fixture.orchestrator()

        orchestrator.start(request())

        assertEquals(CallRealtimeSessionOrchestratorState.FAILED, orchestrator.snapshot().state)
        assertTrue(orchestrator.snapshot().failureReason!!.contains("credential down"))
        assertFalse(events.contains("media_bind"))
        assertEquals(1, fixture.transport.closeCalls.get())
        fixture.endpoint.closePipe()
    }

    @Test
    fun realtimeConnectFailureNeverStartsPrivilegedMedia() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val transport = FakeTransport(events).apply {
            connectResult = Result.failure(IOException("realtime unavailable"))
        }
        val fixture = Fixture(events = events, transport = transport)
        val orchestrator = fixture.orchestrator()

        orchestrator.start(request())

        assertEquals(CallRealtimeSessionOrchestratorState.FAILED, orchestrator.snapshot().state)
        assertTrue(orchestrator.snapshot().failureReason!!.contains("realtime unavailable"))
        assertFalse(events.contains("media_bind"))
        fixture.endpoint.closePipe()
    }

    @Test
    fun takeoverDuringCredentialFetchInvalidatesLateCredential() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val credential = DeferredCredentialProvider(events)
        val fixture = Fixture(events = events, credentialProvider = credential)
        val orchestrator = fixture.orchestrator()

        val generation = orchestrator.start(request())
        assertEquals(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL, orchestrator.snapshot().state)

        orchestrator.takeOverNow()
        assertEquals(CallRealtimeSessionOrchestratorState.TAKEN_OVER, orchestrator.snapshot().state)
        credential.resume(Result.success(RealtimeClientSecret("eph_late", 9_999_999_999L)))

        assertEquals(generation, orchestrator.snapshot().generation)
        assertEquals(CallRealtimeSessionOrchestratorState.TAKEN_OVER, orchestrator.snapshot().state)
        assertEquals(0, fixture.transport.connectCalls.get())
        assertFalse(events.contains("media_bind"))
        fixture.endpoint.closePipe()
    }

    @Test
    fun takeoverDuringRealtimeConnectInvalidatesLateOpen() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val transport = DeferredConnectTransport(events)
        val fixture = Fixture(events = events, transport = transport)
        val orchestrator = fixture.orchestrator()

        orchestrator.start(request())
        assertEquals(CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME, orchestrator.snapshot().state)

        orchestrator.takeOverNow()
        transport.resumeConnect(Result.success(Unit))

        assertEquals(CallRealtimeSessionOrchestratorState.TAKEN_OVER, orchestrator.snapshot().state)
        assertFalse(events.contains("media_bind"))
        assertTrue(transport.closeCalls.get() >= 1)
        fixture.endpoint.closePipe()
    }

    @Test
    fun activeTakeoverStopsLocalMediaBeforeRealtimeCleanup() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val fixture = Fixture(events = events)
        val orchestrator = fixture.orchestrator()
        orchestrator.start(request())
        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, orchestrator.snapshot().state)

        orchestrator.takeOverNow()

        assertEquals(CallRealtimeSessionOrchestratorState.TAKEN_OVER, orchestrator.snapshot().state)
        val abortIndex = events.indexOf("media_abort")
        val cancelIndex = events.indexOf("realtime_cancel")
        val closeIndex = events.indexOf("realtime_close")
        assertTrue(abortIndex >= 0)
        assertTrue(cancelIndex > abortIndex)
        assertTrue(closeIndex > abortIndex)
        fixture.endpoint.closePipe()
    }

    @Test
    fun closeOnActiveSessionUsesLocalTakeoverOrderingBeforeRealtimeCleanup() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val fixture = Fixture(events = events)
        val orchestrator = fixture.orchestrator()
        orchestrator.start(request())
        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, orchestrator.snapshot().state)

        orchestrator.close()

        assertEquals(CallRealtimeSessionOrchestratorState.TAKEN_OVER, orchestrator.snapshot().state)
        val abortIndex = events.indexOf("media_abort")
        val cancelIndex = events.indexOf("realtime_cancel")
        val closeIndex = events.indexOf("realtime_close")
        assertTrue(abortIndex >= 0)
        assertTrue(cancelIndex > abortIndex)
        assertTrue(closeIndex > abortIndex)
        fixture.endpoint.closePipe()
    }

    @Test
    fun closeDuringCredentialFetchInvalidatesLateCredentialLikeTakeover() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val credential = DeferredCredentialProvider(events)
        val fixture = Fixture(events = events, credentialProvider = credential)
        val orchestrator = fixture.orchestrator()

        orchestrator.start(request())
        assertEquals(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL, orchestrator.snapshot().state)

        orchestrator.close()
        assertEquals(CallRealtimeSessionOrchestratorState.TAKEN_OVER, orchestrator.snapshot().state)
        credential.resume(Result.success(RealtimeClientSecret("eph_late_close", 9_999_999_999L)))

        assertEquals(CallRealtimeSessionOrchestratorState.TAKEN_OVER, orchestrator.snapshot().state)
        assertEquals(0, fixture.transport.connectCalls.get())
        assertFalse(events.contains("media_bind"))
        assertTrue(fixture.transport.closeCalls.get() >= 1)
        fixture.endpoint.closePipe()
    }

    @Test
    fun dataPlaneFailureBecomesOrchestratorFailureAndCleansMedia() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val transport = FakeTransport(events).apply {
            sendFailure = IOException("socket lost")
        }
        val fixture = Fixture(events = events, transport = transport)
        val orchestrator = fixture.orchestrator()
        orchestrator.start(request())
        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, orchestrator.snapshot().state)

        fixture.endpoint.feed(ByteArray(TelephonyPcmStreamFramer.FRAME_BYTES_20_MS))
        waitUntil { orchestrator.snapshot().state == CallRealtimeSessionOrchestratorState.FAILED }

        assertTrue(orchestrator.snapshot().failureReason!!.contains("socket lost"))
        assertTrue(events.contains("media_abort"))
        assertTrue(events.contains("realtime_close"))
        fixture.endpoint.closePipe()
    }

    @Test
    fun coordinatorStartFailureFailsClosedAndClosesRealtime() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val fixture = Fixture(events = events, failMediaStart = true)
        val orchestrator = fixture.orchestrator()

        orchestrator.start(request())

        assertEquals(CallRealtimeSessionOrchestratorState.FAILED, orchestrator.snapshot().state)
        assertTrue(orchestrator.snapshot().failureReason!!.contains("media start failed"))
        assertTrue(events.contains("media_abort"))
        assertTrue(events.contains("realtime_close"))
        fixture.endpoint.closePipe()
    }

    private class Fixture(
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf()),
        credentialProvider: RealtimeCredentialProvider = ImmediateCredentialProvider(
            Result.success(RealtimeClientSecret("eph_test", 9_999_999_999L)),
            events,
        ),
        val transport: FakeTransportBase = FakeTransport(events),
        failMediaStart: Boolean = false,
    ) {
        val endpoint = FakeEndpoint()
        private val backend = FakeBackend(endpoint, events, failMediaStart)
        private val coordinator = CallMediaSessionCoordinator(
            backend,
            NoopHeartbeatScheduler(),
            { 1L },
            { },
        )
        private val credentialProvider = credentialProvider

        fun orchestrator(
            listener: (CallRealtimeSessionOrchestratorSnapshot) -> Unit = {},
        ) = CallRealtimeSessionOrchestrator(
            coordinator = coordinator,
            credentialProvider = credentialProvider,
            transportFactory = { transport },
            bootstrapExecutor = Executor { it.run() },
            listener = listener,
        )
    }

    private class FakeBackend(
        private val endpoint: FakeEndpoint,
        private val events: MutableList<String>,
        private val failStart: Boolean,
    ) : CallMediaSessionBackend {
        override fun bind(generation: Long, callback: CallMediaSessionBackend.BindCallback) {
            events += "media_bind"
            callback.onBound(generation)
        }

        override fun prepare(generation: Long, sampleRateHz: Int) {
            events += "media_prepare"
        }

        override fun start(generation: Long): CallMediaEndpointLease {
            events += "media_start"
            if (failStart) throw IOException("media start failed")
            return endpoint
        }

        override fun heartbeat(generation: Long): Boolean = true

        override fun abort(generation: Long) {
            events += "media_abort"
        }

        override fun unbind(generation: Long) {
            events += "media_unbind"
        }
    }

    private class FakeEndpoint : CallMediaEndpointLease {
        private val source = PipedOutputStream()
        private val input = PipedInputStream(source, 4096)
        private val output = ByteArrayOutputStream()

        override fun downlink(): InputStream = input
        override fun uplink(): OutputStream = output
        override fun close() {
            try { source.close() } catch (_: Throwable) {}
            try { input.close() } catch (_: Throwable) {}
            try { output.close() } catch (_: Throwable) {}
        }

        fun feed(data: ByteArray) {
            source.write(data)
            source.flush()
        }

        fun closePipe() = close()
    }

    private class NoopHeartbeatScheduler : CallMediaHeartbeatScheduler {
        override fun start(generation: Long, heartbeat: Runnable): AutoCloseable = AutoCloseable { }
    }

    private class ImmediateCredentialProvider(
        private val result: Result<RealtimeClientSecret>,
        private val events: MutableList<String>? = null,
    ) : RealtimeCredentialProvider {
        override suspend fun fetchClientSecret(): Result<RealtimeClientSecret> {
            events?.add("credential")
            return result
        }
    }

    private class DeferredCredentialProvider(
        private val events: MutableList<String>,
    ) : RealtimeCredentialProvider {
        private var continuation: Continuation<Result<RealtimeClientSecret>>? = null

        override suspend fun fetchClientSecret(): Result<RealtimeClientSecret> =
            suspendCoroutine { continuation ->
                events += "credential"
                this.continuation = continuation
            }

        fun resume(result: Result<RealtimeClientSecret>) {
            continuation!!.resume(result)
        }
    }

    private abstract class FakeTransportBase(
        protected val events: MutableList<String>,
    ) : RealtimeTransport {
        val connectCalls = AtomicInteger()
        val closeCalls = AtomicInteger()
        override fun cancelResponse(): Result<Unit> {
            events += "realtime_cancel"
            return Result.success(Unit)
        }
        override fun close() {
            closeCalls.incrementAndGet()
            events += "realtime_close"
        }
        override fun setListener(listener: RealtimeTransport.Listener?) = Unit
    }

    private class FakeTransport(events: MutableList<String>) : FakeTransportBase(events) {
        var connectResult: Result<Unit> = Result.success(Unit)
        var sendFailure: Throwable? = null

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> {
            connectCalls.incrementAndGet()
            events += "realtime_connect"
            return connectResult
        }

        override fun sendAudio(frame: PcmFrame): Result<Unit> =
            sendFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    private class DeferredConnectTransport(events: MutableList<String>) : FakeTransportBase(events) {
        private var continuation: Continuation<Result<Unit>>? = null

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> =
            suspendCoroutine { continuation ->
                connectCalls.incrementAndGet()
                events += "realtime_connect"
                this.continuation = continuation
            }

        override fun sendAudio(frame: PcmFrame): Result<Unit> = Result.success(Unit)

        fun resumeConnect(result: Result<Unit>) {
            continuation!!.resume(result)
        }
    }

    companion object {
        private fun request() = CallRealtimeSessionRequest(
            sessionEndpoint = "wss://api.openai.com/v1/realtime",
            model = "gpt-realtime-2.1",
            instructions = "Stay within explicit user authority.",
            sampleRateHz = 16_000,
        )

        private fun waitUntil(predicate: () -> Boolean) {
            val deadline = System.nanoTime() + 1_000_000_000L
            while (!predicate()) {
                if (System.nanoTime() >= deadline) error("condition timeout")
                Thread.sleep(5L)
            }
        }
    }
}
