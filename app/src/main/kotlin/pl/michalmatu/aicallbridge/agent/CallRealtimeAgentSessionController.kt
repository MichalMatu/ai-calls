package pl.michalmatu.aicallbridge.agent

import java.util.concurrent.Executor
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialProvider
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport
import pl.michalmatu.aicallbridge.session.CallMediaSessionCoordinator
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestrator
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot

/**
 * App-layer owner for one bound Telephone Agent Realtime session.
 *
 * This class deliberately does not own [CallMediaSessionCoordinator]. The higher production runtime
 * owns the privileged media backend/scheduler/coordinator lifetime. Closing this controller only
 * tears down its Realtime/orchestrator generation, preserving a single clear resource hierarchy.
 */
class CallRealtimeAgentSessionController private constructor(
    val workflow: CallWorkflow,
    val sessionSpec: CallRealtimeAgentSessionSpec,
    private val orchestrator: CallRealtimeSessionOrchestrator,
) : AutoCloseable {
    fun start(): Long = orchestrator.start(sessionSpec.request)

    fun snapshot(): CallRealtimeSessionOrchestratorSnapshot = orchestrator.snapshot()

    fun takeOverNow() {
        orchestrator.takeOverNow()
    }

    fun hasPendingUserDecision(): Boolean =
        sessionSpec.proposalHandler.hasPendingUserDecision()

    fun approvePendingProposal(): Result<CallProposal> =
        sessionSpec.proposalHandler.approvePendingProposal()

    fun rejectPendingProposal(): Result<CallProposal> =
        sessionSpec.proposalHandler.rejectPendingProposal()

    override fun close() {
        orchestrator.close()
    }

    companion object {
        private const val DEFAULT_SAMPLE_RATE_HZ = 16_000

        @JvmStatic
        fun create(
            workflow: CallWorkflow,
            coordinator: CallMediaSessionCoordinator,
            credentialProvider: RealtimeCredentialProvider,
            transportFactory: () -> RealtimeTransport,
            bootstrapExecutor: Executor,
            sessionEndpoint: String,
            model: String,
            sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
            listener: (CallRealtimeSessionOrchestratorSnapshot) -> Unit = {},
        ): CallRealtimeAgentSessionController {
            val spec = CallRealtimeAgentSessionSpec.create(
                workflow = workflow,
                sessionEndpoint = sessionEndpoint,
                model = model,
                sampleRateHz = sampleRateHz,
            )
            val orchestrator = CallRealtimeSessionOrchestrator(
                coordinator = coordinator,
                credentialProvider = credentialProvider,
                transportFactory = transportFactory,
                bootstrapExecutor = bootstrapExecutor,
                listener = listener,
                functionCallHandler = spec.functionCallHandler,
            )
            return CallRealtimeAgentSessionController(workflow, spec, orchestrator)
        }
    }
}
