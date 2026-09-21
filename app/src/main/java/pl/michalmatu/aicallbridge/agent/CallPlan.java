package pl.michalmatu.aicallbridge.agent;

import java.util.List;
import java.util.Objects;

/**
 * Immutable pre-dial execution context for deterministic bounded dialogue rules.
 *
 * <p>This type references existing task and target authority. It does not grant dialing,
 * commitment, fact, or output authority.</p>
 */
public final class CallPlan {
    private final CallTask task;
    private final CallResolvedTarget resolvedTarget;
    private final List<CallPlanRule> rules;
    private final CallPlanFallbackPolicy fallbackPolicy;

    public CallPlan(
        CallTask task,
        CallResolvedTarget resolvedTarget,
        List<CallPlanRule> rules,
        CallPlanFallback fallback
    ) {
        this(task, resolvedTarget, rules, CallPlanFallbackPolicy.fromLegacy(fallback));
    }

    public CallPlan(
        CallTask task,
        CallResolvedTarget resolvedTarget,
        List<CallPlanRule> rules,
        CallPlanFallbackPolicy fallbackPolicy
    ) {
        this.task = Objects.requireNonNull(task, "task");
        this.resolvedTarget = Objects.requireNonNull(resolvedTarget, "resolvedTarget");
        Objects.requireNonNull(rules, "rules");
        this.rules = List.copyOf(rules);
        for (CallPlanRule rule : this.rules) {
            Objects.requireNonNull(rule, "rules item");
        }
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
    }

    public CallTask task() {
        return task;
    }

    public CallResolvedTarget resolvedTarget() {
        return resolvedTarget;
    }

    public List<CallPlanRule> rules() {
        return rules;
    }

    /** Compatibility accessor for the original one-step fallback API. */
    public CallPlanFallback fallback() {
        return fallbackPolicy.initialFallback();
    }

    public CallPlanFallbackPolicy fallbackPolicy() {
        return fallbackPolicy;
    }

    @Override
    public String toString() {
        return "CallPlan[task=REDACTED, resolvedTarget=REDACTED, rules="
            + rules.size()
            + ", fallbackPolicy="
            + fallbackPolicy
            + "]";
    }
}
