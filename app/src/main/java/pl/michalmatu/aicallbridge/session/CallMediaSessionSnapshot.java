package pl.michalmatu.aicallbridge.session;

/** Immutable structured state emitted by the production media-session coordinator. */
public record CallMediaSessionSnapshot(
    long generation,
    CallMediaSessionState state,
    CallMediaSessionFailure failure,
    String failureDetail,
    long startedAtMs,
    long updatedAtMs,
    long heartbeatCount
) {}
