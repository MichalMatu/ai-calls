package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId

/** Explicit application-owned user decision for the first bounded BOOK_APPOINTMENT task. */
internal enum class GateDBookAppointmentUserDecision {
    CONFIRM,
    REJECT,
}

internal enum class GateDBookAppointmentUserDecisionRejectReason {
    PRODUCT_INTEGRATION_NOT_BOUND,
    INTEGRATION_INACTIVE,
    GRAPH_NOT_CONFIRMATION,
    WORKFLOW_NOT_WAITING_FOR_USER,
    MISSING_WORKFLOW_PROPOSAL,
    MISSING_GRAPH_APPOINTMENT,
    PROPOSAL_MISMATCH,
    AUTHORIZATION_RECHECK_FAILED,
    GRAPH_APPLY_REJECTED,
    UNEXPECTED_GRAPH_RESULT,
    UNEXPECTED_EFFECTS,
    WORKFLOW_DECISION_FAILED,
    OWNER_PROPOSAL_MISMATCH,
    INTERNAL_FAILURE,
}

/** Redacted result: no proposal payload or authorization token leaves this boundary. */
internal sealed interface GateDBookAppointmentUserDecisionResult {
    data class Applied(
        val state: TaskGraphStateId,
        val generation: Long,
    ) : GateDBookAppointmentUserDecisionResult

    data class Rejected(
        val reason: GateDBookAppointmentUserDecisionRejectReason,
    ) : GateDBookAppointmentUserDecisionResult
}
