package pl.michalmatu.aicallbridge.conversationcore

/**
 * An IVR digit is a proposed app-owned effect, not permission to control an arbitrary call.
 * A caller must bind [active] and [authorized] to the *same* exact outgoing call and grant.
 * Neither a model transcript nor this class may dial, select a SIM, or manufacture approval.
 */
data class OutgoingIvrDtmfRequest(
    val callGeneration: Long,
    val turn: Int,
    val stepIndex: Int,
    val digit: Char,
)

enum class OutgoingIvrDtmfResult {
    DISPATCHED,
    REUSED,
    INVALID_REQUEST,
    NOT_ACTIVE,
    NOT_AUTHORIZED,
    CONFLICT,
    UNKNOWN,
    LIMIT_REACHED,
    NOT_READY,
}

/**
 * NOT_STARTED proves no external tone was attempted, so a queued IVR step may
 * retry when the previous tone stops. UNCERTAIN could already have been audible
 * and must remain consumed to prevent duplicates.
 */
enum class OutgoingIvrDigitAttempt {
    STARTED,
    NOT_STARTED,
    UNCERTAIN,
}

fun interface OutgoingIvrDigitPort {
    /** Return true only when the exact app-owned call accepted this request. */
    fun dispatch(request: OutgoingIvrDtmfRequest): Boolean

    /** Legacy ports cannot distinguish refused-before-play from uncertain delivery. */
    fun dispatchAttempt(request: OutgoingIvrDtmfRequest): OutgoingIvrDigitAttempt =
        if (dispatch(request)) OutgoingIvrDigitAttempt.STARTED else OutgoingIvrDigitAttempt.UNCERTAIN
}

/**
 * Bounded, fail-closed dispatch/idempotency gate for a future authorized OUTGOING IVR adapter.
 * No Android or hardware dependency; sending must be wired only with a real grant and exact Call.
 *
 * Once a request reaches the port, even an exception is UNKNOWN, never a reason for automatic
 * replay. If STOP invalidates the generation between two checks, no port call takes place.
 */
class OutgoingIvrDtmfGate(
    private val active: (Long) -> Boolean,
    private val authorized: (OutgoingIvrDtmfRequest) -> Boolean,
    private val port: OutgoingIvrDigitPort,
) {
    private data class Key(val generation: Long, val turn: Int, val index: Int)
    private data class Receipt(val digit: Char, val result: OutgoingIvrDtmfResult)
    private val dispatched = linkedMapOf<Key, Receipt>()
    private val checking = mutableMapOf<Key, Char>()

    @Synchronized
    fun request(value: OutgoingIvrDtmfRequest): OutgoingIvrDtmfResult {
        if (value.callGeneration <= 0 || value.turn <= 0 || value.stepIndex < 0 ||
            value.digit !in "0123456789*#") return OutgoingIvrDtmfResult.INVALID_REQUEST
        val key = Key(value.callGeneration, value.turn, value.stepIndex)
        dispatched[key]?.let {
            return when {
                it.digit != value.digit -> OutgoingIvrDtmfResult.CONFLICT
                it.result == OutgoingIvrDtmfResult.UNKNOWN -> OutgoingIvrDtmfResult.UNKNOWN
                else -> OutgoingIvrDtmfResult.REUSED
            }
        }
        // A policy callback may reenter this synchronized gate; it must never dispatch the
        // same step twice while an earlier authorization decision is still in progress.
        checking[key]?.let {
            return if (it == value.digit) OutgoingIvrDtmfResult.UNKNOWN
            else OutgoingIvrDtmfResult.CONFLICT
        }
        if (dispatched.size >= MAX_DISPATCHED_STEPS) return OutgoingIvrDtmfResult.LIMIT_REACHED

        checking[key] = value.digit
        try {
            if (!active(value.callGeneration)) return OutgoingIvrDtmfResult.NOT_ACTIVE
            if (!authorized(value)) return OutgoingIvrDtmfResult.NOT_AUTHORIZED

            // Both callbacks may race with STOP, owner switch or grant revocation.
            if (!active(value.callGeneration)) return OutgoingIvrDtmfResult.NOT_ACTIVE
            if (!authorized(value)) return OutgoingIvrDtmfResult.NOT_AUTHORIZED

            // Reserve before the irreversible port call. Any uncertain effect stays UNKNOWN
            // on duplicate requests instead of being reported as success or replayed.
            dispatched[key] = Receipt(value.digit, OutgoingIvrDtmfResult.UNKNOWN)
            val attempt = try {
                port.dispatchAttempt(value)
            } catch (_: Exception) {
                OutgoingIvrDigitAttempt.UNCERTAIN
            }
            if (attempt == OutgoingIvrDigitAttempt.NOT_STARTED) {
                // No irreversible hardware action occurred. Preserve exact-key
                // idempotency only after a tone may actually have started.
                dispatched.remove(key)
                return OutgoingIvrDtmfResult.NOT_READY
            }
            val result = if (attempt == OutgoingIvrDigitAttempt.STARTED)
                OutgoingIvrDtmfResult.DISPATCHED else OutgoingIvrDtmfResult.UNKNOWN
            dispatched[key] = Receipt(value.digit, result)
            return result
        } finally {
            checking.remove(key)
        }
    }

    private companion object {
        const val MAX_DISPATCHED_STEPS = 256
    }
}
