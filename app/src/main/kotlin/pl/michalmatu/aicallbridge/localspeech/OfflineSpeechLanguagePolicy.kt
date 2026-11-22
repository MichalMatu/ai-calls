package pl.michalmatu.aicallbridge.localspeech

import java.util.Locale

/** Android may have en-GB installed while en-US is merely supported for download.
 * Avoid mistaking a different English dialect for the requested offline model.
 * Preserve the existing permissive Polish region behaviour for the MVP.
 */
internal object OfflineSpeechLanguagePolicy {
    fun matchesInstalled(requested: String, installed: String): Boolean {
        val wanted = Locale.forLanguageTag(requested)
        val available = Locale.forLanguageTag(installed)
        if (!wanted.language.equals(available.language, ignoreCase = true)) return false
        return if (requested.equals("en-US", ignoreCase = true)) {
            available.country.equals("US", ignoreCase = true)
        } else true
    }
}
