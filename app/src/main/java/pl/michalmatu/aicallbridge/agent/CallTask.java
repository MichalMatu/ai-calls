package pl.michalmatu.aicallbridge.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable user-authorized task given to the Telephone Agent workflow. */
public record CallTask(
    String targetDescription,
    String action,
    String service,
    CallConstraints constraints,
    CallPreferences preferences,
    Map<String, String> authorizedFacts
) {
    public CallTask {
        targetDescription = requireNonBlank(targetDescription, "targetDescription");
        action = requireNonBlank(action, "action");
        service = requireNonBlank(service, "service");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(preferences, "preferences");
        authorizedFacts = copyAuthorizedFacts(authorizedFacts);
    }

    private static Map<String, String> copyAuthorizedFacts(Map<String, String> facts) {
        Objects.requireNonNull(facts, "authorizedFacts");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            String key = requireNonBlank(entry.getKey(), "authorizedFacts key");
            String value = requireNonBlank(entry.getValue(), "authorizedFacts value");
            if (result.put(key, value) != null) {
                throw new IllegalArgumentException("duplicate authorizedFacts key: " + key);
            }
        }
        return Collections.unmodifiableMap(result);
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
