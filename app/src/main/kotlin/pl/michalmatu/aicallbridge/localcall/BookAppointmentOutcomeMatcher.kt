package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.appointment.BookAppointmentTaskGraph
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot

/**
 * Pure comparison of approved BOOK_APPOINTMENT terms with factual external outcome evidence.
 *
 * This matcher owns no permit, workflow or TaskGraph authority. Callers must separately validate
 * outcome status and the commitment/consumption lifecycle before using it.
 */
internal object BookAppointmentOutcomeMatcher {
    fun matches(
        proposal: CallProposal,
        outcome: CallOutcome,
        snapshot: TaskGraphSnapshot,
    ): Boolean {
        val scheduledAt = proposal.scheduledAt() ?: return false
        if (outcome.scheduledAt() != scheduledAt) return false
        val graphAppointment = snapshot.context[BookAppointmentTaskGraph.APPOINTMENT_AT]
            as? TaskGraphSlotValue.Text ?: return false
        if (graphAppointment.value != scheduledAt.toString()) return false

        val approvedPrice = proposal.price()
        if (approvedPrice != null) {
            val cost = outcome.cost() ?: return false
            if (approvedPrice.currencyCode() != cost.currencyCode()) return false
            if (approvedPrice.amount().compareTo(cost.amount()) != 0) return false
        }
        val approvedProvider = proposal.provider()
        if (approvedProvider != null && approvedProvider != outcome.provider()) return false
        val approvedLocation = proposal.location()
        if (approvedLocation != null && approvedLocation != outcome.location()) return false
        return true
    }
}
