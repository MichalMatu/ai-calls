package pl.michalmatu.aicallbridge.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Soft preferences. These are deliberately separate from hard authorization constraints. */
public record CallPreferences(
    List<CallTimeWindow> preferredTimeWindows,
    List<String> preferredProviders,
    List<String> preferredLocations
) {
    public CallPreferences {
        Objects.requireNonNull(preferredTimeWindows, "preferredTimeWindows");
        preferredTimeWindows = List.copyOf(preferredTimeWindows);
        preferredProviders = copyNonBlankStrings(preferredProviders, "preferredProviders");
        preferredLocations = copyNonBlankStrings(preferredLocations, "preferredLocations");
    }

    public static CallPreferences none() {
        return new CallPreferences(List.of(), List.of(), List.of());
    }

    private static List<String> copyNonBlankStrings(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) {
            Objects.requireNonNull(value, name + " item");
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(name + " must not contain blank values");
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }
}
