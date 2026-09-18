package pl.michalmatu.aicallbridge.agent

import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionCallHandler
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionRequest

/**
 * Complete app-owned binding for one Telephone Agent Realtime session.
 *
 * The request instructions, advertised proposal-policy tool and function-call handler are created
 * from the same workflow so production code cannot accidentally pair task authority with an
 * unrelated handler or omit the deterministic proposal gate.
 */
class CallRealtimeAgentSessionSpec private constructor(
    val request: CallRealtimeSessionRequest,
    val proposalHandler: CallRealtimeProposalFunctionHandler,
) {
    val functionCallHandler: CallRealtimeFunctionCallHandler
        get() = proposalHandler

    companion object {
        private const val DEFAULT_SAMPLE_RATE_HZ = 16_000

        @JvmStatic
        fun create(
            workflow: CallWorkflow,
            sessionEndpoint: String,
            model: String,
            sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
        ): CallRealtimeAgentSessionSpec {
            val proposalHandler = CallRealtimeProposalFunctionHandler(workflow)
            val request = CallRealtimeSessionRequest(
                sessionEndpoint = sessionEndpoint,
                model = model,
                instructions = CallRealtimeInstructionsBuilder().build(workflow.snapshot().task()),
                sampleRateHz = sampleRateHz,
                tools = listOf(CallRealtimeProposalFunctionHandler.tool()),
            )
            return CallRealtimeAgentSessionSpec(request, proposalHandler)
        }
    }
}
