package pl.michalmatu.aicallbridge.tools

import android.content.Context
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

/** Tool permissions persistence; no ephemeral tool feed or Android view state. */
internal class ConversationToolsSettingsController(context: Context) {
    private val store = ConversationToolSettingsStore(context.applicationContext)

    fun enabled(): Set<ConversationToolId> = store.enabled()

    fun save(selected: Set<ConversationToolId>): Boolean =
        runCatching { store.save(selected) }.isSuccess
}
