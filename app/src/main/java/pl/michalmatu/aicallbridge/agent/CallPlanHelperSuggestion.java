package pl.michalmatu.aicallbridge.agent;

import java.util.Objects;

/**
 * Proposal-only helper output that may reference one rule already declared by {@link CallPlan}.
 *
 * <p>The helper cannot supply speech, facts, outcomes, proposals, actions, targets, or commitment
 * authority. The application resolves this opaque rule reference against the immutable plan.</p>
 */
public record CallPlanHelperSuggestion(String ruleId) {
    public CallPlanHelperSuggestion {
        Objects.requireNonNull(ruleId, "ruleId");
        ruleId = ruleId.trim();
        if (ruleId.isEmpty()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
    }

    @Override
    public String toString() {
        return "CallPlanHelperSuggestion[ruleId=" + ruleId + "]";
    }
}
