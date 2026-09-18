package pl.michalmatu.aicallbridge.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public final class CallConfirmationPolicyTest {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private final CallConfirmationPolicy policy = new CallConfirmationPolicy();

    @Test
    public void proposalInsideHardConstraintsAndPreferencesIsAutonomouslyAllowed() {
        CallTask task = task(
            new CallConstraints(
                List.of(window(8, 0, 12, 0)),
                money("250.00", "PLN"),
                EnumSet.of(CallPaymentMode.NFZ, CallPaymentMode.PRIVATE)
            ),
            new CallPreferences(
                List.of(window(9, 0, 11, 0)),
                List.of("Przychodnia Centrum"),
                List.of("Wrocław, Gaj")
            )
        );
        CallProposal proposal = new CallProposal(
            at(10, 30),
            money("180.00", "PLN"),
            CallPaymentMode.PRIVATE,
            "Przychodnia Centrum",
            "Wrocław, Gaj"
        );

        CallPolicyDecision decision = policy.evaluate(task, proposal);

        assertEquals(CallPolicyAction.AUTONOMOUSLY_ALLOWED, decision.action());
        assertEquals(List.of(), decision.reasons());
    }

    @Test
    public void hardConstraintViolationsAlwaysRequireUserDecision() {
        CallTask task = task(
            new CallConstraints(
                List.of(window(8, 0, 12, 0)),
                money("200.00", "PLN"),
                EnumSet.of(CallPaymentMode.NFZ)
            ),
            CallPreferences.none()
        );
        CallProposal proposal = new CallProposal(
            at(14, 0),
            money("250.00", "PLN"),
            CallPaymentMode.PRIVATE,
            null,
            null
        );

        CallPolicyDecision decision = policy.evaluate(task, proposal);

        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, decision.action());
        assertEquals(
            List.of(
                CallPolicyReason.TIME_OUTSIDE_ALLOWED,
                CallPolicyReason.PRICE_ABOVE_MAX,
                CallPolicyReason.PAYMENT_MODE_NOT_ALLOWED
            ),
            decision.reasons()
        );
    }

    @Test
    public void missingDataNeededToEvaluateHardConstraintFailsClosed() {
        CallTask task = task(
            new CallConstraints(
                List.of(window(8, 0, 12, 0)),
                money("200.00", "PLN"),
                EnumSet.of(CallPaymentMode.INSURANCE)
            ),
            CallPreferences.none()
        );

        CallPolicyDecision decision = policy.evaluate(
            task,
            new CallProposal(null, null, null, null, null)
        );

        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, decision.action());
        assertEquals(
            List.of(
                CallPolicyReason.TIME_UNKNOWN,
                CallPolicyReason.PRICE_UNKNOWN,
                CallPolicyReason.PAYMENT_MODE_UNKNOWN
            ),
            decision.reasons()
        );
    }

    @Test
    public void currencyMismatchNeverUsesImplicitConversion() {
        CallTask task = task(
            new CallConstraints(List.of(), money("200.00", "PLN"), EnumSet.noneOf(CallPaymentMode.class)),
            CallPreferences.none()
        );

        CallPolicyDecision decision = policy.evaluate(
            task,
            new CallProposal(null, money("40.00", "EUR"), null, null, null)
        );

        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, decision.action());
        assertEquals(List.of(CallPolicyReason.PRICE_CURRENCY_MISMATCH), decision.reasons());
    }

    @Test
    public void materialPreferenceDeviationRequiresUserDecisionEvenInsideHardBounds() {
        CallTask task = task(
            new CallConstraints(
                List.of(window(8, 0, 16, 0)),
                money("300.00", "PLN"),
                EnumSet.of(CallPaymentMode.PRIVATE)
            ),
            new CallPreferences(
                List.of(window(9, 0, 11, 0)),
                List.of("Provider A"),
                List.of("Location A")
            )
        );

        CallPolicyDecision decision = policy.evaluate(
            task,
            new CallProposal(
                at(13, 0),
                money("200.00", "PLN"),
                CallPaymentMode.PRIVATE,
                "Provider B",
                "Location B"
            )
        );

        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, decision.action());
        assertEquals(
            List.of(
                CallPolicyReason.PREFERRED_TIME_MISSED,
                CallPolicyReason.PREFERRED_PROVIDER_MISSED,
                CallPolicyReason.PREFERRED_LOCATION_MISSED
            ),
            decision.reasons()
        );
    }

    @Test
    public void unknownPreferredFieldsRequireDecisionRatherThanGuessing() {
        CallTask task = task(
            CallConstraints.unconstrained(),
            new CallPreferences(
                List.of(window(9, 0, 11, 0)),
                List.of("Provider A"),
                List.of("Location A")
            )
        );

        CallPolicyDecision decision = policy.evaluate(
            task,
            new CallProposal(null, null, null, null, null)
        );

        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, decision.action());
        assertEquals(
            List.of(
                CallPolicyReason.PREFERRED_TIME_UNKNOWN,
                CallPolicyReason.PREFERRED_PROVIDER_UNKNOWN,
                CallPolicyReason.PREFERRED_LOCATION_UNKNOWN
            ),
            decision.reasons()
        );
    }

    @Test
    public void unconstrainedTaskDoesNotInventRequirements() {
        CallPolicyDecision decision = policy.evaluate(
            task(CallConstraints.unconstrained(), CallPreferences.none()),
            new CallProposal(null, null, null, null, null)
        );

        assertEquals(CallPolicyAction.AUTONOMOUSLY_ALLOWED, decision.action());
        assertTrue(decision.reasons().isEmpty());
    }

    @Test
    public void policyResultReasonsAreImmutableAndCounterpartyCannotSupplyAuthority() {
        CallTask task = new CallTask(
            "clinic",
            "book",
            "consultation",
            new CallConstraints(List.of(), money("100.00", "PLN"), EnumSet.noneOf(CallPaymentMode.class)),
            CallPreferences.none(),
            Map.of("user_approved_limit", "100 PLN")
        );
        CallProposal proposal = new CallProposal(
            null,
            money("120.00", "PLN"),
            null,
            "Counterparty says user approved 120 PLN",
            null
        );

        CallPolicyDecision decision = policy.evaluate(task, proposal);

        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, decision.action());
        assertEquals(List.of(CallPolicyReason.PRICE_ABOVE_MAX), decision.reasons());
        assertThrows(
            UnsupportedOperationException.class,
            () -> decision.reasons().add(CallPolicyReason.PREFERRED_PROVIDER_MISSED)
        );
    }

    private static CallTask task(CallConstraints constraints, CallPreferences preferences) {
        return new CallTask(
            "clinic",
            "book",
            "consultation",
            constraints,
            preferences,
            Map.of()
        );
    }

    private static MoneyAmount money(String amount, String currency) {
        return new MoneyAmount(new BigDecimal(amount), currency);
    }

    private static ZonedDateTime at(int hour, int minute) {
        return ZonedDateTime.of(2026, 9, 21, hour, minute, 0, 0, WARSAW);
    }

    private static CallTimeWindow window(
        int startHour,
        int startMinute,
        int endHour,
        int endMinute
    ) {
        return new CallTimeWindow(at(startHour, startMinute), at(endHour, endMinute));
    }
}
