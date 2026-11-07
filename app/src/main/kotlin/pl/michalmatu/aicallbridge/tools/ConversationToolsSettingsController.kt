package pl.michalmatu.aicallbridge.tools

import android.content.Context
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

/** Existing tool settings and live diagnostic status, isolated from Android views. */
internal class ConversationToolsSettingsController(context: Context) {
    private val store = ConversationToolSettingsStore(context.applicationContext)

    fun enabled(): Set<ConversationToolId> = store.enabled()

    fun save(selected: Set<ConversationToolId>): Boolean =
        runCatching { store.save(selected) }.isSuccess

    fun latestStatus(): String = ConversationToolUi.latestText()
}
