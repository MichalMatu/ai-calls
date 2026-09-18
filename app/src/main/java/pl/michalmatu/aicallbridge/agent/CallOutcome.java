package pl.michalmatu.aicallbridge.agent;

import java.time.ZonedDateTime;
import java.util.Objects;

/** Structured result produced after a call attempt. Optional fields are null when unavailable. */
public record CallOutcome(
    CallOutcomeStatus status,
    String summary,
    ZonedDateTime scheduledAt,
    String provider,
    String location,
    MoneyAmount cost,
    String bookingReference,
    String followUp
) {
    public CallOutcome {
        Objects.requireNonNull(status, "status");
        summary = requireNonBlank(summary, "summary");
        provider = normalizeOptional(provider, "provider");
        location = normalizeOptional(location, "location");
        bookingReference = normalizeOptional(bookingReference, "bookingReference");
        followUp = normalizeOptional(followUp, "followUp");
    }

    @Override
    public String toString() {
        return "CallOutcome[status=" + status + ", data=REDACTED]";
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, String name) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must be null or non-blank");
        }
        return normalized;
    }
}
