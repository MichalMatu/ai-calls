package pl.michalmatu.aicallbridge.campaign

import java.text.Normalizer
import java.util.Locale

/**
 * Carrier wording is evidence, not authority. Reject ambiguous and negative language.
 * A successful network mutation is still provisional until independent verification.
 */
internal object ClirCarrierEvidence {
    private val subject = Regex(
        """\bclir\b|\bblokad[aeiy]? prezentacji numeru\b|\bzastrzegani[ae] numeru\b""",
    )
    private val denial = Regex(
        """\b(nie|brak|blad|bled|odmow\w*|nieaktyw\w*|niedostep\w*|niepowod\w*|nieud\w*|anulow\w*|bez zmian|nie mozemy|nie mozna|nie udalo|nie powiodlo|jeszcze nie)\b""",
    )
    private val affirmative = Regex(
        """\b(zostal\w*|jest|sa|pomyslnie|poprawnie)\b.{0,64}\b(wlacz\w*|aktywow\w*|aktywn\w*|uruchom\w*|ustawion\w*)\b|\b(wlaczylismy|aktywowalismy|uruchomilismy)\b""",
    )
    private val pendingOrQuestion = Regex(
        """\b(czy|mozemy|prosimy|prosze|chcesz|potwierdz|wybier\w*|zostanie|bedzie|wkrotce|oczekuj\w*|prawda|tak\s+czy\s+nie|zgadza\s+sie)\b""",
    )

    fun isConfirmedCarrierStatement(transcript: String): Boolean {
        // Preserve question boundaries: normalization strips punctuation.
        if (transcript.any { it == '?' || it == '？' }) return false
        val text = Normalizer.normalize(
            transcript.lowercase(Locale.ROOT),
            Normalizer.Form.NFD,
        ).replace(Regex("\\p{M}+"), "")
            .replace('ł', 'l')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (text.length !in 18..512) return false
        if (!subject.containsMatchIn(text)) return false
        if (denial.containsMatchIn(text) || pendingOrQuestion.containsMatchIn(text)) return false
        return affirmative.containsMatchIn(text)
    }
}
