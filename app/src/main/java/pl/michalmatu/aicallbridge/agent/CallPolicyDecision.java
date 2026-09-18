package pl.michalmatu.aicallbridge.agent;

import java.util.List;
import java.util.Objects;

/** Immutable confirmation-policy decision with ordered machine-readable reasons. */
public record CallPolicyDecision(CallPolicyAction action, List<CallPolicyReason> reasons) {
    public CallPolicyDecision {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(reasons, "reasons");
        reasons = List.copyOf(reasons);
        if (action == CallPolicyAction.AUTONOMOUSLY_ALLOWED && !reasons.isEmpty()) {
            throw new IllegalArgumentException("autonomously allowed decision cannot have reasons");
        }
        if (action == CallPolicyAction.NEEDS_USER_DECISION && reasons.isEmpty()) {
            throw new IllegalArgumentException("user decision requires at least one reason");
        }
    }
}
