package pl.michalmatu.aicallbridge.session

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeClientSecret
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialProvider
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionTool
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

class CallRealtimeFunctionRoutingTest {
    @Test
    fun requestToolsReachRealtimeAndFunctionCallGetsOneShotResponder() {
        val transport = FakeTransport()
        var capturedCall: RealtimeFunctionCall? = null
        var capturedResponder: CallRealtimeFunctionResponder? = null
        val fixture = Fixture(
            transports = ArrayDeque<FakeTransport>().apply { add(transport) },
            functionCallHandler = CallRealtimeFunctionCallHandler { call, responder ->
                capturedCall = call
                capturedResponder = responder
            },
        )
        val orchestrator = fixture.orchestrator()
        val tool = proposalTool()

        orchestrator.start(request(tools = listOf(tool)))

        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, orchestrator.snapshot().state)
        assertEquals(listOf(tool), transport.connectedConfig!!.tools)

        transport.emitFunctionCall(
            RealtimeFunctionCall("call_1", "evaluate_proposal", "{\"provider\":\"Clinic A\"}"),
        )

        assertEquals("call_1", capturedCall!!.callId)
        assertEquals("evaluate_proposal", capturedCall!!.name)
        assertTrue(capturedResponder!!.submit("{\"decision\":\"allowed\"}").isSuccess)
        assertEquals(
            listOf("call_1" to "{\"decision\":\"allowed\"}"),
            transport.functionOutputs,
        )

        assertTrue(capturedResponder!!.submit("{\"decision\":\"allowed_again\"}").isFailure)
        assertEquals(1, transport.functionOutputs.size)

        orchestrator.takeOverNow()
        fixture.closeAll()
    }

    @Test
    fun responderFromTakenOverGenerationCannotWriteToNewTransport() {
        val firstTransport = FakeTransport()
        val secondTransport = FakeTransport()
        val responders = mutableListOf<CallRealtimeFunctionResponder>()
        val fixture = Fixture(
            transports = ArrayDeque<FakeTransport>().apply {
                add(firstTransport)
                add(secondTransport)
            },
            functionCallHandler = CallRealtimeFunctionCallHandler { _, responder ->
                responders += responder
            },
        )
        val orchestrator = fixture.orchestrator()

        val firstGeneration = orchestrator.start(request(tools = listOf(proposalTool())))
        firstTransport.emitFunctionCall(
            RealtimeFunctionCall("old_call", "evaluate_proposal", "{}"),
        )
        assertEquals(1, responders.size)
        val oldResponder = responders.single()

        orchestrator.takeOverNow()
        val secondGeneration = orchestrator.start(request(tools = listOf(proposalTool())))
        assertTrue(secondGeneration > firstGeneration)
        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, orchestrator.snapshot().state)

        assertTrue(oldResponder.submit("{\"decision\":\"stale\"}").isFailure)
        assertTrue(firstTransport.functionOutputs.isEmpty())
        assertTrue(secondTransport.functionOutputs.isEmpty())

        secondTransport.emitFunctionCall(
            RealtimeFunctionCall("new_call", "evaluate_proposal", "{}"),
        )
        assertEquals(2, responders.size)
        assertTrue(responders[1].submit("{\"decision\":\"allowed\"}").isSuccess)
        assertEquals(
            listOf("new_call" to "{\"decision\":\"allowed\"}"),
            secondTransport.functionOutputs,
        )

        orchestrator.takeOverNow()
        fixture.closeAll()
    }

    @Test
    fun functionHandlerFailureFailsClosedAndCleansPrivilegedMedia() {
        val transport = FakeTransport()
        val fixture = Fixture(
            transports = ArrayDeque<FakeTransport>().apply { add(transport) },
            functionCallHandler = CallRealtimeFunctionCallHandler { _, _ ->
                throw IllegalStateException("policy handler crashed")
            },
        )
        val orchestrator = fixture.orchestrator()
        orchestrator.start(request(tools = listOf(proposalTool())))
        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, orchestrator.snapshot().state)

        transport.emitFunctionCall(
            RealtimeFunctionCall("call_crash", "evaluate_proposal", "{}"),
        )

        assertEquals(CallRealtimeSessionOrchestratorState.FAILED, orchestrator.snapshot().state)
        assertTrue(orchestrator.snapshot().failureReason!!.contains("policy handler crashed"))
        assertTrue(fixture.backend.abortCalls.get() >= 1)
        assertTrue(transport.closeCalls.get() >= 1)
        fixture.closeAll()
    }

    @Test
    fun functionOutputFailureFailsClosedInsteadOfLeavingModelWaiting() {
        val transport = FakeTransport().apply {
            functionOutputResult = Result.failure(IllegalStateException("function output rejected"))
        }
        var responder: CallRealtimeFunctionResponder? = null
        val fixture = Fixture(
            transports = ArrayDeque<FakeTransport>().apply { add(transport) },
            functionCallHandler = CallRealtimeFunctionCallHandler { _, value -> responder = value },
        )
        val orchestrator = fixture.orchestrator()
        orchestrator.start(request(tools = listOf(proposalTool())))

        transport.emitFunctionCall(
            RealtimeFunctionCall("call_reject", "evaluate_proposal", "{}"),
        )
        val result = responder!!.submit("{\"decision\":\"allowed\"}")

        assertTrue(result.isFailure)
        assertEquals(CallRealtimeSessionOrchestratorState.FAILED, orchestrator.snapshot().state)
        assertTrue(orchestrator.snapshot().failureReason!!.contains("function output rejected"))
        assertTrue(fixture.backend.abortCalls.get() >= 1)
        fixture.closeAll()
    }

    private class Fixture(
        private val transports: ArrayDeque<FakeTransport>,
        private val functionCallHandler: CallRealtimeFunctionCallHandler,
    ) {
        val backend = FreshEndpointBackend()
        private val coordinator = CallMediaSessionCoordinator(
            backend,
            NoopHeartbeatScheduler(),
            { 1L },
            { },
        )

        fun orchestrator() = CallRealtimeSessionOrchestrator(
            coordinator = coordinator,
            credentialProvider = ImmediateCredentialProvider(),
            transportFactory = { transports.removeFirst() },
            bootstrapExecutor = Executor { it.run() },
            functionCallHandler = functionCallHandler,
        )

        fun closeAll() = backend.closeAll()
    }

    private class ImmediateCredentialProvider : RealtimeCredentialProvider {
        override suspend fun fetchClientSecret(): Result<RealtimeClientSecret> =
            Result.success(RealtimeClientSecret("eph_function_route", 9_999_999_999L))
    }

    private class FakeTransport : RealtimeTransport {
        var connectedConfig: RealtimeSessionConfig? = null
        var listener: RealtimeTransport.Listener? = null
        var functionOutputResult: Result<Unit> = Result.success(Unit)
        val functionOutputs = mutableListOf<Pair<String, String>>()
        val closeCalls = AtomicInteger()

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> {
            connectedConfig = config
            return Result.success(Unit)
        }

        override fun sendAudio(frame: PcmFrame): Result<Unit> = Result.success(Unit)
        override fun cancelResponse(): Result<Unit> = Result.success(Unit)

        override fun submitFunctionOutput(callId: String, outputJson: String): Result<Unit> {
            val result = functionOutputResult
            if (result.isSuccess) functionOutputs += callId to outputJson
            return result
        }

        override fun close() {
            closeCalls.incrementAndGet()
        }

        override fun setListener(listener: RealtimeTransport.Listener?) {
            this.listener = listener
        }

        fun emitFunctionCall(call: RealtimeFunctionCall) {
            listener!!.onFunctionCall(call)
        }
    }

    private class FreshEndpointBackend : CallMediaSessionBackend {
        private val endpoints = mutableListOf<FakeEndpoint>()
        val abortCalls = AtomicInteger()

        override fun bind(generation: Long, callback: CallMediaSessionBackend.BindCallback) {
            callback.onBound(generation)
        }

        override fun prepare(generation: Long, sampleRateHz: Int) = Unit

        override fun start(generation: Long): CallMediaEndpointLease =
            FakeEndpoint().also { endpoints += it }

        override fun heartbeat(generation: Long): Boolean = true

        override fun abort(generation: Long) {
            abortCalls.incrementAndGet()
        }

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
        private fun proposalTool() = RealtimeFunctionTool(
            "evaluate_proposal",
            "Evaluate one concrete counterparty proposal using application-owned policy.",
            "{\"type\":\"object\",\"properties\":{}}",
        )

        private fun request(tools: List<RealtimeFunctionTool>) = CallRealtimeSessionRequest(
            sessionEndpoint = "wss://api.openai.com/v1/realtime",
            model = "gpt-realtime-2",
            instructions = "Stay within explicit user authority.",
            sampleRateHz = 16_000,
            tools = tools,
        )
    }
}
