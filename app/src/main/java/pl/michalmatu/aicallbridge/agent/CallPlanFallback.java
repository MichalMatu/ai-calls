package pl.michalmatu.aicallbridge.agent;

import java.util.Objects;

/** Fail-closed fallback used when no unique deterministic rule matches a final transcript. */
public enum CallPlanFallback {
    ASK_REPEAT(CallPlanAction.ASK_REPEAT),
    TAKE_OVER(CallPlanAction.TAKE_OVER);

    private final CallPlanAction action;

    CallPlanFallback(CallPlanAction action) {
        this.action = Objects.requireNonNull(action, "action");
    }

    public CallPlanAction action() {
        return action;
    }
}
