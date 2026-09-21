package pl.michalmatu.aicallbridge.agent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable exact final-transcript rule that proposes one predeclared typed counterparty offer. */
public record CallPlanProposalRule(
    String id,
    Set<String> utterances,
    CallProposal proposal
) {
    public CallPlanProposalRule {
        id = requireNonBlank(id, "id");
        Objects.requireNonNull(utterances, "utterances");
        if (utterances.isEmpty()) {
            throw new IllegalArgumentException("utterances must not be empty");
        }
        LinkedHashSet<String> copied = new LinkedHashSet<>();
        for (String utterance : utterances) {
            copied.add(requireNonBlank(utterance, "utterances item"));
        }
        utterances = Collections.unmodifiableSet(copied);
        proposal = Objects.requireNonNull(proposal, "proposal");
    }

    @Override
    public String toString() {
        return "CallPlanProposalRule[id=" + id + ", utterances=" + utterances.size()
            + ", proposal=REDACTED]";
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
