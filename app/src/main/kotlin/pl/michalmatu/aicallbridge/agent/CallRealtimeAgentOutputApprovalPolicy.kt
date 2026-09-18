package pl.michalmatu.aicallbridge.agent

import pl.michalmatu.aicallbridge.realtime.RealtimeOutputPartId
import pl.michalmatu.aicallbridge.session.CallRealtimeOutputApprovalPolicy
import pl.michalmatu.aicallbridge.session.CallRealtimeOutputDecision

/**
 * Application-owned final speech release policy for Telephone Agent output.
 *
 * Ordinary model speech may reach the cellular uplink only while the workflow is in normal active
 * negotiation and no external commitment authorization is pending. Every other workflow state
 * fails closed. The exact [CallCommitmentGate] instance is shared with proposal/commit handlers.
 */
class CallRealtimeAgentOutputApprovalPolicy(
    private val workflow: CallWorkflow,
    private val commitmentGate: CallCommitmentGate,
) : CallRealtimeOutputApprovalPolicy {
    override fun evaluate(
        partId: RealtimeOutputPartId,
        transcript: String,
    ): CallRealtimeOutputDecision {
        val safeToRelease =
            workflow.snapshot().state() == CallWorkflowState.ACTIVE_NEGOTIATION &&
                !commitmentGate.hasAuthorization()
        return if (safeToRelease) {
            CallRealtimeOutputDecision.RELEASE
        } else {
            CallRealtimeOutputDecision.DROP
        }
    }
}
