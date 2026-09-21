package pl.michalmatu.aicallbridge.agent;

import java.util.Objects;

/** Immutable proposal returned by the deterministic CallPlan engine. */
public record CallPlanDecision(
    CallPlanAction action,
    String text,
    String ruleId,
    CallOutcome outcome,
    CallProposal proposal
) {
    public CallPlanDecision(CallPlanAction action, String text, String ruleId) {
        this(action, text, ruleId, null, null);
    }

    public CallPlanDecision(CallPlanAction action, String text, String ruleId, CallOutcome outcome) {
        this(action, text, ruleId, outcome, null);
    }

    public CallPlanDecision {
        action = Objects.requireNonNull(action, "action");
        if (action == CallPlanAction.SAY) {
            requireNonBlank(text, "text");
            requireNonBlank(ruleId, "ruleId");
            if (outcome != null || proposal != null) {
                throw new IllegalArgumentException("SAY must not carry outcome or proposal");
            }
        } else if (action == CallPlanAction.PROPOSAL) {
            if (text != null) {
                throw new IllegalArgumentException("PROPOSAL must not carry speech text");
            }
            requireNonBlank(ruleId, "ruleId");
            if (outcome != null) {
                throw new IllegalArgumentException("PROPOSAL must not carry outcome");
            }
            proposal = Objects.requireNonNull(proposal, "proposal");
        } else if (action == CallPlanAction.COMPLETE) {
            if (text != null) {
                throw new IllegalArgumentException("COMPLETE must not carry speech text");
            }
            requireNonBlank(ruleId, "ruleId");
            outcome = Objects.requireNonNull(outcome, "outcome");
            if (proposal != null) {
                throw new IllegalArgumentException("COMPLETE must not carry proposal");
            }
        } else {
            if (text != null) {
                throw new IllegalArgumentException(action + " must not carry speech text");
            }
            if (outcome != null) {
                throw new IllegalArgumentException(action + " must not carry outcome");
            }
            if (proposal != null) {
                throw new IllegalArgumentException(action + " must not carry proposal");
            }
        }
        if (ruleId != null) {
            ruleId = requireNonBlank(ruleId, "ruleId");
        }
    }

    public static CallPlanDecision say(String text, String ruleId) {
        return new CallPlanDecision(CallPlanAction.SAY, text, ruleId, null, null);
    }

    public static CallPlanDecision askRepeat() {
        return new CallPlanDecision(CallPlanAction.ASK_REPEAT, null, null, null, null);
    }

    public static CallPlanDecision proposal(CallProposal proposal, String ruleId) {
        return new CallPlanDecision(CallPlanAction.PROPOSAL, null, ruleId, null, proposal);
    }

    public static CallPlanDecision complete(CallOutcome outcome, String ruleId) {
        return new CallPlanDecision(CallPlanAction.COMPLETE, null, ruleId, outcome, null);
    }

    public static CallPlanDecision takeOver(String ruleId) {
        return new CallPlanDecision(CallPlanAction.TAKE_OVER, null, ruleId, null, null);
    }

    @Override
    public String toString() {
        return "CallPlanDecision[action=" + action
            + ", text=" + (text == null ? "null" : "REDACTED")
            + ", ruleId=" + ruleId
            + ", outcome=" + (outcome == null ? "null" : "REDACTED")
            + ", proposal=" + (proposal == null ? "null" : "REDACTED")
            + "]";
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
