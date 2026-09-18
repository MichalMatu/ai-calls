package pl.michalmatu.aicallbridge.agent;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic authority/confirmation gate. Counterparty content is evaluated only as proposal
 * data and can never widen the user's constraints or authorized facts.
 */
public final class CallConfirmationPolicy {
    public CallPolicyDecision evaluate(CallTask task, CallProposal proposal) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(proposal, "proposal");

        List<CallPolicyReason> reasons = new ArrayList<>();
        evaluateHardConstraints(task.constraints(), proposal, reasons);
        evaluatePreferences(task.preferences(), proposal, reasons);

        if (reasons.isEmpty()) {
            return new CallPolicyDecision(CallPolicyAction.AUTONOMOUSLY_ALLOWED, List.of());
        }
        return new CallPolicyDecision(CallPolicyAction.NEEDS_USER_DECISION, reasons);
    }

    private static void evaluateHardConstraints(
        CallConstraints constraints,
        CallProposal proposal,
        List<CallPolicyReason> reasons
    ) {
        if (!constraints.allowedTimeWindows().isEmpty()) {
            if (proposal.scheduledAt() == null) {
                reasons.add(CallPolicyReason.TIME_UNKNOWN);
            } else if (!matchesAnyWindow(proposal.scheduledAt(), constraints.allowedTimeWindows())) {
                reasons.add(CallPolicyReason.TIME_OUTSIDE_ALLOWED);
            }
        }

        MoneyAmount maxPrice = constraints.maxPrice();
        if (maxPrice != null) {
            MoneyAmount price = proposal.price();
            if (price == null) {
                reasons.add(CallPolicyReason.PRICE_UNKNOWN);
            } else if (!maxPrice.currencyCode().equals(price.currencyCode())) {
                reasons.add(CallPolicyReason.PRICE_CURRENCY_MISMATCH);
            } else if (price.amount().compareTo(maxPrice.amount()) > 0) {
                reasons.add(CallPolicyReason.PRICE_ABOVE_MAX);
            }
        }

        if (!constraints.allowedPaymentModes().isEmpty()) {
            if (proposal.paymentMode() == null) {
                reasons.add(CallPolicyReason.PAYMENT_MODE_UNKNOWN);
            } else if (!constraints.allowedPaymentModes().contains(proposal.paymentMode())) {
                reasons.add(CallPolicyReason.PAYMENT_MODE_NOT_ALLOWED);
            }
        }
    }

    private static void evaluatePreferences(
        CallPreferences preferences,
        CallProposal proposal,
        List<CallPolicyReason> reasons
    ) {
        if (!preferences.preferredTimeWindows().isEmpty()) {
            if (proposal.scheduledAt() == null) {
                reasons.add(CallPolicyReason.PREFERRED_TIME_UNKNOWN);
            } else if (!matchesAnyWindow(proposal.scheduledAt(), preferences.preferredTimeWindows())) {
                reasons.add(CallPolicyReason.PREFERRED_TIME_MISSED);
            }
        }

        if (!preferences.preferredProviders().isEmpty()) {
            if (proposal.provider() == null) {
                reasons.add(CallPolicyReason.PREFERRED_PROVIDER_UNKNOWN);
            } else if (!containsNormalized(preferences.preferredProviders(), proposal.provider())) {
                reasons.add(CallPolicyReason.PREFERRED_PROVIDER_MISSED);
            }
        }

        if (!preferences.preferredLocations().isEmpty()) {
            if (proposal.location() == null) {
                reasons.add(CallPolicyReason.PREFERRED_LOCATION_UNKNOWN);
            } else if (!containsNormalized(preferences.preferredLocations(), proposal.location())) {
                reasons.add(CallPolicyReason.PREFERRED_LOCATION_MISSED);
            }
        }
    }

    private static boolean matchesAnyWindow(
        ZonedDateTime candidate,
        List<CallTimeWindow> windows
    ) {
        for (CallTimeWindow window : windows) {
            if (
                !candidate.toInstant().isBefore(window.startInclusive().toInstant())
                    && candidate.toInstant().isBefore(window.endExclusive().toInstant())
            ) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNormalized(List<String> allowed, String candidate) {
        String normalizedCandidate = normalize(candidate);
        for (String value : allowed) {
            if (normalize(value).equals(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
