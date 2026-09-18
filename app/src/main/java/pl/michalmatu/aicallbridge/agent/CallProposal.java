package pl.michalmatu.aicallbridge.agent;

import java.time.ZonedDateTime;

/** Structured facts extracted from one concrete counterparty offer. */
public record CallProposal(
    ZonedDateTime scheduledAt,
    MoneyAmount price,
    CallPaymentMode paymentMode,
    String provider,
    String location
) {
    public CallProposal {
        provider = normalizeOptional(provider, "provider");
        location = normalizeOptional(location, "location");
    }

    @Override
    public String toString() {
        return "CallProposal[data=REDACTED]";
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
