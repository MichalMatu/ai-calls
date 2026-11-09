package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

/** Lab-only typed validation; even an allowed proposal is NEVER an external effect. */
internal enum class LabToolDisposition { NONE, PROPOSED_ONLY, REJECTED_UNAVAILABLE }

internal data class LabToolAssessment(
    val proposed: ConversationToolId?,
    val disposition: LabToolDisposition,
    val executed: Boolean = false,
) {
    val acceptedProposal: ConversationToolId?
        get() = if (disposition == LabToolDisposition.PROPOSED_ONLY) proposed else null
}

internal object LabToolProposalPolicy {
    fun validate(reply: ConversationReply,
        enabled: Set<ConversationToolId>): LabToolAssessment = when {
        reply.tool == null -> LabToolAssessment(null, LabToolDisposition.NONE)
        reply.tool !in enabled ->
            LabToolAssessment(reply.tool, LabToolDisposition.REJECTED_UNAVAILABLE)
        else -> LabToolAssessment(reply.tool, LabToolDisposition.PROPOSED_ONLY)
    }
}
