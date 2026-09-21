package pl.michalmatu.aicallbridge.agent;

/** Bounded proposal actions produced by the deterministic CallPlan engine. */
public enum CallPlanAction {
    SAY,
    ASK_REPEAT,
    COMPLETE,
    TAKE_OVER
}
