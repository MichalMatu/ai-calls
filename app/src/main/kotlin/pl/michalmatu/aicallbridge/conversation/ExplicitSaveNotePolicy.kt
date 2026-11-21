package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteProposal

/**
 * Narrow, model-independent recovery for a caller's explicit request to save a note.
 * It never ends a call, infers a booking, or turns a mere topic mention into a tool call.
 * Only the existing authorized SAVE_NOTE port may persist the proposal.
 */
internal object ExplicitSaveNotePolicy {
    private val request = Regex(
        """(?iu)\b(?:zapisz|zanotuj|zapamiętaj|prosz[eę]\s+(?:zapisa[cć]|zanotowa[cć])|czy\s+możesz\s+(?:mi\s+)?(?:zapisa[cć]|zanotowa[cć]))\b"""
    )
    private val negation = Regex(
        """(?iu)\b(?:nie|żeby\s+nie)\s+(?:zapisuj|zapisz|zanotuj|zapisa[cć]|zanotowa[cć])\b"""
    )
    private val secrets = Regex(
        """(?iu)\b(?:has[lł]o|pin|pesel|cvv|otp|kod\s+jednorazowy|jednorazowy\s+kod)\b"""
    )
    private val closing = Regex(
        """(?iu)(?:\s*(?:i\s+)?(?:to\s+już\s+wszystko|do\s+widzenia|do\s+usłyszenia|dzi[eę]kuj[eę]).*)$"""
    )
    private val noteHead = Regex(
        """(?iu)^(?:\s|,|:)*(?:(?:mi|to|tę|tą|moją|notatkę|wiadomość|informację|proszę|że)\s+)*"""
    )
    private val words = Regex("""\s+""")

    fun proposal(callerText: String, earlierCallerText: String?): ConversationNoteProposal? {
        val current = callerText.replace(words, " ").trim()
        if (current.length !in 8..2_000 || negation.containsMatchIn(current) ||
            secrets.containsMatchIn(current) || current.any { it in "\"'„”«»" }) return null
        val command = request.find(current) ?: return null
        val tail = current.substring(command.range.last + 1)
            .replace(noteHead, "")
            .replace(closing, "")
            .trim(' ', ',', '.', ':', ';', '!')
        val context = earlierCallerText?.replace(words, " ")?.trim()
            ?.takeIf { it.length in 15..700 && !secrets.containsMatchIn(it) }
        // Prefer the actual information over a sign-off or a bare "save this" instruction.
        // Keep just the adjacent relevant caller turn; never auto-save the whole conversation.
        val content = when {
            context != null && tail.length >= 16 && !isBareRequest(tail) ->
                "$context. $tail"
            context != null -> context
            tail.length >= 16 && !isBareRequest(tail) -> tail
            else -> return null
        }.take(900)
        if (content.isBlank() || content.any { it.isISOControl() }) return null
        return ConversationNoteProposal(ConversationNoteKind.NOTE, content)
    }

    private fun isBareRequest(text: String): Boolean =
        text.lowercase().matches(
            Regex("""(?iu)(?:notatkę|wiadomość|to|wszystko|tę\s+notatkę|tą\s+wiadomość|teraz|proszę)(?:[.!?\s]*)""")
        )
}
