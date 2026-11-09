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
}

fun interface OutgoingIvrDigitPort {
    /** Return true only when the exact app-owned call accepted this request. */
    fun dispatch(request: OutgoingIvrDtmfRequest): Boolean
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
    private val dispatched = linkedMapOf<Key, Char>()

    @Synchronized
    fun request(value: OutgoingIvrDtmfRequest): OutgoingIvrDtmfResult {
        if (value.callGeneration <= 0 || value.turn <= 0 || value.stepIndex < 0 ||
            value.digit !in "0123456789*#ABCD") return OutgoingIvrDtmfResult.INVALID_REQUEST
        val key = Key(value.callGeneration, value.turn, value.stepIndex)
        dispatched[key]?.let {
            return if (it == value.digit) OutgoingIvrDtmfResult.REUSED
            else OutgoingIvrDtmfResult.CONFLICT
        }

        if (!active(value.callGeneration)) return OutgoingIvrDtmfResult.NOT_ACTIVE
        if (!authorized(value)) return OutgoingIvrDtmfResult.NOT_AUTHORIZED

        // Both callbacks may have raced with an operator STOP, change of call owner or a
        // revoked grant. Re-check immediately before the irreversible port boundary.
        if (!active(value.callGeneration)) return OutgoingIvrDtmfResult.NOT_ACTIVE
        if (!authorized(value)) return OutgoingIvrDtmfResult.NOT_AUTHORIZED

        // Record before dispatch. Any uncertain side effect is never sent twice by this gate.
        dispatched[key] = value.digit
        return try {
            if (port.dispatch(value)) OutgoingIvrDtmfResult.DISPATCHED
            else OutgoingIvrDtmfResult.UNKNOWN
        } catch (_: Exception) {
            OutgoingIvrDtmfResult.UNKNOWN
        }
    }
}
