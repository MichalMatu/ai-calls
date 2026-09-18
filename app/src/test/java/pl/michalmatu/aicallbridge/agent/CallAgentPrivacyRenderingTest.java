package pl.michalmatu.aicallbridge.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class CallAgentPrivacyRenderingTest {
    @Test
    public void taskRenderingDoesNotExposeUserFactsOrTargetText() {
        CallTask task = sensitiveTask();

        String rendered = task.toString();

        assertFalse(rendered.contains("Jan Kowalski"));
        assertFalse(rendered.contains("90010112345"));
        assertFalse(rendered.contains("Sensitive dermatology clinic"));
        assertFalse(rendered.contains("Provider Secret"));
        assertTrue(rendered.contains("REDACTED"));
    }

    @Test
    public void proposalOutcomeAndWorkflowSnapshotRenderingStayRedacted() {
        CallProposal proposal = new CallProposal(
            ZonedDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneId.of("Europe/Warsaw")),
            new MoneyAmount(new BigDecimal("149.99"), "PLN"),
            CallPaymentMode.PRIVATE,
            "Provider Secret",
            "Secret Street 1"
        );
        CallOutcome outcome = new CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "Booked for Jan Kowalski",
            proposal.scheduledAt(),
            "Provider Secret",
            "Secret Street 1",
            proposal.price(),
            "BOOKING-SECRET-123",
            "Bring private document 9988"
        );
        CallResolvedTarget target = new CallResolvedTarget("Provider Secret", "+48123456789");
        CallWorkflowSnapshot snapshot = new CallWorkflowSnapshot(
            sensitiveTask(),
            CallWorkflowState.COMPLETED,
            target,
            null,
            null,
            outcome,
            null
        );

        String combined = proposal + "\n" + outcome + "\n" + target + "\n" + snapshot;

        for (String secret : List.of(
            "Jan Kowalski",
            "Provider Secret",
            "Secret Street 1",
            "BOOKING-SECRET-123",
            "+48123456789",
            "9988"
        )) {
            assertFalse("rendered secret: " + secret, combined.contains(secret));
        }
        assertTrue(combined.contains("REDACTED"));
    }

    private static CallTask sensitiveTask() {
        return new CallTask(
            "Sensitive dermatology clinic",
            "book",
            "consultation",
            new CallConstraints(
                List.of(),
                new MoneyAmount(new BigDecimal("200.00"), "PLN"),
                Set.of(CallPaymentMode.PRIVATE)
            ),
            new CallPreferences(List.of(), List.of("Provider Secret"), List.of("Secret Street 1")),
            Map.of("full_name", "Jan Kowalski", "pesel", "90010112345")
        );
    }
}
