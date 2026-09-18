package pl.michalmatu.aicallbridge.session;

/** Immutable host-visible counters for one call/Reatime PCM bridge generation. */
public record CallRealtimePcmBridgeSnapshot(
    long downlinkBytes,
    long downlinkFrames,
    long uplinkBytes,
    long uplinkFrames,
    String terminalReason
) {}
