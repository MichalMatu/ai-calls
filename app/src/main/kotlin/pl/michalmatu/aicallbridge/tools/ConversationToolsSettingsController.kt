package pl.michalmatu.aicallbridge.tools

import android.content.Context
import pl.michalmatu.aicallbridge.conversationtools.*
import pl.michalmatu.aicallbridge.R

/** Existing tool settings and live diagnostic status, isolated from Android views. */
internal class ConversationToolsSettingsController(context: Context) {
    private val uiContext = context
    private val store = ConversationToolSettingsStore(context.applicationContext)

    fun enabled(): Set<ConversationToolId> = store.enabled()

    fun save(selected: Set<ConversationToolId>): Boolean =
        runCatching { store.save(selected) }.isSuccess

    fun latestStatus(): String {
        val event = ConversationToolUi.latestEvent()
            ?: return uiContext.getString(R.string.tools_ui_no_recent)
        val stage = when (event.stage) {
            ConversationToolStage.REQUESTED -> R.string.tools_ui_stage_requested
            ConversationToolStage.VALIDATED -> R.string.tools_ui_stage_validated
            ConversationToolStage.DISPATCHED -> R.string.tools_ui_stage_dispatched
            ConversationToolStage.CONFIRMED -> R.string.tools_ui_stage_confirmed
            ConversationToolStage.UNKNOWN -> R.string.tools_ui_stage_unknown
            ConversationToolStage.REUSED -> R.string.tools_ui_stage_reused
            ConversationToolStage.FAILED -> R.string.tools_ui_stage_failed
            ConversationToolStage.REJECTED ->
                if (event.result == ConversationToolResult.DISABLED)
                    R.string.tools_ui_stage_disabled else R.string.tools_ui_stage_unauthorized
        }
        return uiContext.getString(R.string.tools_ui_recent_event,
            uiContext.getString(R.string.tools_ui_end_call_title),
            uiContext.getString(stage))
    }
}
