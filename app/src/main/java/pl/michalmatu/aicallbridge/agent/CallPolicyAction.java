package pl.michalmatu.aicallbridge.agent;

/** Deterministic result of evaluating one counterparty proposal against user authority. */
public enum CallPolicyAction {
    AUTONOMOUSLY_ALLOWED,
    NEEDS_USER_DECISION,
}
