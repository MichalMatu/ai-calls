package pl.michalmatu.aicallbridge.agent;

/** High-level Telephone Agent v1 workflow states. */
public enum CallWorkflowState {
    RESEARCHING,
    READY_TO_DIAL,
    DIALING,
    ACTIVE_NEGOTIATION,
    NEEDS_USER_DECISION,
    COMPLETED,
    FAILED,
}
