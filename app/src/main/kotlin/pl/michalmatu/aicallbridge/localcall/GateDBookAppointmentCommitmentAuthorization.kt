package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallCommitmentAuthorization

internal enum class GateDBookAppointmentCommitmentAuthorizationRejectReason {
    PRODUCT_INTEGRATION_NOT_BOUND,
    INTEGRATION_INACTIVE,
    COMMITMENT_GATE_NOT_BOUND,
    GRAPH_NOT_COMMITMENT,
    MISSING_APPROVED_PROPOSAL,
    ALREADY_ISSUED,
    AUTHORIZATION_FAILED,
    INTERNAL_FAILURE,
}

/**
 * Redacted result for the bounded BOOK_APPOINTMENT commitment-authorization boundary.
 *
 * The opaque token is available only to the explicit commitment owner that receives [authorization];
 * normal diagnostics must not expose it.
 */
internal sealed interface GateDBookAppointmentCommitmentAuthorizationResult {
    class Authorized(
        val authorization: CallCommitmentAuthorization,
    ) : GateDBookAppointmentCommitmentAuthorizationResult {
        override fun toString(): String =
            "GateDBookAppointmentCommitmentAuthorizationResult.Authorized(authorization=REDACTED)"
    }

    data class Rejected(
        val reason: GateDBookAppointmentCommitmentAuthorizationRejectReason,
    ) : GateDBookAppointmentCommitmentAuthorizationResult
}
