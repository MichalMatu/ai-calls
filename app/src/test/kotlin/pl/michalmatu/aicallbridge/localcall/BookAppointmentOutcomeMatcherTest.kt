package pl.michalmatu.aicallbridge.localcall

import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPaymentMode
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.agent.MoneyAmount
import pl.michalmatu.aicallbridge.appointment.BookAppointmentTaskGraph
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphContext
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot

class BookAppointmentOutcomeMatcherTest {
    private val scheduledAt = ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, ZoneId.of("Europe/Warsaw"))
    private val price = MoneyAmount(BigDecimal("180.00"), "PLN")
    private val proposal = CallProposal(
        scheduledAt,
        price,
        CallPaymentMode.PRIVATE,
        "dr Alternatywna",
        "Centrum Medyczne",
    )

    @Test
    fun exactApprovedTermsMatch() {
        assertTrue(BookAppointmentOutcomeMatcher.matches(proposal, outcome(), snapshot()))
    }

    @Test
    fun timeAndGraphAppointmentMustMatchExactly() {
        assertFalse(
            BookAppointmentOutcomeMatcher.matches(
                proposal,
                outcome(scheduledAtOverride = scheduledAt.plusHours(1)),
                snapshot(),
            ),
        )
        assertFalse(
            BookAppointmentOutcomeMatcher.matches(
                proposal,
                outcome(),
                snapshot(scheduledAt.plusHours(1).toString()),
            ),
        )
    }

    @Test
    fun approvedPriceProviderAndLocationMustMatchExactly() {
        assertFalse(
            BookAppointmentOutcomeMatcher.matches(
                proposal,
                outcome(cost = MoneyAmount(BigDecimal("181.00"), "PLN")),
                snapshot(),
            ),
        )
        assertFalse(
            BookAppointmentOutcomeMatcher.matches(
                proposal,
                outcome(cost = MoneyAmount(BigDecimal("180.00"), "EUR")),
                snapshot(),
            ),
        )
        assertFalse(
            BookAppointmentOutcomeMatcher.matches(
                proposal,
                outcome(provider = "dr Inna"),
                snapshot(),
            ),
        )
        assertFalse(
            BookAppointmentOutcomeMatcher.matches(
                proposal,
                outcome(location = "Inna placowka"),
                snapshot(),
            ),
        )
    }

    @Test
    fun termsNotApprovedOnProposalDoNotBecomeNewCompletionRequirements() {
        val minimalProposal = CallProposal(scheduledAt, null, null, null, null)
        assertTrue(
            BookAppointmentOutcomeMatcher.matches(
                minimalProposal,
                outcome(),
                snapshot(),
            ),
        )
    }

    private fun snapshot(appointmentAt: String = scheduledAt.toString()) = TaskGraphSnapshot(
        graphVersion = 1,
        state = BookAppointmentTaskGraph.COMMITMENT,
        context = TaskGraphContext().with(
            BookAppointmentTaskGraph.APPOINTMENT_AT,
            TaskGraphSlotValue.Text(appointmentAt),
        ),
    )

    private fun outcome(
        scheduledAtOverride: ZonedDateTime = scheduledAt,
        provider: String = "dr Alternatywna",
        location: String = "Centrum Medyczne",
        cost: MoneyAmount = price,
    ) = CallOutcome(
        CallOutcomeStatus.SUCCESS,
        "Booked",
        scheduledAtOverride,
        provider,
        location,
        cost,
        "booking-match",
        null,
    )
}
