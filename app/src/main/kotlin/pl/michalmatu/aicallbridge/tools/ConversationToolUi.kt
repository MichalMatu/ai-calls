package pl.michalmatu.aicallbridge.tools

import android.content.Context
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.conversationtools.*

internal class ConversationToolSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("conversation_tools_v1", Context.MODE_PRIVATE)
    fun enabled(): Set<ConversationToolId> = ConversationToolId.entries.filterTo(mutableSetOf()) {
        prefs.getBoolean("enabled_${it.wireName}", it == ConversationToolId.END_CALL)
    }
    fun save(enabled: Set<ConversationToolId>) {
        val editor = prefs.edit()
        ConversationToolId.entries.forEach { editor.putBoolean("enabled_${it.wireName}", it in enabled) }
        if (!editor.commit()) throw IOException("conversation_tool_settings_save_failed")
    }
}

internal object ConversationToolUi {
    private val latest = AtomicReference<ConversationToolEvent?>(null)
    fun record(event: ConversationToolEvent) { latest.set(event) }
    fun latestText(): String = latest.get()?.let { "Ostatnie wywołanie: " + text(it) } ?: "Brak bieżącego wywołania. Historia narzędzi jest dostępna w zapisanych rozmowach."
    fun text(event: ConversationToolEvent): String = event.tool.title + " • " + when (event.stage) {
        ConversationToolStage.REQUESTED -> "model zgłosił żądanie"
        ConversationToolStage.VALIDATED -> "sprawdzono zakres"
        ConversationToolStage.DISPATCHED -> "żądanie przekazano do Androida; oczekiwanie na koniec połączenia"
        ConversationToolStage.CONFIRMED -> "Android potwierdził koniec połączenia"
        ConversationToolStage.UNKNOWN -> "wynik pozostaje niepotwierdzony"
        ConversationToolStage.REUSED -> "odczytano wynik wcześniejszego żądania"
        ConversationToolStage.FAILED -> "błąd wykonania"
        ConversationToolStage.REJECTED -> when (event.result) {
            ConversationToolResult.DISABLED -> "narzędzie jest wyłączone"
            else -> "brak aktualnego upoważnienia do wykonania"
        }
    }
}
