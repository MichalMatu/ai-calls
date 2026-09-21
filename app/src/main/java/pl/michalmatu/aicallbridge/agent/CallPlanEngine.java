package pl.michalmatu.aicallbridge.agent;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Deterministic exact-rule classifier for final transcripts. */
public final class CallPlanEngine {
    public CallPlanDecision decide(CallPlan plan, String finalTranscript) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(finalTranscript, "finalTranscript");

        String normalizedTranscript = normalize(finalTranscript);
        if (normalizedTranscript.isEmpty()) {
            return fallback(plan.fallback());
        }

        List<CallPlanRule> matches = new ArrayList<>();
        for (CallPlanRule rule : plan.rules()) {
            if (matches(rule, normalizedTranscript)) {
                matches.add(rule);
            }
        }

        if (matches.size() != 1) {
            return fallback(plan.fallback());
        }

        CallPlanRule rule = matches.get(0);
        String authorizedValue = plan.task().authorizedFacts().get(rule.authorizedFactKey());
        if (authorizedValue == null) {
            return CallPlanDecision.takeOver(rule.id());
        }
        return CallPlanDecision.say(authorizedValue, rule.id());
    }

    private static boolean matches(CallPlanRule rule, String normalizedTranscript) {
        for (String utterance : rule.utterances()) {
            if (normalize(utterance).equals(normalizedTranscript)) {
                return true;
            }
        }
        return false;
    }

    private static CallPlanDecision fallback(CallPlanFallback fallback) {
        return switch (fallback) {
            case ASK_REPEAT -> CallPlanDecision.askRepeat();
            case TAKE_OVER -> CallPlanDecision.takeOver(null);
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
