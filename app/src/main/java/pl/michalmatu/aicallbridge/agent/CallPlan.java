package pl.michalmatu.aicallbridge.agent;

import java.util.List;
import java.util.Objects;

/**
 * Immutable pre-dial execution context for deterministic bounded dialogue rules.
 *
 * <p>This type references existing task and target authority. It does not grant dialing,
 * commitment, fact, or output authority.</p>
 */
public record CallPlan(
    CallTask task,
    CallResolvedTarget resolvedTarget,
    List<CallPlanRule> rules,
    CallPlanFallback fallback
) {
    public CallPlan {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(resolvedTarget, "resolvedTarget");
        Objects.requireNonNull(rules, "rules");
        fallback = Objects.requireNonNull(fallback, "fallback");
        rules = List.copyOf(rules);
        for (CallPlanRule rule : rules) {
            Objects.requireNonNull(rule, "rules item");
        }
    }

    @Override
    public String toString() {
        return "CallPlan[task=REDACTED, resolvedTarget=REDACTED, rules="
            + rules.size()
            + ", fallback="
            + fallback
            + "]";
    }
}
