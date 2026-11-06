package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.conversation.ConversationResponseFormat

import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.conversation.*

/** Task-specific authority validation followed by the shared direction-aware instruction compiler. */
internal object GemmaDirectTaskBriefBuilder {
    fun build(request: CampaignExecutionRequest, grant: CampaignAuthorizationGrant,
        preparation: ConversationPreparation): String {
        require(CampaignAuthorizationPolicy.authorize(grant, request) is CampaignAuthorizationDecision.Authorized) {
            "gemma_task_brief_scope_not_authorized"
        }
        val goal = when {
            request.action == ClirCampaignSpec.ACTION &&
                request.service == CallService.CLIR && request.enabled ->
                "Zlecone działanie: włączenie stałego zastrzegania prezentacji numeru " +
                    "CLIR dla aktualnej usługi głosowej. " +
                    "Użytkownik chce ukrywać swój numer przy połączeniach wychodzących."
            else -> throw IllegalArgumentException("gemma_task_brief_unsupported_execution")
        }
        val scoped = ConversationPreparation(preparation.id, preparation.title, ConversationDirection.OUTGOING,
            preparation.notes, preparation.skills, preparation.scopedDataFields(grant.allowedDisclosureFields))
        return ConversationInstructionBuilder.build(scoped, goal, ConversationResponseFormat.SPOKEN_TEXT)
    }
}
