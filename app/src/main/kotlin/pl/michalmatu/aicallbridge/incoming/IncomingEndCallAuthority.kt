package pl.michalmatu.aicallbridge.incoming

import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantPolicy
import pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantState

/**
 * Final authorization immediately before Telecom.disconnect(). This is separate
 * from AI audio authority: the coordinator is already in ENDING at this boundary.
 */
internal object IncomingEndCallAuthority {
    fun permits(
        state: IncomingAssistantState?,
        endingGeneration: Long?,
        slotGeneration: Long,
        slotStillOwned: Boolean,
        slotCancelled: Boolean,
        endToolRequested: Boolean,
        slotPolicy: IncomingAssistantPolicy,
        currentPolicy: IncomingAssistantPolicy,
    ): Boolean =
        state == IncomingAssistantState.ENDING &&
            endingGeneration != null && endingGeneration == slotGeneration &&
            slotStillOwned && !slotCancelled && endToolRequested &&
            slotPolicy == currentPolicy && currentPolicy.mode == AnsweringMode.AI
}
