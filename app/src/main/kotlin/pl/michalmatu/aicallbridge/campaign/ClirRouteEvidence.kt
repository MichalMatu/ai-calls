package pl.michalmatu.aicallbridge.campaign

import java.text.Normalizer
import java.util.Locale

/**
 * Topic evidence is weaker than success evidence. Require a recognisable CLIR service context
 * rather than an arbitrary substring such as "nie-clir".
 */
internal object ClirRouteEvidence {
    private val explicit = Regex(
        """\b(usluga|uslugi|usluge|wlaczenie|ustawienia|ustawieniami|obsluga|blokada|zastrzeganie)\b.{0,60}\bclir\b|\bclir\b.{0,60}\b(usluga|uslugi|usluge|wlaczenie|ustawienia|ustawieniami|obsluga|blokada|zastrzeganie)\b|\bblokad\w* prezentac\w* numer\w*\b|\bzastrzegani\w* numer\w*\b""",
    )
    fun isExplicitServiceContext(transcript: String): Boolean {
        val text = Normalizer.normalize(
            transcript.lowercase(Locale.ROOT),
            Normalizer.Form.NFD,
        ).replace(Regex("\\p{M}+"), "").replace('ł', 'l')
            .replace(Regex("[^a-z0-9]+"), " ").trim()
        return text.length in 12..512 && explicit.containsMatchIn(text)
    }
}
