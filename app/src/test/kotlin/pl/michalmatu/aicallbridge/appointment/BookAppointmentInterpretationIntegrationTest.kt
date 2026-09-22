package pl.michalmatu.aicallbridge.appointment

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallTimeWindow
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue

class BookAppointmentInterpretationIntegrationTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val referenceDate = LocalDate.of(2026, 9, 22)
    private val target = CallResolvedTarget("Example clinic", "+48123456789")
    private val task = CallTask(
        "Example clinic",
        "book appointment",
        "appointment",
        CallConstraints(
            listOf(
                CallTimeWindow(
                    ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
                    ZonedDateTime.of(2026, 9, 24, 18, 0, 0, 0, zone),
                ),
            ),
            null,
            emptySet(),
        ),
        CallPreferences.none(),
        emptyMap(),
    )
    private val facts = AuthorizedFactSnapshot(
        task = task,
        target = target,
        generation = 11L,
        availableFields = emptySet(),
        authorizedFields = emptySet(),
    )

    @Test
    fun `simulator uses shared interpreter for explicit-reference weekday offer`() {
        val result = BookAppointmentSimulator(
            task = task,
            target = target,
            authorizedFacts = facts,
            zone = zone,
            referenceDate = referenceDate,
        ).run(
            listOf(BookAppointmentSimulationStep.ReceptionistOffer("w czwartek o 17:00")),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertEquals(
            TaskGraphSlotValue.Text("2026-09-24T17:00+02:00[Europe/Warsaw]"),
            result.finalSnapshot.context[BookAppointmentTaskGraph.APPOINTMENT_AT],
        )
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.OFFER_PARSED })
    }

    @Test
    fun `relative offer fails closed when simulator has no explicit reference date`() {
        val result = BookAppointmentSimulator(
            task = task,
            target = target,
            authorizedFacts = facts,
            zone = zone,
        ).run(
            listOf(BookAppointmentSimulationStep.ReceptionistOffer("jutro o 17:00")),
        )

        assertEquals(BookAppointmentOutcome.INCOMPLETE, result.outcome)
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.CLARIFICATION_REQUIRED })
        assertFalse(result.finalSnapshot.context.contains(BookAppointmentTaskGraph.APPOINTMENT_AT))
    }
}
