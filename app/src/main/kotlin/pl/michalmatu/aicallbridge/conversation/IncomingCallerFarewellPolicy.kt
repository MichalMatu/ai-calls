package pl.michalmatu.aicallbridge.conversation

import java.util.Locale

/**
 * Conservative, incoming-call-only fallback for an unambiguous caller sign-off.
 *
 * Never inspect the assistant's own speech. Quotes, questions, corrections,
 * additional business and ambiguous thanks alone must not end a call.
 * This only proposes END_CALL; existing authenticated playback/Telecom gates
 * still decide whether the current Call may be disconnected.
 */
internal object IncomingCallerFarewellPolicy {
    private val punctuation = Regex("[.,!;:]+")
    private val whitespace = Regex("\\s+")
    private val closingTurn = Regex(
        """^(?:(?:to (?:już )?wszystko|dziękuję(?: bardzo)?|dzięki|no dobrze|okej|ok|thank you|thanks|that's all) ){0,2}(?:do widzenia|do usłyszenia|żegnam|goodbye|good bye|bye)(?: (?:dziękuję|dzięki|thank you|thanks))?$"""
    )

    fun isUnambiguousGoodbye(callerTranscript: String): Boolean {
        if (callerTranscript.isBlank() || callerTranscript.length > 120 ||
            callerTranscript.any { it in "?\"'„”«»" }) return false
        val normalized = callerTranscript.lowercase(Locale.ROOT)
            .replace(punctuation, " ")
            .replace(whitespace, " ")
            .trim()
        return closingTurn.matches(normalized)
    }

    fun finalSpeech(languageTag: String): String =
        if (languageTag == "en-US") "Thank you for calling. Goodbye."
        else "Dziękuję za rozmowę. Do widzenia."
}
