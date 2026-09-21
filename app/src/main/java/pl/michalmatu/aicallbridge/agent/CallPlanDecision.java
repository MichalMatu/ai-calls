package pl.michalmatu.aicallbridge.agent;

import java.util.Objects;

/** Immutable proposal returned by the deterministic CallPlan engine. */
public record CallPlanDecision(
    CallPlanAction action,
    String text,
    String ruleId
) {
    public CallPlanDecision {
        action = Objects.requireNonNull(action, "action");
        if (action == CallPlanAction.SAY) {
            requireNonBlank(text, "text");
            requireNonBlank(ruleId, "ruleId");
        } else if (text != null) {
            throw new IllegalArgumentException(action + " must not carry speech text");
        }
        if (ruleId != null) {
            ruleId = requireNonBlank(ruleId, "ruleId");
        }
    }

    public static CallPlanDecision say(String text, String ruleId) {
        return new CallPlanDecision(CallPlanAction.SAY, text, ruleId);
    }

    public static CallPlanDecision askRepeat() {
        return new CallPlanDecision(CallPlanAction.ASK_REPEAT, null, null);
    }

    public static CallPlanDecision takeOver(String ruleId) {
        return new CallPlanDecision(CallPlanAction.TAKE_OVER, null, ruleId);
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
