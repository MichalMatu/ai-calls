package pl.michalmatu.aicallbridge.textagent

import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState

enum class TextOutputDecision {
    RELEASE,
    DROP,
}

fun interface TextOutputApprovalPolicy {
    fun evaluate(text: String): TextOutputDecision
}

/** Application-owned speech release gate for complete text-agent responses. */
internal class CallTextAgentOutputApprovalPolicy(
    private val workflow: CallWorkflow,
    private val commitmentGate: CallCommitmentGate,
) : TextOutputApprovalPolicy {
    override fun evaluate(text: String): TextOutputDecision {
        if (text.isBlank()) return TextOutputDecision.DROP
        val safeToRelease =
            workflow.snapshot().state() == CallWorkflowState.ACTIVE_NEGOTIATION &&
                !commitmentGate.hasAuthorization()
        return if (safeToRelease) TextOutputDecision.RELEASE else TextOutputDecision.DROP
    }
}
