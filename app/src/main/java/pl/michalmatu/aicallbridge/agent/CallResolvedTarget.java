package pl.michalmatu.aicallbridge.agent;

import java.util.Objects;

/**
 * One concrete dial target produced by an external resolver.
 *
 * <p>The dial address is deliberately redacted from {@link #toString()} so ordinary state/log
 * rendering cannot accidentally expose a full phone number.</p>
 */
public record CallResolvedTarget(String displayName, String dialAddress) {
    public CallResolvedTarget {
        displayName = requireNonBlank(displayName, "displayName");
        dialAddress = requireNonBlank(dialAddress, "dialAddress");
    }

    @Override
    public String toString() {
        return "CallResolvedTarget[displayName=" + displayName + ", dialAddress=REDACTED]";
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
