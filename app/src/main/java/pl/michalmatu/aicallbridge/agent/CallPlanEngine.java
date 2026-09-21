package pl.michalmatu.aicallbridge.agent;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Deterministic exact-rule classifier for final transcripts. */
public final class CallPlanEngine {
    public CallPlanDecision decide(CallPlan plan, String finalTranscript) {
        return decide(plan, finalTranscript, 0);
    }

    public CallPlanDecision decide(CallPlan plan, String finalTranscript, int priorUnknownCount) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(finalTranscript, "finalTranscript");
        if (priorUnknownCount < 0) {
            throw new IllegalArgumentException("priorUnknownCount must be >= 0");
        }

        String normalizedTranscript = normalize(finalTranscript);
        if (normalizedTranscript.isEmpty()) {
            return fallback(plan.fallbackPolicy(), priorUnknownCount);
        }

        List<CallPlanRule> factMatches = new ArrayList<>();
        for (CallPlanRule rule : plan.rules()) {
            if (matches(rule.utterances(), normalizedTranscript)) {
                factMatches.add(rule);
            }
        }

        List<CallPlanCompletionRule> completionMatches = new ArrayList<>();
        for (CallPlanCompletionRule rule : plan.completionRules()) {
            if (matches(rule.utterances(), normalizedTranscript)) {
                completionMatches.add(rule);
            }
        }

        List<CallPlanProposalRule> proposalMatches = new ArrayList<>();
        for (CallPlanProposalRule rule : plan.proposalRules()) {
            if (matches(rule.utterances(), normalizedTranscript)) {
                proposalMatches.add(rule);
            }
        }

        if (factMatches.size() + completionMatches.size() + proposalMatches.size() != 1) {
            return fallback(plan.fallbackPolicy(), priorUnknownCount);
        }

        if (completionMatches.size() == 1) {
            CallPlanCompletionRule rule = completionMatches.get(0);
            return CallPlanDecision.complete(rule.outcome(), rule.id());
        }

        if (proposalMatches.size() == 1) {
            CallPlanProposalRule rule = proposalMatches.get(0);
            return CallPlanDecision.proposal(rule.proposal(), rule.id());
        }

        CallPlanRule rule = factMatches.get(0);
        String authorizedValue = plan.task().authorizedFacts().get(rule.authorizedFactKey());
        if (authorizedValue == null) {
            return CallPlanDecision.takeOver(rule.id());
        }
        return CallPlanDecision.say(authorizedValue, rule.id());
    }

    private static boolean matches(Iterable<String> utterances, String normalizedTranscript) {
        for (String utterance : utterances) {
            if (normalize(utterance).equals(normalizedTranscript)) {
                return true;
            }
        }
        return false;
    }

    private static CallPlanDecision fallback(
        CallPlanFallbackPolicy policy,
        int priorUnknownCount
    ) {
        return switch (policy.actionFor(priorUnknownCount)) {
            case ASK_REPEAT -> CallPlanDecision.askRepeat();
            case TAKE_OVER -> CallPlanDecision.takeOver(null);
            case SAY, PROPOSAL, COMPLETE -> throw new IllegalStateException(
                "fallback policy must not produce speech, proposal, or completion"
            );
        };
    }

    private static String normalize(String value) {
        String source = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(source.length());
        source.codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)) {
                normalized.appendCodePoint(codePoint);
            } else {
                normalized.append(' ');
            }
        });
        return normalized.toString().trim().replaceAll("\\s+", " ");
    }
}
