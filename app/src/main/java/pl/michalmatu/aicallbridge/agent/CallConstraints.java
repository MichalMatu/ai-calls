package pl.michalmatu.aicallbridge.agent;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Explicit hard limits that authorize autonomous action only inside these bounds. */
public record CallConstraints(
    List<CallTimeWindow> allowedTimeWindows,
    MoneyAmount maxPrice,
    Set<CallPaymentMode> allowedPaymentModes
) {
    public CallConstraints {
        Objects.requireNonNull(allowedTimeWindows, "allowedTimeWindows");
        Objects.requireNonNull(allowedPaymentModes, "allowedPaymentModes");
        allowedTimeWindows = List.copyOf(allowedTimeWindows);
        allowedPaymentModes = Set.copyOf(allowedPaymentModes);
    }

    /** Empty collections and a null maxPrice intentionally mean no hard restriction. */
    public static CallConstraints unconstrained() {
        return new CallConstraints(List.of(), null, Set.of());
    }

    public boolean hasHardRestrictions() {
        return !allowedTimeWindows.isEmpty() || maxPrice != null || !allowedPaymentModes.isEmpty();
    }
}
