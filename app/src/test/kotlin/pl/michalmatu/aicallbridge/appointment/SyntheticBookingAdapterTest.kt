package pl.michalmatu.aicallbridge.appointment

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.*
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot

class SyntheticBookingAdapterTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun proposal(hour: Int = 11) = CallProposal(
        ZonedDateTime.of(2026, 11, 11, hour, 0, 0, 0, zone),
        null, null, "Synthetic clinic", null,
    )
    private fun consumed(p: CallProposal): CallCommitmentConsumptionEvidence {
        val gate = CallCommitmentGate { "synthetic-permit" }
        val token = gate.authorize(p)
        return CallCommitmentConsumptionEvidence(gate.consume(token.value).getOrThrow())
    }

    @Test fun idempotentHoldKeepsOriginalTtlAndRejectsChangedTerms() {
        val provider = SyntheticBookingAdapter()
        val first = provider.hold(proposal(), "key", 1_000L).getOrThrow()
        assertSame(first, provider.hold(proposal(), "key", 50_000L).getOrThrow())
        assertEquals(1_000L, first.expiresAtMs)
        assertTrue(provider.hold(proposal(12), "key", 1_000L).isFailure)
    }
    @Test fun exactConsumptionConfirmsOnceWithIndependentReadback() {
        val provider = SyntheticBookingAdapter()
        val p = proposal()
        val hold = provider.hold(p, "key", 10_000L).getOrThrow()
        val permit = consumed(p)
        val receipt = provider.confirm(hold, "key", permit).getOrThrow()
        assertSame(receipt, provider.confirm(hold, "key", permit).getOrThrow())
        assertSame(receipt, provider.readBack(receipt.bookingId))
        assertNull(provider.readBack("unknown"))
        assertFalse(hold.toString().contains("Synthetic clinic"))
        assertFalse(receipt.toString().contains("Synthetic clinic"))
    }
    @Test fun wrongScopeAndExpiredHoldNeverCreateReceipt() {
        var now = 100L
        val provider = SyntheticBookingAdapter(clockMs = { now })
        val p = proposal()
        val hold = provider.hold(p, "key", 10L).getOrThrow()
        assertTrue(provider.confirm(hold, "wrong", consumed(p)).isFailure)
        assertTrue(provider.confirm(hold, "key", consumed(proposal(12))).isFailure)
        now = 110L
        assertTrue(provider.confirm(hold, "key", consumed(p)).isFailure)
        assertTrue(provider.hold(p, "key", 100L).isFailure)
        assertNull(provider.readBack("synthetic-booking-" + hold.id))
    }
    @Test fun failedProviderCanRetryBeforeExpiryWithoutPhantomBooking() {
        var ready = false
        val provider = SyntheticBookingAdapter(confirmReady = { ready })
        val p = proposal()
        val hold = provider.hold(p, "key", 10_000L).getOrThrow()
        val evidence = consumed(p)
        assertTrue(provider.confirm(hold, "key", evidence).isFailure)
        assertNull(provider.readBack("synthetic-booking-" + hold.id))
        ready = true
        assertTrue(provider.confirm(hold, "key", evidence).isSuccess)
    }
    @Test fun noAvailabilityAndInvalidTtlFailClosed() {
        val denied = SyntheticBookingAdapter(availability = { false })
        assertFalse(denied.available(proposal()))
        assertTrue(denied.hold(proposal(), "key", 100L).isFailure)
        val overflow = SyntheticBookingAdapter(clockMs = { Long.MAX_VALUE - 1L })
        assertTrue(overflow.hold(proposal(), "key", 100L).isFailure)
        assertTrue(overflow.hold(proposal(), "", 100L).isFailure)
        assertTrue(overflow.hold(proposal(), "key", 0L).isFailure)
    }
    @Test fun simulatorDoesNotDeclareSuccessWithoutProviderConfirmation() {
        val task = CallTask("Synthetic clinic", "book appointment", "appointment",
            CallConstraints.unconstrained(), CallPreferences.none(), emptyMap())
        val target = CallResolvedTarget("Synthetic clinic", "+48000000000")
        val facts = AuthorizedFactSnapshot(task, target, 1L, emptySet(), emptySet())
        val provider = SyntheticBookingAdapter(confirmReady = { false })
        val result = BookAppointmentSimulator(task, target, facts, zone,
            bookingProvider = provider).run(listOf(
            BookAppointmentSimulationStep.ReceptionistOffer("2026-11-11 11:00"),
        ))
        assertEquals(BookAppointmentOutcome.INCOMPLETE, result.outcome)
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.PROVIDER_NOT_CONFIRMED })
        assertFalse(result.evidence.any { it.type == BookAppointmentEvidenceType.PROVIDER_CONFIRMED })
        assertTrue(result.replayVerified)
    }
}
