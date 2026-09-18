package pl.michalmatu.aicallbridge.realtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Base64;
import java.util.Objects;

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

/**
 * Serializer/parser for the GA OpenAI Realtime WebSocket event subset used by the call bridge.
 *
 * <p>Authentication is deliberately outside this class: a short-lived client secret belongs on
 * the WebSocket handshake, never inside session or audio event payloads. Model selection also
 * belongs to the connection URL and is intentionally not repeated in session.update.</p>
 */
public final class RealtimeWebSocketProtocol {
    private static final PcmFormat REALTIME_PCM = new PcmFormat(24_000, 1, 16);

    public String sessionUpdate(RealtimeSessionConfig config) {
        Objects.requireNonNull(config, "config");

        JsonObject root = new JsonObject();
        root.addProperty("type", "session.update");

        JsonObject session = new JsonObject();
        session.addProperty("type", "realtime");
        session.addProperty("instructions", config.getInstructions());

        com.google.gson.JsonArray outputModalities = new com.google.gson.JsonArray();
        outputModalities.add("audio");
        session.add("output_modalities", outputModalities);

        JsonObject audio = new JsonObject();
        JsonObject input = new JsonObject();
        input.add("format", pcm24FormatJson());
        JsonObject turnDetection = new JsonObject();
        turnDetection.addProperty("type", "semantic_vad");
        input.add("turn_detection", turnDetection);
        audio.add("input", input);

        JsonObject output = new JsonObject();
        output.add("format", pcm24FormatJson());
        audio.add("output", output);
        session.add("audio", audio);

        root.add("session", session);
        return root.toString();
    }

    public String inputAudioAppend(PcmFrame frame) {
        Objects.requireNonNull(frame, "frame");
        requireRealtimePcm(frame);

        JsonObject root = new JsonObject();
        root.addProperty("type", "input_audio_buffer.append");
        root.addProperty("audio", Base64.getEncoder().encodeToString(frame.getData()));
        return root.toString();
    }

    public String responseCancel() {
        return "{\"type\":\"response.cancel\"}";
    }

    public RealtimeServerEvent parseServerEvent(String json, long monotonicTimestampNs) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("server event JSON must not be blank");
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("invalid Realtime server event JSON", error);
        }

        if (!root.has("type") || !root.get("type").isJsonPrimitive()) {
            throw new IllegalArgumentException("Realtime server event is missing type");
        }
        String type = root.get("type").getAsString();
        return switch (type) {
            case "response.output_audio.delta" -> parseAudioDelta(root, type, monotonicTimestampNs);
            case "input_audio_buffer.speech_started" -> RealtimeServerEvent.speechStarted(type);
            case "input_audio_buffer.speech_stopped" -> RealtimeServerEvent.speechStopped(type);
            case "error" -> RealtimeServerEvent.error(parseErrorMessage(root), type);
            default -> RealtimeServerEvent.other(type);
        };
    }

    private static RealtimeServerEvent parseAudioDelta(
        JsonObject root,
        String rawType,
        long monotonicTimestampNs
    ) {
        if (!root.has("delta") || !root.get("delta").isJsonPrimitive()) {
            throw new IllegalArgumentException("response.output_audio.delta is missing delta");
        }
        final byte[] pcm;
        try {
            pcm = Base64.getDecoder().decode(root.get("delta").getAsString());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("response.output_audio.delta is not valid base64", error);
        }
        if ((pcm.length & 1) != 0) {
            throw new IllegalArgumentException("Realtime audio delta must contain whole PCM16 samples");
        }
        return RealtimeServerEvent.audioDelta(
            new PcmFrame(REALTIME_PCM, pcm, monotonicTimestampNs),
            rawType
        );
    }

    private static String parseErrorMessage(JsonObject root) {
        if (root.has("error") && root.get("error").isJsonObject()) {
            JsonObject error = root.getAsJsonObject("error");
            if (error.has("message") && error.get("message").isJsonPrimitive()) {
                String message = error.get("message").getAsString();
                if (!message.isBlank()) {
                    return message;
                }
            }
        }
        return "Realtime server error";
    }

    private static JsonObject pcm24FormatJson() {
        JsonObject format = new JsonObject();
        format.addProperty("type", "audio/pcm");
        format.addProperty("rate", 24_000);
        return format;
    }

    private static void requireRealtimePcm(PcmFrame frame) {
        PcmFormat format = Objects.requireNonNull(frame.getFormat(), "frame.format");
        byte[] data = Objects.requireNonNull(frame.getData(), "frame.data");
        if (format.getSampleRateHz() != 24_000
            || format.getChannels() != 1
            || format.getBitsPerSample() != 16) {
            throw new IllegalArgumentException("Realtime input audio must be mono PCM16LE at 24000 Hz");
        }
        if ((data.length & 1) != 0) {
            throw new IllegalArgumentException("Realtime input audio must contain whole PCM16 samples");
        }
    }
}
