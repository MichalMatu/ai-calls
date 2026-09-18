package pl.michalmatu.aicallbridge.agent;

import java.util.Objects;

/** Immutable observable state of one Telephone Agent task workflow. */
public record CallWorkflowSnapshot(
    CallTask task,
    CallWorkflowState state,
    CallResolvedTarget resolvedTarget,
    CallProposal pendingProposal,
    CallPolicyDecision pendingDecision,
    CallOutcome outcome,
    String failureReason
) {
    public CallWorkflowSnapshot {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(state, "state");

        boolean needsDecision = state == CallWorkflowState.NEEDS_USER_DECISION;
        if (needsDecision != (pendingProposal != null && pendingDecision != null)) {
            throw new IllegalArgumentException(
                "NEEDS_USER_DECISION requires one pending proposal and policy decision"
            );
        }
        if (!needsDecision && (pendingProposal != null || pendingDecision != null)) {
            throw new IllegalArgumentException("pending decision data is only valid while waiting for user");
        }
        if (state == CallWorkflowState.COMPLETED && outcome == null) {
            throw new IllegalArgumentException("COMPLETED requires an outcome");
        }
        if (state != CallWorkflowState.COMPLETED && outcome != null) {
            throw new IllegalArgumentException("outcome is only valid for COMPLETED workflow");
        }
        if (state == CallWorkflowState.FAILED && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("FAILED requires a failure reason");
        }
        if (state != CallWorkflowState.FAILED && failureReason != null) {
            throw new IllegalArgumentException("failureReason is only valid for FAILED workflow");
        }
    }
}
