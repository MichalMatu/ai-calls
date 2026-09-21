package pl.michalmatu.aicallbridge.localcall;

import java.util.Objects;

import pl.michalmatu.aicallbridge.agent.CallPlanAction;
import pl.michalmatu.aicallbridge.agent.CallPlanDecision;
import pl.michalmatu.aicallbridge.agent.CallPolicyDecision;

/**
 * Structured result of routing one final transcript through CallPlan and the existing workflow.
 *
 * <p>The policy decision exists only for a typed proposal. Payload rendering stays delegated to
 * the already-redacted CallPlanDecision representation.</p>
 */
record CallPlanTurnResult(CallPlanDecision decision, CallPolicyDecision policyDecision) {
    CallPlanTurnResult {
        Objects.requireNonNull(decision, "decision");
        boolean proposal = decision.action() == CallPlanAction.PROPOSAL;
        if (proposal && policyDecision == null) {
            throw new IllegalArgumentException("proposal result requires policyDecision");
        }
        if (!proposal && policyDecision != null) {
            throw new IllegalArgumentException("non-proposal result must not include policyDecision");
        }
    }

    @Override
    public String toString() {
        return "CallPlanTurnResult[decision=" + decision + ", policyDecision=" + policyDecision + "]";
    }
}
