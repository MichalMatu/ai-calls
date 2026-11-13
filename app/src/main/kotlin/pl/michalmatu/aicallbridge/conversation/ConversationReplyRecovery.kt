package pl.michalmatu.aicallbridge.conversation

import java.util.concurrent.CancellationException

/** Fixed diagnostics only. Native/JSON exceptions can contain private response text. */
internal enum class ConversationReplyFailure {
    JSON, OBJECT, FIELDS, SPEECH, TOOL, NOTE, TRANSCRIPT,
}

internal class InvalidConversationReply(val failure: ConversationReplyFailure) :
    IllegalArgumentException("conversation_reply_invalid_${failure.name.lowercase()}")

/** A repair repeats inference, never playback, a tool, or a telephone operation. */
internal fun <R> sendWithReplyRecovery(
    isCurrent: () -> Boolean,
    send: () -> R,
    validate: (R) -> Unit,
    recreateForRepair: () -> Unit,
    rejected: (ConversationReplyFailure) -> Unit = {},
): R {
    var repaired = false
    while (true) {
        if (!isCurrent()) throw CancellationException("native_generation_cancelled")
        val reply = send()
        if (!isCurrent()) throw CancellationException("native_generation_cancelled")
        try {
            validate(reply)
            if (!isCurrent()) throw CancellationException("native_generation_cancelled")
            return reply
        } catch (invalid: InvalidConversationReply) {
            rejected(invalid.failure)
            if (!isCurrent()) throw CancellationException("native_generation_cancelled")
            if (repaired) throw invalid
            repaired = true
            recreateForRepair()
        }
    }
}

internal object ConversationReplyRecovery {
    const val REPAIR_INSTRUCTION = "Poprzednia próba nie spełniła schematu i nie została wypowiedziana ani wykonana. " +
        "Odpowiedz na bieżącą wypowiedź kompletnym obiektem JSON zgodnym ze schematem. " +
        "Pole reply musi zawierać krótką odpowiedź, tool jedną dozwoloną wartość. " +
        ConversationReplyProtocol.FIELD_INSTRUCTION + " " +
        "Nie dodawaj komentarzy ani powitania."
    const val CLARIFICATION = "Nie udało mi się poprawnie przygotować odpowiedzi. Proszę powtórzyć ostatnią informację."

    fun canClarify(reason: String) = reason == "backend_conversation_reply_invalid"
}
