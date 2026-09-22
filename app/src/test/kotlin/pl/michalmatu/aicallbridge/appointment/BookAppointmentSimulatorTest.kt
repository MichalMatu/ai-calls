package pl.michalmatu.aicallbridge.appointment

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
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

class BookAppointmentSimulatorTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val allowedWindow = CallTimeWindow(
        ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
        ZonedDateTime.of(2026, 9, 24, 18, 0, 0, 0, zone),
    )
    private val target = CallResolvedTarget("Example clinic", "+48123456789")

    @Test
    fun `happy path books acceptable first offer and replay matches`() {
        val simulator = simulator(task())

        val result = simulator.run(
            listOf(BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:30")),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertEquals(BookAppointmentTaskGraph.COMPLETE, result.finalSnapshot.state)
        assertEquals(
            "2026-09-24T17:30+02:00[Europe/Warsaw]",
            (result.finalSnapshot.context[BookAppointmentTaskGraph.APPOINTMENT_AT] as pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue.Text).value,
        )
        assertTrue(result.replayVerified)
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.COMMITMENT_CONSUMED })
    }

    @Test
    fun `unacceptable first offer is not committed and alternative can succeed`() {
        val simulator = simulator(task())

        val result = simulator.run(
            listOf(
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 20:00"),
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:00"),
            ),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.OFFER_REJECTED })
        assertEquals(1, result.evidence.count { it.type == BookAppointmentEvidenceType.PROPOSAL_CREATED })
        assertFalse(result.eventRecords.any { record ->
            record.event.candidates.values.any { it == pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue.Text("2026-09-24T20:00+02:00[Europe/Warsaw]") }
        })
    }

    @Test
    fun `ambiguous time requests clarification without mutating graph slot`() {
        val simulator = simulator(task())

        val result = simulator.run(
            listOf(
                BookAppointmentSimulationStep.ReceptionistOffer("Mamy termin 2026-09-24 po południu"),
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 16:30"),
            ),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.CLARIFICATION_REQUIRED })
        assertEquals(1, result.evidence.count { it.type == BookAppointmentEvidenceType.PROPOSAL_CREATED })
    }

    @Test
    fun `no availability completes with structured negative business outcome`() {
        val result = simulator(task()).run(
            listOf(BookAppointmentSimulationStep.NoAvailability),
        )

        assertEquals(BookAppointmentOutcome.NO_AVAILABILITY, result.outcome)
        assertEquals(BookAppointmentTaskGraph.COMPLETE, result.finalSnapshot.state)
        assertTrue(result.replayVerified)
    }

    @Test
    fun `soft preference deviation requires user confirmation and rejection resumes negotiation`() {
        val preferred = CallTimeWindow(
            ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 9, 24, 17, 0, 0, 0, zone),
        )
        val preferredTask = task(CallPreferences(listOf(preferred), emptyList(), emptyList()))

        val result = simulator(preferredTask).run(
            listOf(
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:30"),
                BookAppointmentSimulationStep.UserRejectsProposal,
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 16:30"),
            ),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.USER_CONFIRMATION_REQUIRED })
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.USER_REJECTED_PROPOSAL })
    }

    @Test
    fun `user confirmation authorizes only the concrete pending proposal before commitment`() {
        val preferred = CallTimeWindow(
            ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 9, 24, 17, 0, 0, 0, zone),
        )
        val preferredTask = task(CallPreferences(listOf(preferred), emptyList(), emptyList()))

        val result = simulator(preferredTask).run(
            listOf(
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:30"),
                BookAppointmentSimulationStep.UserConfirmsProposal,
            ),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        val required = result.evidence.indexOfFirst { it.type == BookAppointmentEvidenceType.USER_CONFIRMATION_REQUIRED }
        val confirmed = result.evidence.indexOfFirst { it.type == BookAppointmentEvidenceType.USER_CONFIRMED_PROPOSAL }
        val committed = result.evidence.indexOfFirst { it.type == BookAppointmentEvidenceType.COMMITMENT_CONSUMED }
        assertTrue(required >= 0 && confirmed > required && committed > confirmed)
    }

    @Test
    fun `identity requests are policy decisions and never identity plaintext evidence`() {
        val task = task()
        val allowedState = BookAppointmentTaskGraph.WAITING_OFFER
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 11L,
            availableFields = setOf(
                IdentityFieldId.FIRST_NAME,
                IdentityFieldId.PHONE,
                IdentityFieldId.EMAIL,
                IdentityFieldId.PESEL,
            ),
            authorizedFields = setOf(IdentityFieldId.FIRST_NAME, IdentityFieldId.PHONE, IdentityFieldId.PESEL),
            highSensitivityApprovedFields = emptySet(),
            allowedDisclosureStates = mapOf(
                IdentityFieldId.FIRST_NAME to setOf(allowedState),
                IdentityFieldId.PHONE to setOf(allowedState),
                IdentityFieldId.PESEL to setOf(allowedState),
            ),
        )
        val simulator = BookAppointmentSimulator(task, target, facts, zone)

        val result = simulator.run(
            listOf(
                BookAppointmentSimulationStep.IdentityRequest(IdentityFieldId.FIRST_NAME),
                BookAppointmentSimulationStep.IdentityRequest(IdentityFieldId.PHONE),
                BookAppointmentSimulationStep.IdentityRequest(IdentityFieldId.PESEL),
                BookAppointmentSimulationStep.IdentityRequest(IdentityFieldId.EMAIL),
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:00"),
            ),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertEquals(2, result.evidence.count { it.type == BookAppointmentEvidenceType.DISCLOSURE_ALLOWED })
        assertEquals(2, result.evidence.count { it.type == BookAppointmentEvidenceType.DISCLOSURE_ASK_USER })
        assertTrue(result.evidence.all { evidence ->
            IdentityFieldId.entries.none { field -> evidence.detail?.contains("value=") == true || evidence.detail?.contains("12345678901") == true }
        })
    }

    @Test
    fun `cancel takeover harmless question and noisy paraphrase are deterministic`() {
        val cancel = simulator(task()).run(listOf(BookAppointmentSimulationStep.Cancel))
        assertEquals(BookAppointmentOutcome.CANCELLED, cancel.outcome)
        assertEquals(BookAppointmentTaskGraph.CANCELLED, cancel.finalSnapshot.state)

        val takeover = simulator(task()).run(listOf(BookAppointmentSimulationStep.TakeOver))
        assertEquals(BookAppointmentOutcome.TAKE_OVER, takeover.outcome)
        assertEquals(BookAppointmentTaskGraph.TAKE_OVER, takeover.finalSnapshot.state)

        val noisy = simulator(task()).run(
            listOf(
                BookAppointmentSimulationStep.UnexpectedHarmlessQuestion,
                BookAppointmentSimulationStep.ReceptionistOffer("wolny termin 24.09.2026, godzina 17:30"),
            ),
        )
        assertEquals(BookAppointmentOutcome.BOOKED, noisy.outcome)
        assertTrue(noisy.evidence.any { it.type == BookAppointmentEvidenceType.HARMLESS_QUESTION })
        assertTrue(noisy.evidence.any { it.type == BookAppointmentEvidenceType.OFFER_PARSED })
    }

    private fun task(preferences: CallPreferences = CallPreferences.none()): CallTask = CallTask(
        "Example clinic",
        "book appointment",
        "appointment",
        CallConstraints(listOf(allowedWindow), null, emptySet()),
        preferences,
        emptyMap(),
    )

    private fun simulator(task: CallTask): BookAppointmentSimulator {
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 11L,
            availableFields = emptySet(),
            authorizedFields = emptySet(),
        )
        return BookAppointmentSimulator(task, target, facts, zone)
    }
}
