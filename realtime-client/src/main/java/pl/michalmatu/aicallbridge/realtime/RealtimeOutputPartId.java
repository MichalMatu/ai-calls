package pl.michalmatu.aicallbridge.realtime;

import java.util.Objects;

/** Stable identity shared by one streamed Realtime audio content part and its transcript events. */
public record RealtimeOutputPartId(
    String responseId,
    String itemId,
    int outputIndex,
    int contentIndex
) {
    public RealtimeOutputPartId {
        responseId = requireNonBlank(responseId, "responseId");
        itemId = requireNonBlank(itemId, "itemId");
        if (outputIndex < 0) {
            throw new IllegalArgumentException("outputIndex must be >= 0");
        }
        if (contentIndex < 0) {
            throw new IllegalArgumentException("contentIndex must be >= 0");
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
