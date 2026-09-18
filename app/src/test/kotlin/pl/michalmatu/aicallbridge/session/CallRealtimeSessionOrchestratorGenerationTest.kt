package pl.michalmatu.aicallbridge.session

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeClientSecret
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialProvider
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

class CallRealtimeSessionOrchestratorGenerationTest {
    @Test
    fun lateCredentialFromTakenOverGenerationCannotCloseNewTransport() {
        val credentialProvider = FirstDeferredThenImmediateCredentialProvider()
        val firstTransport = FakeTransport()
        val secondTransport = FakeTransport()
        val transports = ArrayDeque<RealtimeTransport>().apply {
            add(firstTransport)
            add(secondTransport)
        }
        val backend = FreshEndpointBackend()
        val coordinator = CallMediaSessionCoordinator(
            backend,
            NoopHeartbeatScheduler(),
            { 1L },
            { },
        )
        val orchestrator = CallRealtimeSessionOrchestrator(
            coordinator = coordinator,
            credentialProvider = credentialProvider,
            transportFactory = { transports.removeFirst() },
            bootstrapExecutor = Executor { it.run() },
        )

        val firstGeneration = orchestrator.start(request())
        assertEquals(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL, orchestrator.snapshot().state)
        orchestrator.takeOverNow()
        assertEquals(CallRealtimeSessionOrchestratorState.TAKEN_OVER, orchestrator.snapshot().state)

        val secondGeneration = orchestrator.start(request())
        assertTrue(secondGeneration > firstGeneration)
        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, orchestrator.snapshot().state)
        assertEquals(1, secondTransport.connectCalls.get())
        assertEquals(0, secondTransport.closeCalls.get())

        credentialProvider.resumeFirst(
            Result.success(RealtimeClientSecret("eph_old", 9_999_999_999L)),
        )

        assertEquals(secondGeneration, orchestrator.snapshot().generation)
        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, orchestrator.snapshot().state)
        assertEquals(0, secondTransport.closeCalls.get())
        assertTrue(firstTransport.closeCalls.get() >= 1)

        orchestrator.takeOverNow()
        backend.closeAll()
    }

    private class FirstDeferredThenImmediateCredentialProvider : RealtimeCredentialProvider {
        private var calls = 0
        private var firstContinuation: Continuation<Result<RealtimeClientSecret>>? = null

        override suspend fun fetchClientSecret(): Result<RealtimeClientSecret> {
            calls++
            if (calls == 1) {
                return suspendCoroutine { firstContinuation = it }
            }
            return Result.success(RealtimeClientSecret("eph_new", 9_999_999_999L))
        }

        fun resumeFirst(result: Result<RealtimeClientSecret>) {
            firstContinuation!!.resume(result)
        }
    }

    private class FakeTransport : RealtimeTransport {
        val connectCalls = AtomicInteger()
        val closeCalls = AtomicInteger()

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> {
            connectCalls.incrementAndGet()
            return Result.success(Unit)
        }

        override fun sendAudio(frame: PcmFrame): Result<Unit> = Result.success(Unit)
        override fun cancelResponse(): Result<Unit> = Result.success(Unit)
        override fun close() {
            closeCalls.incrementAndGet()
        }
        override fun setListener(listener: RealtimeTransport.Listener?) = Unit
    }

    private class FreshEndpointBackend : CallMediaSessionBackend {
        private val endpoints = mutableListOf<FakeEndpoint>()

        override fun bind(generation: Long, callback: CallMediaSessionBackend.BindCallback) {
            callback.onBound(generation)
        }

        override fun prepare(generation: Long, sampleRateHz: Int) = Unit

        override fun start(generation: Long): CallMediaEndpointLease =
            FakeEndpoint().also { endpoints += it }

        override fun heartbeat(generation: Long): Boolean = true
        override fun abort(generation: Long) = Unit
        override fun unbind(generation: Long) = Unit

        fun closeAll() {
            endpoints.forEach { it.close() }
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
    }

    private class NoopHeartbeatScheduler : CallMediaHeartbeatScheduler {
        override fun start(generation: Long, heartbeat: Runnable): AutoCloseable = AutoCloseable { }
    }

    companion object {
        private fun request() = CallRealtimeSessionRequest(
            sessionEndpoint = "wss://api.openai.com/v1/realtime",
            model = "gpt-realtime-2.1",
            instructions = "Stay within explicit user authority.",
        )
    }
}
