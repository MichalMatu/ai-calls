package pl.michalmatu.aicallbridge.appointment

import java.time.ZonedDateTime
import pl.michalmatu.aicallbridge.agent.CallPolicyAction
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyResult
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue

internal enum class BookAppointmentEffectOwnerRejectReason {
    INVALID_EFFECT_COUNT,
    UNSUPPORTED_TRANSITION,
    MISSING_APPOINTMENT_AT,
    INVALID_APPOINTMENT_AT,
    POLICY_TRANSITION_MISMATCH,
    WORKFLOW_NOT_READY,
}

internal sealed interface BookAppointmentEffectOwnerResult {
    data object Ignored : BookAppointmentEffectOwnerResult

    data class Applied(
        val action: CallPolicyAction,
    ) : BookAppointmentEffectOwnerResult

    data class Rejected(
        val reason: BookAppointmentEffectOwnerRejectReason,
    ) : BookAppointmentEffectOwnerResult
}

/**
 * BOOK_APPOINTMENT-only effect mapping into the existing CallWorkflow owner.
 *
 * This is deliberately not a generic effect executor. It recognizes exactly one reviewed effect,
 * reconstructs one typed non-secret proposal field from the already-applied graph snapshot, and
 * asks CallWorkflow to atomically re-check the expected policy action before any workflow mutation.
 * It has no dialing, speech/TTS, identity disclosure, user-confirmation, commitment or completion
 * authority.
 */
internal class BookAppointmentTaskGraphEffectOwnerBridge(
    private val workflow: CallWorkflow,
) {
    fun onApplyResult(result: TaskGraphApplyResult): BookAppointmentEffectOwnerResult {
        val accepted = result as? TaskGraphApplyResult.Accepted
            ?: return BookAppointmentEffectOwnerResult.Ignored

        val effectCount = accepted.effects.count {
            it == BookAppointmentTaskGraph.EVALUATE_PROPOSAL_EFFECT
        }
        if (effectCount == 0) return BookAppointmentEffectOwnerResult.Ignored
        if (effectCount != 1) {
            return rejected(BookAppointmentEffectOwnerRejectReason.INVALID_EFFECT_COUNT)
        }

        val expectedAction = when (accepted.record.transitionId) {
            BookAppointmentTaskGraph.ACCEPT_AUTONOMOUS_TRANSITION ->
                CallPolicyAction.AUTONOMOUSLY_ALLOWED
            BookAppointmentTaskGraph.REQUIRE_CONFIRMATION_TRANSITION ->
                CallPolicyAction.NEEDS_USER_DECISION
            else -> return rejected(BookAppointmentEffectOwnerRejectReason.UNSUPPORTED_TRANSITION)
        }

        val slot = accepted.snapshot.context[BookAppointmentTaskGraph.APPOINTMENT_AT]
            as? TaskGraphSlotValue.Text
            ?: return rejected(BookAppointmentEffectOwnerRejectReason.MISSING_APPOINTMENT_AT)
        val scheduledAt = runCatching { ZonedDateTime.parse(slot.value) }.getOrNull()
            ?: return rejected(BookAppointmentEffectOwnerRejectReason.INVALID_APPOINTMENT_AT)
        val proposal = CallProposal(scheduledAt, null, null, null, null)

        val decision = runCatching {
            workflow.evaluateProposalIfExpectedAction(proposal, expectedAction)
        }.getOrElse {
            return rejected(BookAppointmentEffectOwnerRejectReason.WORKFLOW_NOT_READY)
        }
        if (decision.action() != expectedAction) {
            return rejected(BookAppointmentEffectOwnerRejectReason.POLICY_TRANSITION_MISMATCH)
        }
        return BookAppointmentEffectOwnerResult.Applied(decision.action())
    }

    private fun rejected(
        reason: BookAppointmentEffectOwnerRejectReason,
    ): BookAppointmentEffectOwnerResult.Rejected = BookAppointmentEffectOwnerResult.Rejected(reason)
}
