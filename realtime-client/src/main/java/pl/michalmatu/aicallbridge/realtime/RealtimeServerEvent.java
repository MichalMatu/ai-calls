package pl.michalmatu.aicallbridge.realtime;

import pl.michalmatu.aicallbridge.audio.PcmFrame;

/** Minimal typed subset of Realtime server events needed by the telephone audio bridge. */
public final class RealtimeServerEvent {
    public enum Type {
        AUDIO_DELTA,
        REMOTE_SPEECH_STARTED,
        REMOTE_SPEECH_STOPPED,
        ERROR,
        OTHER,
    }

    private final Type type;
    private final PcmFrame audioFrame;
    private final String errorMessage;
    private final String rawType;

    private RealtimeServerEvent(
        Type type,
        PcmFrame audioFrame,
        String errorMessage,
        String rawType
    ) {
        this.type = type;
        this.audioFrame = audioFrame;
        this.errorMessage = errorMessage;
        this.rawType = rawType;
    }

    static RealtimeServerEvent audioDelta(PcmFrame frame, String rawType) {
        return new RealtimeServerEvent(Type.AUDIO_DELTA, frame, null, rawType);
    }

    static RealtimeServerEvent speechStarted(String rawType) {
        return new RealtimeServerEvent(Type.REMOTE_SPEECH_STARTED, null, null, rawType);
    }

    static RealtimeServerEvent speechStopped(String rawType) {
        return new RealtimeServerEvent(Type.REMOTE_SPEECH_STOPPED, null, null, rawType);
    }

    static RealtimeServerEvent error(String message, String rawType) {
        return new RealtimeServerEvent(Type.ERROR, null, message, rawType);
    }

    static RealtimeServerEvent other(String rawType) {
        return new RealtimeServerEvent(Type.OTHER, null, null, rawType);
    }

    public Type type() {
        return type;
    }

    public PcmFrame audioFrame() {
        return audioFrame;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String rawType() {
        return rawType;
    }
}
