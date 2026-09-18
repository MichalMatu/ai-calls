package pl.michalmatu.aicallbridge.agent

import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionCallHandler
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionRequest

/**
 * Complete app-owned binding for one Telephone Agent Realtime session.
 *
 * Instructions, proposal evaluation, one-shot commitment authorization and function routing are
 * created together from the same workflow. Production code therefore cannot advertise a commit
 * tool backed by unrelated authority or accidentally omit the deterministic proposal gate.
 */
class CallRealtimeAgentSessionSpec private constructor(
    val request: CallRealtimeSessionRequest,
    val proposalHandler: CallRealtimeProposalFunctionHandler,
    val commitmentHandler: CallRealtimeCommitmentFunctionHandler,
    val commitmentGate: CallCommitmentGate,
    val functionCallHandler: CallRealtimeFunctionCallHandler,
) {
    companion object {
        private const val DEFAULT_SAMPLE_RATE_HZ = 16_000

        @JvmStatic
        fun create(
            workflow: CallWorkflow,
            sessionEndpoint: String,
            model: String,
            sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
        ): CallRealtimeAgentSessionSpec {
            val commitmentGate = CallCommitmentGate()
            val proposalHandler = CallRealtimeProposalFunctionHandler(workflow, commitmentGate)
            val commitmentHandler = CallRealtimeCommitmentFunctionHandler(workflow, commitmentGate)
            val functionCallHandler = CallRealtimeFunctionCallHandler { call, responder ->
                when (call.name) {
                    CallRealtimeProposalFunctionHandler.FUNCTION_NAME ->
                        proposalHandler.onFunctionCall(call, responder)
                    CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME ->
                        commitmentHandler.onFunctionCall(call, responder)
                    else -> throw IllegalArgumentException("unsupported Telephone Agent function: ${call.name}")
                }
            }
            val request = CallRealtimeSessionRequest(
                sessionEndpoint = sessionEndpoint,
                model = model,
                instructions = CallRealtimeInstructionsBuilder().build(workflow.snapshot().task()),
                sampleRateHz = sampleRateHz,
                tools = listOf(
                    CallRealtimeProposalFunctionHandler.tool(),
                    CallRealtimeCommitmentFunctionHandler.tool(),
                ),
            )
            return CallRealtimeAgentSessionSpec(
                request = request,
                proposalHandler = proposalHandler,
                commitmentHandler = commitmentHandler,
                commitmentGate = commitmentGate,
                functionCallHandler = functionCallHandler,
            )
        }
    }
}
