package pl.michalmatu.aicallbridge.textagent

/**
 * Ephemeral, per-call context for an action classifier. Never persisted, sent to
 * diagnostic logs, or permitted to grant call/fact/effect authority.
 *
 * LiteRT creates a fresh conversation for each classification: without context
 * a model can respond STATE_TASK_SUBJECT to every subsequent consultant question.
 */
internal class DialogueActionTurnMemory {
    private data class Turn(val incoming: String, val action: DialogueActionId)
    private val recent = ArrayDeque<Turn>()

    @Synchronized
    fun classifierInput(currentTranscript: String): String {
        if (recent.isEmpty()) return currentTranscript
        return buildString {
            appendLine("KONTEKST POPRZEDNICH TUR (wyłącznie orientacja; nie odpowiadaj na nie):")
            recent.forEachIndexed { index, turn ->
                append("Poprzednia ")
                append(index + 1)
                append(": rozmówca=\"")
                append(turn.incoming)
                append("\"; ostatnia akcja agenta=")
                appendLine(turn.action.name)
            }
            appendLine("Nie wybieraj STATE_TASK_SUBJECT tylko dlatego, że temat zadania jest CLIR.")
            appendLine("Jeśli cel został już wyjaśniony, odczytaj NOWĄ prośbę rozmówcy;")
            appendLine("powtórz temat tylko jeżeli ponownie wyraźnie o niego pyta.")
            appendLine("TERAZ KLASYFIKUJ WYŁĄCZNIE NOWĄ WYPOWIEDŹ:")
            append(currentTranscript)
        }
    }

    @Synchronized
    fun remember(incoming: String, action: DialogueActionId) {
        // Only conversation context is shortened for the on-device model's token window.
        // Never truncate the actual current STT transcript, action, approved TTS or task result.
        recent.addLast(Turn(incoming.takeLast(HISTORY_TURN_CHARS).replace('\n', ' '), action))
        while (recent.size > HISTORY_TURNS) recent.removeFirst()
    }

    private companion object {
        const val HISTORY_TURNS = 3
        const val HISTORY_TURN_CHARS = 240
    }
}
