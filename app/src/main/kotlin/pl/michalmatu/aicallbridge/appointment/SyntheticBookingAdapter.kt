package pl.michalmatu.aicallbridge.appointment

import pl.michalmatu.aicallbridge.agent.CallCommitmentConsumptionEvidence
import pl.michalmatu.aicallbridge.agent.CallExternalEffect
import pl.michalmatu.aicallbridge.agent.CallProposal
import java.time.Instant

/** Synthetic-only handles: their diagnostics must not include appointment identity or terms. */
class SyntheticBookingHold(val id: String, val expiresAtMs: Long) {
    override fun toString(): String = "SyntheticBookingHold(REDACTED)"
}
class SyntheticBookingReceipt(val bookingId: String, val holdId: String) {
    override fun toString(): String = "SyntheticBookingReceipt(REDACTED)"
}

/**
 * Off-call, in-memory booking provider. It cannot perform real reservations.
 * Provider-side state and read-back are independent of model output and CallCommitmentGate.
 */
class SyntheticBookingAdapter(
    private val clockMs: () -> Long = { 0L },
    private val availability: (CallProposal) -> Boolean = { true },
    private val confirmReady: () -> Boolean = { true },
) {
    private data class SlotKey(val provider: String?, val location: String?, val instant: Instant)
    private fun slotOf(proposal: CallProposal): SlotKey? =
        proposal.scheduledAt()?.toInstant()?.let { SlotKey(proposal.provider(), proposal.location(), it) }

    private class Entry(
        val hold: SyntheticBookingHold,
        val key: String,
        val slot: SlotKey,
        val proposal: CallProposal,
        var receipt: SyntheticBookingReceipt? = null,
        var consumedEvidence: CallCommitmentConsumptionEvidence? = null,
        var cancelled: Boolean = false,
    )
    private val byKey = mutableMapOf<String, Entry>()
    /** Availability belongs to the provider/time/location, not a mutable price or terms. */
    private val bySlot = mutableMapOf<SlotKey, Entry>()
    private val byHold = mutableMapOf<String, Entry>()
    private val byReceipt = mutableMapOf<String, SyntheticBookingReceipt>()
    // Consumption receipts are opaque identity objects, not reusable effect descriptions.
    private val byConsumption = java.util.IdentityHashMap<CallCommitmentConsumptionEvidence, Entry>()
    private var sequence = 0L

    private fun occupied(slot: SlotKey, atMs: Long): Boolean {
        val current = bySlot[slot] ?: return false
        return !current.cancelled &&
            (current.receipt != null || atMs < current.hold.expiresAtMs)
    }

    @Synchronized fun available(proposal: CallProposal): Boolean = runCatching {
        val slot = slotOf(proposal) ?: return@runCatching false
        if (occupied(slot, clockMs())) return@runCatching false
        if (!availability(proposal)) return@runCatching false
        // Provider callbacks may synchronously acquire this slot. A read-only
        // availability query cannot return stale success after that side effect.
        !occupied(slot, clockMs())
    }.getOrDefault(false)

    /** Same idempotency key retains original TTL; changed terms are rejected. */
    @Synchronized fun hold(
        proposal: CallProposal, idempotencyKey: String, ttlMs: Long,
    ): Result<SyntheticBookingHold> = runCatching {
        require(idempotencyKey.isNotBlank())
        require(ttlMs > 0L)
        val slot = slotOf(proposal) ?: error("booking time is missing")
        val now = clockMs()
        byKey[idempotencyKey]?.let { old ->
            check(old.proposal == proposal) { "booking key conflicts with another proposal" }
            check(!old.cancelled) { "booking hold was cancelled" }
            check(old.receipt != null || now < old.hold.expiresAtMs) { "booking hold expired" }
            return@runCatching old.hold
        }
        check(now <= Long.MAX_VALUE - ttlMs) { "booking hold overflow" }
        val expiresAtMs = now + ttlMs
        check(available(proposal)) { "slot unavailable" }
        // An availability callback may advance the clock or reenter hold().
        // Never publish a hold that is expired on arrival or overwrite a peer.
        val afterAvailabilityMs = clockMs()
        check(afterAvailabilityMs < expiresAtMs) { "hold expired during availability check" }
        check(!occupied(slot, afterAvailabilityMs)) { "slot acquired during availability callback" }
        val hold = SyntheticBookingHold("synthetic-hold-" + (++sequence), expiresAtMs)
        val entry = Entry(hold, idempotencyKey, slot, proposal)
        byKey[idempotencyKey] = entry
        bySlot[slot] = entry
        byHold[hold.id] = entry
        hold
    }

    /** Replaying the same successful request returns its receipt without another booking. */
    @Synchronized fun confirm(
        hold: SyntheticBookingHold,
        idempotencyKey: String,
        consumed: CallCommitmentConsumptionEvidence,
    ): Result<SyntheticBookingReceipt> = runCatching {
        val entry = byHold[hold.id] ?: error("unknown hold")
        check(entry.hold === hold && entry.key == idempotencyKey) { "hold mismatch" }
        check(!entry.cancelled && bySlot[entry.slot] === entry) { "hold no longer active" }
        check(consumed.effect == CallExternalEffect.BookAppointment(entry.proposal)) {
            "consumed effect differs from held offer"
        }
        // A consumed permit may authorize exactly one hold. Retain this binding
        // across failed provider responses, cancellation and expiry; retries on the
        // same hold are idempotent, never permission to confirm a replacement hold.
        check(byConsumption[consumed]?.let { it === entry } != false) {
            "commitment evidence already used for another hold"
        }
        check(entry.consumedEvidence == null || entry.consumedEvidence === consumed) {
            "hold already bound to different commitment evidence"
        }
        entry.receipt?.let { return@runCatching it }
        check(clockMs() < entry.hold.expiresAtMs) { "hold expired" }
        entry.consumedEvidence = consumed
        byConsumption[consumed] = entry
        check(runCatching { confirmReady() }.getOrDefault(false)) { "provider confirmation unavailable" }
        // External readiness can reenter confirm(), cancel(), or advance the clock.
        // Preserve an already-confirmed receipt; never commit a revoked/expired hold.
        entry.receipt?.let { return@runCatching it }
        check(!entry.cancelled && bySlot[entry.slot] === entry &&
            clockMs() < entry.hold.expiresAtMs) { "hold invalidated during confirmation" }
        val receipt = SyntheticBookingReceipt("synthetic-booking-" + entry.hold.id, entry.hold.id)
        entry.receipt = receipt
        byReceipt[receipt.bookingId] = receipt
        receipt
    }

    /** Only an exact, unconfirmed hold may be released; confirmed receipts are immutable. */
    @Synchronized fun cancel(hold: SyntheticBookingHold, idempotencyKey: String): Boolean {
        val entry = byHold[hold.id] ?: return false
        if (entry.hold !== hold || entry.key != idempotencyKey ||
            entry.cancelled || entry.receipt != null ||
            bySlot[entry.slot] !== entry) return false
        val now = runCatching { clockMs() }.getOrNull() ?: return false
        if (now >= entry.hold.expiresAtMs) return false
        entry.cancelled = true
        bySlot.remove(entry.slot, entry)
        return true
    }

    /** Read-back from provider-owned store, not from the model or commitment ledger. */
    @Synchronized fun readBack(bookingId: String): SyntheticBookingReceipt? = byReceipt[bookingId]
}
