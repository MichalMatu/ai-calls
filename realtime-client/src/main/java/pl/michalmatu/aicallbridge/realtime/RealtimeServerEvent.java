package pl.michalmatu.aicallbridge.realtime;

import pl.michalmatu.aicallbridge.audio.PcmFrame;

/** Minimal typed subset of Realtime server events needed by the telephone audio bridge. */
public final class RealtimeServerEvent {
    public enum Type {
        AUDIO_DELTA,
        OUTPUT_AUDIO_DONE,
        OUTPUT_AUDIO_TRANSCRIPT_DELTA,
        OUTPUT_AUDIO_TRANSCRIPT_DONE,
        REMOTE_SPEECH_STARTED,
        REMOTE_SPEECH_STOPPED,
        FUNCTION_CALL,
        ERROR,
        OTHER,
    }

    private final Type type;
    private final PcmFrame audioFrame;
    private final RealtimeOutputPartId outputPartId;
    private final String text;
    private final RealtimeFunctionCall functionCall;
    private final String errorMessage;
    private final String rawType;

    private RealtimeServerEvent(
        Type type,
        PcmFrame audioFrame,
        RealtimeOutputPartId outputPartId,
        String text,
        RealtimeFunctionCall functionCall,
        String errorMessage,
        String rawType
    ) {
        this.type = type;
        this.audioFrame = audioFrame;
        this.outputPartId = outputPartId;
        this.text = text;
        this.functionCall = functionCall;
        this.errorMessage = errorMessage;
        this.rawType = rawType;
    }

    static RealtimeServerEvent audioDelta(
        PcmFrame frame,
        RealtimeOutputPartId outputPartId,
        String rawType
    ) {
        return new RealtimeServerEvent(
            Type.AUDIO_DELTA,
            frame,
            outputPartId,
            null,
            null,
            null,
            rawType
        );
    }

    static RealtimeServerEvent outputAudioDone(RealtimeOutputPartId outputPartId, String rawType) {
        return new RealtimeServerEvent(
            Type.OUTPUT_AUDIO_DONE,
            null,
            outputPartId,
            null,
            null,
            null,
            rawType
        );
    }

    static RealtimeServerEvent outputAudioTranscriptDelta(
        RealtimeOutputPartId outputPartId,
        String delta,
        String rawType
    ) {
        return new RealtimeServerEvent(
            Type.OUTPUT_AUDIO_TRANSCRIPT_DELTA,
            null,
            outputPartId,
            delta,
            null,
            null,
            rawType
        );
    }

    static RealtimeServerEvent outputAudioTranscriptDone(
        RealtimeOutputPartId outputPartId,
        String transcript,
        String rawType
    ) {
        return new RealtimeServerEvent(
            Type.OUTPUT_AUDIO_TRANSCRIPT_DONE,
            null,
            outputPartId,
            transcript,
            null,
            null,
            rawType
        );
    }

    static RealtimeServerEvent speechStarted(String rawType) {
        return new RealtimeServerEvent(
            Type.REMOTE_SPEECH_STARTED,
            null,
            null,
            null,
            null,
            null,
            rawType
        );
    }

    static RealtimeServerEvent speechStopped(String rawType) {
        return new RealtimeServerEvent(
            Type.REMOTE_SPEECH_STOPPED,
            null,
            null,
            null,
            null,
            null,
            rawType
        );
    }

    static RealtimeServerEvent functionCall(RealtimeFunctionCall call, String rawType) {
        return new RealtimeServerEvent(Type.FUNCTION_CALL, null, null, null, call, null, rawType);
    }

    static RealtimeServerEvent error(String message, String rawType) {
        return new RealtimeServerEvent(Type.ERROR, null, null, null, null, message, rawType);
    }

    static RealtimeServerEvent other(String rawType) {
        return new RealtimeServerEvent(Type.OTHER, null, null, null, null, null, rawType);
    }

    public Type type() {
        return type;
    }

    public PcmFrame audioFrame() {
        return audioFrame;
    }

    public RealtimeOutputPartId outputPartId() {
        return outputPartId;
    }

    public String text() {
        return text;
    }

    public RealtimeFunctionCall functionCall() {
        return functionCall;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String rawType() {
        return rawType;
    }
}
