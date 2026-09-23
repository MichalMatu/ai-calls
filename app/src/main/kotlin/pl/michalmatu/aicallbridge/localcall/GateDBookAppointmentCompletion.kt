package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId

internal enum class GateDBookAppointmentCommitmentConsumptionRejectReason {
    INTEGRATION_INACTIVE,
    GRAPH_NOT_COMMITMENT,
    MISSING_APPROVED_PROPOSAL,
    WORKFLOW_NOT_ACTIVE,
    COMMITMENT_NOT_ISSUED,
    PERMIT_STILL_AUTHORIZED,
    PROPOSAL_MISMATCH,
    ALREADY_RECORDED,
}

/** Redacted result of recording exact one-shot commitment-consumption evidence. */
internal sealed interface GateDBookAppointmentCommitmentConsumptionResult {
    data object Recorded : GateDBookAppointmentCommitmentConsumptionResult

    data class Rejected(
        val reason: GateDBookAppointmentCommitmentConsumptionRejectReason,
    ) : GateDBookAppointmentCommitmentConsumptionResult
}

internal enum class GateDBookAppointmentCompletionRejectReason {
    PRODUCT_INTEGRATION_NOT_BOUND,
    INTEGRATION_INACTIVE,
    GRAPH_NOT_COMMITMENT,
    MISSING_APPROVED_PROPOSAL,
    WORKFLOW_NOT_ACTIVE,
    COMMITMENT_NOT_CONSUMED,
    CONSUMED_PROPOSAL_MISMATCH,
    OUTCOME_NOT_SUCCESS,
    OUTCOME_MISMATCH,
    AUTHORIZATION_RECHECK_FAILED,
    GRAPH_APPLY_REJECTED,
    UNEXPECTED_EFFECTS,
    UNEXPECTED_GRAPH_RESULT,
    WORKFLOW_COMPLETION_FAILED,
    INTERNAL_FAILURE,
}

/**
 * Redacted result of the explicit BOOK_APPOINTMENT factual-completion owner boundary.
 *
 * Applied means the exact consumed proposal and matching SUCCESS outcome passed all checks, the
 * existing CallWorkflow owner accepted the outcome, and only then the staged TaskGraph snapshot was
 * committed. No plaintext proposal/outcome payload is rendered in diagnostics.
 */
internal sealed interface GateDBookAppointmentCompletionResult {
    data class Applied(
        val state: TaskGraphStateId,
        val generation: Long,
    ) : GateDBookAppointmentCompletionResult

    data class Rejected(
        val reason: GateDBookAppointmentCompletionRejectReason,
    ) : GateDBookAppointmentCompletionResult
}
