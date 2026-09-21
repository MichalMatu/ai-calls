package pl.michalmatu.aicallbridge.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application-owned validator for proposal-only language-helper suggestions.
 *
 * <p>A helper may nominate only a rule id already present in the immutable {@link CallPlan}. The
 * validator never accepts helper-supplied payload data and never grants workflow, commitment,
 * dialing, or output authority.</p>
 */
public final class CallPlanHelperValidator {
    public CallPlanDecision validate(
        CallPlan plan,
        CallPlanHelperSuggestion suggestion,
        int priorUnknownCount
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(suggestion, "suggestion");
        if (priorUnknownCount < 0) {
            throw new IllegalArgumentException("priorUnknownCount must be >= 0");
        }

        List<CallPlanRule> factMatches = new ArrayList<>();
        for (CallPlanRule rule : plan.rules()) {
            if (rule.id().equals(suggestion.ruleId())) {
                factMatches.add(rule);
            }
        }

        List<CallPlanCompletionRule> completionMatches = new ArrayList<>();
        for (CallPlanCompletionRule rule : plan.completionRules()) {
            if (rule.id().equals(suggestion.ruleId())) {
                completionMatches.add(rule);
            }
        }

        List<CallPlanProposalRule> proposalMatches = new ArrayList<>();
        for (CallPlanProposalRule rule : plan.proposalRules()) {
            if (rule.id().equals(suggestion.ruleId())) {
                proposalMatches.add(rule);
            }
        }

        int totalMatches = factMatches.size() + completionMatches.size() + proposalMatches.size();
        if (totalMatches != 1) {
            return fallback(plan.fallbackPolicy(), priorUnknownCount);
        }

        if (completionMatches.size() == 1) {
            CallPlanCompletionRule rule = completionMatches.get(0);
            return CallPlanDecision.complete(rule.outcome(), rule.id());
        }
        if (proposalMatches.size() == 1) {
            CallPlanProposalRule rule = proposalMatches.get(0);
            return CallPlanDecision.proposal(rule.proposal(), rule.id());
        }

        CallPlanRule rule = factMatches.get(0);
        String authorizedValue = plan.task().authorizedFacts().get(rule.authorizedFactKey());
        if (authorizedValue == null) {
            return CallPlanDecision.takeOver(rule.id());
        }
        return CallPlanDecision.say(authorizedValue, rule.id());
    }

    private static CallPlanDecision fallback(
        CallPlanFallbackPolicy policy,
        int priorUnknownCount
    ) {
        return switch (policy.actionFor(priorUnknownCount)) {
            case ASK_REPEAT -> CallPlanDecision.askRepeat();
            case TAKE_OVER -> CallPlanDecision.takeOver(null);
            case SAY, COMPLETE, PROPOSAL -> throw new IllegalStateException(
                "fallback policy must not produce plan-owned payload actions"
            );
        };
    }
}
