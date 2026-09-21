package pl.michalmatu.aicallbridge.agent;

/**
 * Immutable bounded fallback policy for unknown or ambiguous final transcripts.
 *
 * <p>The engine remains stateless: callers provide the number of earlier unknown-turn
 * fallbacks when asking for a decision.</p>
 */
public record CallPlanFallbackPolicy(int repeatLimit) {
    public CallPlanFallbackPolicy {
        if (repeatLimit < 0) {
            throw new IllegalArgumentException("repeatLimit must be >= 0");
        }
    }

    public static CallPlanFallbackPolicy repeatThenTakeOver(int repeatLimit) {
        return new CallPlanFallbackPolicy(repeatLimit);
    }

    public static CallPlanFallbackPolicy takeOverImmediately() {
        return new CallPlanFallbackPolicy(0);
    }

    public CallPlanAction actionFor(int priorUnknownCount) {
        if (priorUnknownCount < 0) {
            throw new IllegalArgumentException("priorUnknownCount must be >= 0");
        }
        return priorUnknownCount < repeatLimit
            ? CallPlanAction.ASK_REPEAT
            : CallPlanAction.TAKE_OVER;
    }

    static CallPlanFallbackPolicy fromLegacy(CallPlanFallback fallback) {
        return switch (java.util.Objects.requireNonNull(fallback, "fallback")) {
            case ASK_REPEAT -> repeatThenTakeOver(1);
            case TAKE_OVER -> takeOverImmediately();
        };
    }

    CallPlanFallback initialFallback() {
        return repeatLimit > 0 ? CallPlanFallback.ASK_REPEAT : CallPlanFallback.TAKE_OVER;
    }
}
