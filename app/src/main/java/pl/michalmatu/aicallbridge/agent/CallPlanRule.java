package pl.michalmatu.aicallbridge.agent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** One deterministic final-transcript rule that may read exactly one already-authorized fact. */
public record CallPlanRule(
    String id,
    Set<String> utterances,
    String authorizedFactKey
) {
    public CallPlanRule {
        id = requireNonBlank(id, "id");
        authorizedFactKey = requireNonBlank(authorizedFactKey, "authorizedFactKey");
        utterances = copyUtterances(utterances);
        if (utterances.isEmpty()) {
            throw new IllegalArgumentException("utterances must not be empty");
        }
    }

    private static Set<String> copyUtterances(Set<String> source) {
        Objects.requireNonNull(source, "utterances");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String utterance : source) {
            result.add(requireNonBlank(utterance, "utterance"));
        }
        return Collections.unmodifiableSet(result);
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
