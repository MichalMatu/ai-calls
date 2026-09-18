package pl.michalmatu.aicallbridge.agent

import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeClientSecret
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialProvider
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionFollowup
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport
import pl.michalmatu.aicallbridge.session.CallMediaEndpointLease
import pl.michalmatu.aicallbridge.session.CallMediaHeartbeatScheduler
import pl.michalmatu.aicallbridge.session.CallMediaSessionBackend
import pl.michalmatu.aicallbridge.session.CallMediaSessionCoordinator
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorState

class CallRealtimeAgentSessionControllerTest {
    @Test
    fun controllerStartsBoundSpecRoutesCommitmentFlowAndRevokesPermitOnTakeover() {
        val workflow = activeWorkflow()
        val transport = FakeTransport()
        val backend = FakeBackend()
        val coordinator = CallMediaSessionCoordinator(
            backend,
            NoopHeartbeatScheduler(),
            { 1L },
            { },
        )
        val controller = CallRealtimeAgentSessionController.create(
            workflow = workflow,
            coordinator = coordinator,
            credentialProvider = ImmediateCredentialProvider(),
            transportFactory = { transport },
            bootstrapExecutor = Executor { it.run() },
            sessionEndpoint = "wss://api.openai.com/v1/realtime",
            model = "gpt-realtime",
        )

        controller.start()

        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, controller.snapshot().state)
        assertTrue(transport.connectedConfig!!.instructions.contains("evaluate_proposal"))
        assertEquals(
            listOf(
                CallRealtimeProposalFunctionHandler.FUNCTION_NAME,
                CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME,
            ),
            transport.connectedConfig!!.tools.map { it.name },
        )

        transport.emitFunctionCall(
            RealtimeFunctionCall(
                "call_1",
                CallRealtimeProposalFunctionHandler.FUNCTION_NAME,
                """{"scheduled_at":null,"price":null,"payment_mode":null,"provider":"Clinic A","location":"Wroclaw"}""",
            ),
        )

        val evaluationOutput = JsonParser.parseString(transport.functionOutputs.single().second).asJsonObject
        assertEquals("autonomously_allowed", evaluationOutput.get("decision").asString)
        assertEquals(
            RealtimeFunctionFollowup.ForceFunction(CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME),
            transport.functionFollowups.single(),
        )
        val authorization = evaluationOutput.get("commitment_authorization").asString
        assertTrue(authorization.isNotBlank())
        assertTrue(controller.sessionSpec.commitmentGate.hasAuthorization())

        transport.emitFunctionCall(
            RealtimeFunctionCall(
                "call_2",
                CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME,
                "{\"authorization\":\"$authorization\"}",
            ),
        )

        assertEquals(
            "authorized",
            JsonParser.parseString(transport.functionOutputs[1].second)
                .asJsonObject.get("commitment").asString,
        )
        assertEquals(RealtimeFunctionFollowup.NoTools, transport.functionFollowups[1])
        assertFalse(controller.sessionSpec.commitmentGate.hasAuthorization())
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())

        // A later approved proposal creates new authority, which TAKE OVER must revoke locally.
        transport.emitFunctionCall(
            RealtimeFunctionCall(
                "call_3",
                CallRealtimeProposalFunctionHandler.FUNCTION_NAME,
                """{"scheduled_at":null,"price":null,"payment_mode":null,"provider":"Clinic A","location":"Wroclaw"}""",
            ),
        )
        assertTrue(controller.sessionSpec.commitmentGate.hasAuthorization())

        controller.takeOverNow()

        assertEquals(CallRealtimeSessionOrchestratorState.TAKEN_OVER, controller.snapshot().state)
        assertFalse(controller.sessionSpec.commitmentGate.hasAuthorization())
        assertTrue(backend.abortCalls > 0)
        controller.close()
        backend.close()
    }

    private fun activeWorkflow(): CallWorkflow =
        CallWorkflow(
            CallTask(
                "dermatolog",
                "umow wizyte",
                "konsultacja",
                CallConstraints.unconstrained(),
                CallPreferences.none(),
                mapOf(),
            ),
            CallConfirmationPolicy(),
        ) { }.apply {
            resolveTarget(CallResolvedTarget("Clinic A", "510100100"))
            markDialing()
            markCallActive()
        }

    private class ImmediateCredentialProvider : RealtimeCredentialProvider {
        override suspend fun fetchClientSecret(): Result<RealtimeClientSecret> =
            Result.success(RealtimeClientSecret("ek_test_controller", 9_999_999_999L))
    }

    private class FakeTransport : RealtimeTransport {
        var connectedConfig: RealtimeSessionConfig? = null
        private var listener: RealtimeTransport.Listener? = null
        val functionOutputs = mutableListOf<Pair<String, String>>()
        val functionFollowups = mutableListOf<RealtimeFunctionFollowup>()

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> {
            connectedConfig = config
            return Result.success(Unit)
        }

        override fun sendAudio(frame: PcmFrame): Result<Unit> = Result.success(Unit)
        override fun cancelResponse(): Result<Unit> = Result.success(Unit)

        override fun submitFunctionOutput(callId: String, outputJson: String): Result<Unit> =
            submitFunctionOutput(callId, outputJson, RealtimeFunctionFollowup.Auto)

        override fun submitFunctionOutput(
            callId: String,
            outputJson: String,
            followup: RealtimeFunctionFollowup,
        ): Result<Unit> {
            functionOutputs += callId to outputJson
            functionFollowups += followup
            return Result.success(Unit)
        }

        override fun setListener(listener: RealtimeTransport.Listener?) {
            this.listener = listener
        }

        override fun close() = Unit

        fun emitFunctionCall(call: RealtimeFunctionCall) {
            listener!!.onFunctionCall(call)
        }
    }

    private class FakeBackend : CallMediaSessionBackend, AutoCloseable {
        private val endpoints = mutableListOf<FakeEndpoint>()
        var abortCalls = 0

        override fun bind(generation: Long, callback: CallMediaSessionBackend.BindCallback) {
            callback.onBound(generation)
        }

        override fun prepare(generation: Long, sampleRateHz: Int) = Unit

        override fun start(generation: Long): CallMediaEndpointLease =
            FakeEndpoint().also { endpoints += it }

        override fun heartbeat(generation: Long): Boolean = true

        override fun abort(generation: Long) {
            abortCalls++
        }

        override fun unbind(generation: Long) = Unit

        override fun close() {
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
}
