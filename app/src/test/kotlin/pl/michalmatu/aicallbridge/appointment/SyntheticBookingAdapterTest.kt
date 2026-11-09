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
    @Test fun differentKeysCannotDoubleBookHeldOrConfirmedSlot() {
        val provider = SyntheticBookingAdapter()
        val p = proposal()
        assertTrue(provider.available(p))
        val held = provider.hold(p, "first", 100L).getOrThrow()
        assertFalse(provider.available(p))
        assertTrue(provider.hold(p, "second", 100L).isFailure)
        val receipt = provider.confirm(held, "first", consumed(p)).getOrThrow()
        assertTrue(provider.hold(p, "second", 100L).isFailure)
        assertFalse(provider.cancel(held, "first"))
        assertSame(receipt, provider.readBack(receipt.bookingId))
        // A different time remains independently available.
        assertTrue(provider.hold(proposal(12), "other-time", 100L).isSuccess)
    }

    @Test fun expiryReleasesSlotButCannotReviveOldHold() {
        var now = 0L
        val provider = SyntheticBookingAdapter(clockMs = { now })
        val p = proposal()
        val stale = provider.hold(p, "old", 10L).getOrThrow()
        now = 10L
        assertTrue(provider.available(p))
        val current = provider.hold(p, "fresh", 20L).getOrThrow()
        assertFalse(provider.cancel(stale, "old"))
        assertFalse(provider.available(p))
        assertTrue(provider.confirm(stale, "old", consumed(p)).isFailure)
        assertTrue(provider.confirm(current, "fresh", consumed(p)).isSuccess)
        assertTrue(provider.hold(p, "another", 20L).isFailure)
    }

    @Test fun cancellationRevokesHoldAndPermitsNewKeyWithoutReplayingOld() {
        val provider = SyntheticBookingAdapter()
        val p = proposal()
        val old = provider.hold(p, "old", 100L).getOrThrow()
        assertFalse(provider.cancel(old, "wrong"))
        assertTrue(provider.cancel(old, "old"))
        assertFalse(provider.cancel(old, "old"))
        assertTrue(provider.hold(p, "old", 100L).isFailure)
        assertTrue(provider.confirm(old, "old", consumed(p)).isFailure)
        val fresh = provider.hold(p, "fresh", 100L).getOrThrow()
        assertTrue(provider.confirm(fresh, "fresh", consumed(p)).isSuccess)
    }

    @Test fun changedPriceAndEquivalentUtcTimeCannotDoubleBookAnOccupiedSlot() {
        val provider = SyntheticBookingAdapter()
        val offer = proposal()
        val held = provider.hold(offer, "first", 1_000L).getOrThrow()
        val withPrice = CallProposal(
            offer.scheduledAt(),
            MoneyAmount(java.math.BigDecimal("75"), "PLN"),
            offer.paymentMode(), offer.provider(), offer.location(),
        )
        val sameInstantUtc = CallProposal(
            offer.scheduledAt().withZoneSameInstant(ZoneId.of("UTC")),
            offer.price(), offer.paymentMode(), offer.provider(), offer.location(),
        )
        assertFalse(provider.available(withPrice))
        assertFalse(provider.available(sameInstantUtc))
        assertTrue(provider.hold(withPrice, "changed-price", 1_000L).isFailure)
        assertTrue(provider.hold(sameInstantUtc, "equivalent-time", 1_000L).isFailure)
        assertTrue(provider.confirm(held, "first", consumed(offer)).isSuccess)
        assertTrue(provider.hold(withPrice, "after-confirm", 1_000L).isFailure)
    }

    @Test fun missingTimeCannotAcquireProviderSlotEvenWhenProviderSaysAvailable() {
        val provider = SyntheticBookingAdapter()
        val invalid = CallProposal(null, null, null, "Synthetic clinic", null)
        assertFalse(provider.available(invalid))
        assertTrue(provider.hold(invalid, "no-time", 1_000L).isFailure)
    }

    @Test fun releasedExpiredSlotMayBeHeldWithNewPriceButOldPermitCannotConfirm() {
        var now = 0L
        val provider = SyntheticBookingAdapter(clockMs = { now })
        val old = proposal()
        val stale = provider.hold(old, "first", 10L).getOrThrow()
        now = 10L
        val withPrice = CallProposal(
            old.scheduledAt(), MoneyAmount(java.math.BigDecimal("40"), "PLN"),
            null, old.provider(), old.location(),
        )
        val next = provider.hold(withPrice, "second", 100L).getOrThrow()
        assertTrue(provider.confirm(stale, "first", consumed(old)).isFailure)
        assertTrue(provider.confirm(next, "second", consumed(withPrice)).isSuccess)
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
