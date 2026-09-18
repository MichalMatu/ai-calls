package pl.michalmatu.aicallbridge.realtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

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

        JsonArray outputModalities = new JsonArray();
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

        if (!config.getTools().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (RealtimeFunctionTool tool : config.getTools()) {
                JsonObject encoded = new JsonObject();
                encoded.addProperty("type", "function");
                encoded.addProperty("name", tool.getName());
                encoded.addProperty("description", tool.getDescription());
                encoded.add(
                    "parameters",
                    JsonParser.parseString(tool.getParametersJson()).getAsJsonObject()
                );
                tools.add(encoded);
            }
            session.add("tools", tools);
            session.addProperty("tool_choice", "auto");
        }

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

    /** Creates the official client event that returns one application-owned function result. */
    public String functionCallOutput(String callId, String outputJson) {
        String safeCallId = requireNonBlank(callId, "callId");
        String safeOutput = requireValidJson(outputJson, "outputJson");

        JsonObject root = new JsonObject();
        root.addProperty("type", "conversation.item.create");
        JsonObject item = new JsonObject();
        item.addProperty("type", "function_call_output");
        item.addProperty("call_id", safeCallId);
        item.addProperty("output", safeOutput);
        root.add("item", item);
        return root.toString();
    }

    /** Starts the default follow-up model response after a function result was added. */
    public String responseCreate() {
        return responseCreate(RealtimeFunctionFollowup.Auto.INSTANCE);
    }

    /** Starts one response with a response-scoped tool-choice override. */
    public String responseCreate(RealtimeFunctionFollowup followup) {
        Objects.requireNonNull(followup, "followup");
        if (followup instanceof RealtimeFunctionFollowup.Auto) {
            return "{\"type\":\"response.create\"}";
        }

        JsonObject root = new JsonObject();
        root.addProperty("type", "response.create");
        JsonObject response = new JsonObject();

        if (followup instanceof RealtimeFunctionFollowup.NoTools) {
            response.addProperty("tool_choice", "none");
        } else if (followup instanceof RealtimeFunctionFollowup.ForceFunction forced) {
            JsonObject toolChoice = new JsonObject();
            toolChoice.addProperty("type", "function");
            toolChoice.addProperty("name", forced.getName());
            response.add("tool_choice", toolChoice);
        } else {
            throw new IllegalArgumentException("unsupported Realtime function follow-up policy");
        }

        root.add("response", response);
        return root.toString();
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
            case "response.output_audio.done" -> RealtimeServerEvent.outputAudioDone(
                parseOutputPartId(root, true),
                type
            );
            case "response.output_audio_transcript.delta" ->
                RealtimeServerEvent.outputAudioTranscriptDelta(
                    parseOutputPartId(root, true),
                    requiredText(root, "delta", type),
                    type
                );
            case "response.output_audio_transcript.done" ->
                RealtimeServerEvent.outputAudioTranscriptDone(
                    parseOutputPartId(root, true),
                    requiredText(root, "transcript", type),
                    type
                );
            case "response.done" -> parseResponseDone(root, type);
            case "input_audio_buffer.speech_started" -> RealtimeServerEvent.speechStarted(type);
            case "input_audio_buffer.speech_stopped" -> RealtimeServerEvent.speechStopped(type);
            case "response.output_item.done" -> parseOutputItemDone(root, type);
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
            parseOutputPartId(root, false),
            rawType
        );
    }

    private static RealtimeServerEvent parseResponseDone(JsonObject root, String rawType) {
        if (!root.has("response") || !root.get("response").isJsonObject()) {
            throw new IllegalArgumentException("response.done is missing response");
        }
        JsonObject response = root.getAsJsonObject("response");
        String responseId = requiredString(response, "id", "response.done response");
        String rawStatus = requiredString(response, "status", "response.done response");
        return RealtimeServerEvent.responseDone(
            responseId,
            RealtimeResponseStatus.fromWireValue(rawStatus),
            rawType
        );
    }

    private static RealtimeServerEvent parseOutputItemDone(JsonObject root, String rawType) {
        if (!root.has("item") || !root.get("item").isJsonObject()) {
            throw new IllegalArgumentException("response.output_item.done is missing item");
        }
        JsonObject item = root.getAsJsonObject("item");
        if (!hasString(item, "type")) {
            throw new IllegalArgumentException("response.output_item.done item is missing type");
        }
        if (!"function_call".equals(item.get("type").getAsString())) {
            return RealtimeServerEvent.other(rawType);
        }

        String callId = requiredString(item, "call_id", "function_call");
        String name = requiredString(item, "name", "function_call");
        String arguments = requiredString(item, "arguments", "function_call");
        return RealtimeServerEvent.functionCall(
            new RealtimeFunctionCall(callId, name, arguments),
            rawType
        );
    }

    private static RealtimeOutputPartId parseOutputPartId(JsonObject root, boolean required) {
        boolean any = root.has("response_id")
            || root.has("item_id")
            || root.has("output_index")
            || root.has("content_index");
        if (!any && !required) {
            return null;
        }

        String responseId = requiredString(root, "response_id", "Realtime output event");
        String itemId = requiredString(root, "item_id", "Realtime output event");
        int outputIndex = requiredNonNegativeInt(root, "output_index", "Realtime output event");
        int contentIndex = requiredNonNegativeInt(root, "content_index", "Realtime output event");
        return new RealtimeOutputPartId(responseId, itemId, outputIndex, contentIndex);
    }

    private static int requiredNonNegativeInt(JsonObject object, String name, String context) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException(context + " is missing " + name);
        }
        JsonPrimitive primitive = object.getAsJsonPrimitive(name);
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(context + " has non-numeric " + name);
        }
        final int value;
        try {
            value = primitive.getAsInt();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(context + " has invalid " + name, error);
        }
        if (value < 0) {
            throw new IllegalArgumentException(context + " has negative " + name);
        }
        return value;
    }

    private static String requiredText(JsonObject object, String name, String context) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException(context + " is missing " + name);
        }
        return object.get(name).getAsString();
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

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String requireValidJson(String value, String name) {
        String safe = requireNonBlank(value, name);
        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(safe);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(name + " must be valid JSON", error);
        }
        if (parsed.isJsonNull()) {
            throw new IllegalArgumentException(name + " must not be JSON null");
        }
        return safe;
    }

    private static boolean hasString(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonPrimitive();
    }

    private static String requiredString(JsonObject object, String name, String context) {
        if (!hasString(object, name)) {
            throw new IllegalArgumentException(context + " is missing " + name);
        }
        String value = object.get(name).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException(context + " has blank " + name);
        }
        return value;
    }
}
