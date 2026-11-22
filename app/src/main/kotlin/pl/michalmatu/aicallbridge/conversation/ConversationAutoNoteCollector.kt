package pl.michalmatu.aicallbridge.conversation

import java.util.Locale
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteProposal

/** Bounded, local-only fallback for materially relevant caller statements.
 * Does not infer facts, summarize a full call or store every STT turn.
 * The latest correction appears after earlier facts and takes precedence.
 */
internal class ConversationAutoNoteCollector {
    private val material = LinkedHashMap<Int, String>()
    var revision: Int = 0
        private set

    fun accept(turn: Int, text: String) {
        if (turn <= 0) return
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        if (cleaned.length !in 12..320 || !safe(cleaned) ||
            IncomingCallerFarewellPolicy.isUnambiguousGoodbye(cleaned) ||
            bareIntroduction.matches(cleaned)) return
        val lower = cleaned.lowercase(Locale.ROOT)
        val corrective = correction.containsMatchIn(lower) && material.isNotEmpty()
        if (!topic.containsMatchIn(lower) && !corrective) return
        if (material[turn] == cleaned || material.values.lastOrNull() == cleaned) return
        material[turn] = cleaned
        while (material.size > 4) material.remove(material.keys.first())
        revision++
    }

    fun proposal(): ConversationNoteProposal? {
        if (material.isEmpty()) return null
        val text = material.values.joinToString("\n") { it.take(320) }
        if (!safe(text)) return null
        return ConversationNoteProposal(ConversationNoteKind.NOTE, text)
    }

    companion object {
        private val bareIntroduction = Regex(
            """(?iu)^(?:(?:chcę|chciałbym|chciałabym|proszę|i would like to|i'd like to) )?(?:zostawić|przekazać|leave|take) (?:a |the )?(?:wiadomość|notatkę|message|note)[.!]?$"""
        )
        private val topic = Regex(
            """(?iu)\b(?:wiadomoś\w*|notatk\w*|przekaż\w*|przekaz\w*|zapisz|termin\w*|spotkani\w*|wizyta|wizyt\w*|jutro|pojutrze|poniedział\w*|wtorek|wtork\w*|środ\w*|czwart\w*|piąt\w*|sobot\w*|niedziel\w*|godzin\w*|adres\w*|przyjed\w*|oddzwon\w*|kontakt\w*|ustalen\w*|rezerwac\w*|message|appointment|meeting|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|schedule|reschedule|contact|address|deliver|callback|note|please tell|pass along|correct(?:ion|ed)?|change(?:d)?)\b"""
        )
        private val correction = Regex(
            """(?iu)\b(?:poprawk\w*|jednak|zamiast|zmien\w*|korekt\w*|nie,? (?:w |we )?|correction|actually|instead|rather|reschedule)\b"""
        )
        private val secretWords = Regex(
            """(?iu)\b(?:pin|pesel|hasło|hasla|password|passcode|otp|cvv|iban|numer karty|card number|account number|kod jednorazowy|verification code)\b"""
        )
        private val longNumbers = Regex("""(?<!\d)\+?\d(?:[\s-]?\d){6,}(?!\d)""")
        fun safe(value: String): Boolean =
            value.isNotBlank() && value.length <= 2000 &&
                !secretWords.containsMatchIn(value) && !longNumbers.containsMatchIn(value) &&
                value.none { Character.isISOControl(it) && it != '\n' && it != '\t' }
    }
}
