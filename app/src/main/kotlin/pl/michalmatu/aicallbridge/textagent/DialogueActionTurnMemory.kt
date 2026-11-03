package pl.michalmatu.aicallbridge.textagent

import java.text.Normalizer
import java.util.Locale

/**
 * Ephemeral, per-call context for an action classifier. Never persisted, sent to
 * diagnostic logs, or permitted to grant call/fact/effect authority.
 *
 * The native LiteRT call conversation now retains all accepted model turns; do not
 * duplicate this bounded text memory into its model input. This fallback context is
 * used only by stateless classifiers and the policy-side repetition safeguard remains.
 */
internal class DialogueActionTurnMemory {
    private data class Turn(val incoming: String, val action: DialogueActionId)
    private val recent = ArrayDeque<Turn>()
    private var subjectAlreadyExplained = false

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

    /**
     * Guard against repeating a canned task introduction for every different IVR
     * utterance after the subject has already been explained. A real repeat request
     * still receives its original action. This cannot grant facts or effects.
     */
    @Synchronized
    fun adjustRepetitiveSubject(
        currentTranscript: String,
        decision: DialogueActionDecision,
    ): DialogueActionDecision {
        if (!subjectAlreadyExplained || decision.actionId != DialogueActionId.STATE_TASK_SUBJECT) {
            return decision
        }
        val normalized = Normalizer.normalize(
            currentTranscript.lowercase(Locale.ROOT), Normalizer.Form.NFD,
        ).replace(Regex("\\p{M}+"), "").replace('ł', 'l')
        val repeatSubjectRequest = listOf(
            "w jakiej sprawie", "czego dotyczy", "w czym moge pomoc",
            "jak moge pomoc", "po co pan dzwoni", "po co pani dzwoni",
            "o co chodzi", "proszę powiedzieć w jakiej",
            "prosze powiedziec w jakiej",
        ).any { it in normalized }
        return if (repeatSubjectRequest) decision else decision.copy(
            actionId = DialogueActionId.ASK_NEXT_SERVICE_STEP,
            reason = "subject_already_explained",
        )
    }

    @Synchronized
    fun remember(incoming: String, action: DialogueActionId) {
        if (action == DialogueActionId.STATE_TASK_SUBJECT) subjectAlreadyExplained = true
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
