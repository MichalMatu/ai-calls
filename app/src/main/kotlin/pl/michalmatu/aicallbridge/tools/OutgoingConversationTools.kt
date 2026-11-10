package pl.michalmatu.aicallbridge.tools

import android.content.Context
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversation.ConversationRuntimeHooks
import pl.michalmatu.aicallbridge.conversationtools.*

/** No tool can dial, book, send or access the Vault. End-call authority stays with exact owned Call. */
internal fun createOutgoingConversationTools(
    context: Context,
    hooks: ConversationRuntimeHooks,
    disconnectExactAuthorizedCall: () -> Boolean,
): ConversationToolExecutor {
    val settings = ConversationToolSettingsStore(context)
    return ConversationToolExecutor(object : ConversationToolPort {
        override fun enabled(tool: ConversationToolId) = tool in settings.enabled()
        override fun authorized(tool: ConversationToolId) =
            hooks.sessionId.isNotBlank() && hooks.authorized()

        override fun requestEndCall(turn: Int): Boolean =
            hooks.authorized() && disconnectExactAuthorizedCall()

        override fun saveNote(turn: Int, proposal: ConversationNoteProposal): Boolean =
            hooks.authorized() && AndroidConversationPreparationStore(context).appendNote(
                hooks.sessionId, "OUTGOING", turn, proposal)
    })
}
