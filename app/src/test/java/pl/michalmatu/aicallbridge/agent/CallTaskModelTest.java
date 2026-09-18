package pl.michalmatu.aicallbridge.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public final class CallTaskModelTest {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Test
    public void workflowStatesMatchTelephoneAgentV1Contract() {
        assertEquals(
            List.of(
                CallWorkflowState.RESEARCHING,
                CallWorkflowState.READY_TO_DIAL,
                CallWorkflowState.DIALING,
                CallWorkflowState.ACTIVE_NEGOTIATION,
                CallWorkflowState.NEEDS_USER_DECISION,
                CallWorkflowState.COMPLETED,
                CallWorkflowState.FAILED
            ),
            List.of(CallWorkflowState.values())
        );
    }

    @Test
    public void hardConstraintsPreferencesAndAuthorizedFactsStayStructurallySeparate() {
        CallTimeWindow hardWindow = window(2026, 9, 21, 8, 0, 12, 0);
        CallTimeWindow preferredWindow = window(2026, 9, 21, 9, 30, 10, 30);
        CallConstraints constraints = new CallConstraints(
            List.of(hardWindow),
            new MoneyAmount(new BigDecimal("250.00"), "PLN"),
            EnumSet.of(CallPaymentMode.NFZ, CallPaymentMode.PRIVATE)
        );
        CallPreferences preferences = new CallPreferences(
            List.of(preferredWindow),
            List.of("Przychodnia Centrum"),
            List.of("Wrocław, Gaj")
        );
        CallTask task = new CallTask(
            "dermatolog we Wrocławiu",
            "umów wizytę",
            "konsultacja dermatologiczna",
            constraints,
            preferences,
            Map.of("full_name", "Jan Kowalski", "birth_year", "1990")
        );

        assertEquals(List.of(hardWindow), task.constraints().allowedTimeWindows());
        assertEquals(new BigDecimal("250.00"), task.constraints().maxPrice().amount());
        assertEquals(
            EnumSet.of(CallPaymentMode.NFZ, CallPaymentMode.PRIVATE),
            task.constraints().allowedPaymentModes()
        );
        assertEquals(List.of(preferredWindow), task.preferences().preferredTimeWindows());
        assertEquals(List.of("Przychodnia Centrum"), task.preferences().preferredProviders());
        assertEquals(List.of("Wrocław, Gaj"), task.preferences().preferredLocations());
        assertEquals("Jan Kowalski", task.authorizedFacts().get("full_name"));
    }

    @Test
    public void taskAndNestedCollectionsAreDefensivelyCopied() {
        List<CallTimeWindow> allowed = new ArrayList<>();
        allowed.add(window(2026, 9, 22, 10, 0, 11, 0));
        EnumSet<CallPaymentMode> modes = EnumSet.of(CallPaymentMode.INSURANCE);
        List<String> providers = new ArrayList<>(List.of("Provider A"));
        Map<String, String> facts = new HashMap<>();
        facts.put("policy_number", "ABC123");

        CallTask task = new CallTask(
            "clinic",
            "book",
            "consultation",
            new CallConstraints(allowed, null, modes),
            new CallPreferences(List.of(), providers, List.of()),
            facts
        );

        allowed.clear();
        modes.add(CallPaymentMode.PRIVATE);
        providers.add("Provider B");
        facts.put("policy_number", "CHANGED");

        assertEquals(1, task.constraints().allowedTimeWindows().size());
        assertEquals(EnumSet.of(CallPaymentMode.INSURANCE), task.constraints().allowedPaymentModes());
        assertEquals(List.of("Provider A"), task.preferences().preferredProviders());
        assertEquals("ABC123", task.authorizedFacts().get("policy_number"));

        assertThrows(
            UnsupportedOperationException.class,
            () -> task.constraints().allowedTimeWindows().clear()
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> task.constraints().allowedPaymentModes().add(CallPaymentMode.PRIVATE)
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> task.preferences().preferredProviders().add("Provider C")
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> task.authorizedFacts().put("x", "y")
        );
    }

    @Test
    public void emptyConstraintCollectionsMeanNoHardRestriction() {
        CallConstraints constraints = CallConstraints.unconstrained();

        assertTrue(constraints.allowedTimeWindows().isEmpty());
        assertNull(constraints.maxPrice());
        assertTrue(constraints.allowedPaymentModes().isEmpty());
        assertFalse(constraints.hasHardRestrictions());
    }

    @Test
    public void validationRejectsAmbiguousOrInvalidAuthorityData() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new CallTask(
                " ",
                "book",
                "consultation",
                CallConstraints.unconstrained(),
                CallPreferences.none(),
                Map.of()
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new CallTask(
                "clinic",
                "book",
                "consultation",
                CallConstraints.unconstrained(),
                CallPreferences.none(),
                Map.of(" ", "secret")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new CallTask(
                "clinic",
                "book",
                "consultation",
                CallConstraints.unconstrained(),
                CallPreferences.none(),
                Map.of("name", " ")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MoneyAmount(new BigDecimal("-0.01"), "PLN")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MoneyAmount(new BigDecimal("10.00"), "not-a-currency")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new CallTimeWindow(
                ZonedDateTime.of(2026, 9, 21, 12, 0, 0, 0, WARSAW),
                ZonedDateTime.of(2026, 9, 21, 12, 0, 0, 0, WARSAW)
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new CallPreferences(List.of(), List.of(" "), List.of())
        );
    }

    @Test
    public void outcomeKeepsStructuredResultWithoutChangingTaskAuthority() {
        CallOutcome outcome = new CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "Wizyta zarezerwowana",
            ZonedDateTime.of(2026, 9, 23, 10, 30, 0, 0, WARSAW),
            "Przychodnia Centrum",
            "Wrocław, ul. Testowa 1",
            new MoneyAmount(new BigDecimal("180.00"), "PLN"),
            "REF-123",
            "Przyjdź 10 minut wcześniej"
        );

        assertEquals(CallOutcomeStatus.SUCCESS, outcome.status());
        assertEquals("Przychodnia Centrum", outcome.provider());
        assertEquals("PLN", outcome.cost().currencyCode());
        assertEquals("REF-123", outcome.bookingReference());
    }

    private static CallTimeWindow window(
        int year,
        int month,
        int day,
        int startHour,
        int startMinute,
        int endHour,
        int endMinute
    ) {
        return new CallTimeWindow(
            ZonedDateTime.of(year, month, day, startHour, startMinute, 0, 0, WARSAW),
            ZonedDateTime.of(year, month, day, endHour, endMinute, 0, 0, WARSAW)
        );
    }
}
