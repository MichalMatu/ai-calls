package pl.michalmatu.aicallbridge.session;

public enum CallMediaSessionFailure {
    NONE,
    BIND_FAILED,
    PREPARE_FAILED,
    START_FAILED,
    HEARTBEAT_FAILED,
    HELPER_DISCONNECTED,
    INTERNAL_ERROR,
}
