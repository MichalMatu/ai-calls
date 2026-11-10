package pl.michalmatu.aicallbridge.appointment

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallTimeWindow
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

/** Synthetic E2E: offer -> constraints -> scoped facts -> review, no dial or commitment. */
class BookAppointmentMvpReviewTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val target = CallResolvedTarget("Synthetic clinic", "+48000000000")
    private val timeWindow = CallTimeWindow(
        ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
        ZonedDateTime.of(2026, 9, 24, 18, 0, 0, 0, zone),
    )
    private val task = CallTask("Synthetic clinic", "book appointment", "appointment",
        CallConstraints(listOf(timeWindow), null, emptySet()), CallPreferences.none(), emptyMap())
    private fun sim(
        provider: SyntheticBookingAdapter = SyntheticBookingAdapter(),
        facts: AuthorizedFactSnapshot = AuthorizedFactSnapshot(task, target, 7L, emptySet(), emptySet()),
    ) = BookAppointmentSimulator(task, target, facts, zone, bookingProvider = provider,
        executionMode = BookAppointmentExecutionMode.REVIEW_ONLY)

    @Test fun approvedOfferAndScopedFactsStopBeforeAnyProviderCommitment() {
        var availabilityCalls = 0
        val provider = SyntheticBookingAdapter(availability = { availabilityCalls++; true })
        val facts = AuthorizedFactSnapshot(task, target, 7L,
            setOf(IdentityFieldId.FIRST_NAME, IdentityFieldId.EMAIL),
            setOf(IdentityFieldId.FIRST_NAME),
            allowedDisclosureStates = mapOf(IdentityFieldId.FIRST_NAME to
                setOf(BookAppointmentTaskGraph.WAITING_OFFER)))
        val result = sim(provider, facts).run(listOf(
            BookAppointmentSimulationStep.IdentityRequest(IdentityFieldId.FIRST_NAME),
            BookAppointmentSimulationStep.IdentityRequest(IdentityFieldId.EMAIL),
            BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:30"),
            BookAppointmentSimulationStep.UserConfirmsProposal,
        ))
        assertEquals(BookAppointmentOutcome.REVIEW_REQUIRED, result.outcome)
        assertEquals(BookAppointmentTaskGraph.CONFIRMATION, result.finalSnapshot.state)
        assertTrue(result.replayVerified)
        assertEquals(0, availabilityCalls)
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.USER_REVIEW_REQUIRED })
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.DISCLOSURE_ALLOWED })
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.DISCLOSURE_ASK_USER })
        assertFalse(result.evidence.any { it.type == BookAppointmentEvidenceType.COMMITMENT_CONSUMED ||
            it.type == BookAppointmentEvidenceType.PROVIDER_CONFIRMED })
        val review = result.review()
        assertEquals(ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, zone), review.proposedAt)
        assertTrue(review.requiresUserReview)
        assertTrue(review.syntheticOnly)
        assertFalse(review.externalBookingConfirmed)
    }

    @Test fun invalidOfferOrUnavailableOrTakeoverCannotPretendBooking() {
        val invalid = sim().run(listOf(BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 20:00")))
        assertEquals(BookAppointmentOutcome.INCOMPLETE, invalid.outcome)
        assertNull(invalid.review().proposedAt)
        assertTrue(invalid.replayVerified)
        for (step in listOf(BookAppointmentSimulationStep.NoAvailability,
            BookAppointmentSimulationStep.Cancel, BookAppointmentSimulationStep.TakeOver)) {
            val result = sim().run(listOf(step))
            assertFalse(result.review().requiresUserReview)
            assertFalse(result.review().externalBookingConfirmed)
            assertTrue(result.replayVerified)
        }
    }

    @Test fun existingSyntheticCommitIsNeverExposedAsRealBookingReceipt() {
        val result = BookAppointmentSimulator(task, target,
            AuthorizedFactSnapshot(task, target, 7L, emptySet(), emptySet()), zone)
            .run(listOf(BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:00")))
        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertFalse(result.review().externalBookingConfirmed)
    }
}
