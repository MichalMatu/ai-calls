package pl.michalmatu.aicallbridge.campaign

import java.util.Locale

/**
 * A historical campaign record is not a live Call state, a grant, or external-success
 * evidence. This presenter is deliberately pure: it cannot read or change the runtime.
 */
internal enum class CampaignExecutionMeaning {
    NO_RECORD,
    REQUESTED,
    CONFIRMED,
    FAILED,
    UNVERIFIED,
}

internal enum class CampaignExecutionTone {
    NEUTRAL,
    CAUTION,
    SUCCESS,
}

internal data class CampaignExecutionScreenState(
    val meaning: CampaignExecutionMeaning,
    val tone: CampaignExecutionTone,
    val summary: String,
)

internal object CampaignExecutionStatusPresenter {
    private const val DISCONNECT_REQUESTED = "targeted_disconnect_requested"
    private const val DISCONNECT_CONFIRMED = "targeted_disconnect_confirmed"
    private const val DISCONNECT_FAILED = "targeted_disconnect_failed"
    private const val DISCONNECT_EXCEPTION = "targeted_disconnect_exception"
    private val codeSeparator = Regex("[^A-Za-z0-9_]+")

    fun present(record: String?, locale: Locale): CampaignExecutionScreenState {
        val raw = record?.trim().orEmpty()
        // Split exact diagnostic tokens; never read arbitrary prose as confirmed success.
        // Unlike Android-unsupported (?iuU), this regex uses no special Unicode flags.
        val codes = raw.split(codeSeparator).toSet()
        val meaning = when {
            raw.isEmpty() -> CampaignExecutionMeaning.NO_RECORD
            DISCONNECT_FAILED in codes || DISCONNECT_EXCEPTION in codes ->
                CampaignExecutionMeaning.FAILED
            DISCONNECT_CONFIRMED in codes -> CampaignExecutionMeaning.CONFIRMED
            DISCONNECT_REQUESTED in codes -> CampaignExecutionMeaning.REQUESTED
            else -> CampaignExecutionMeaning.UNVERIFIED
        }
        val tone = when (meaning) {
            CampaignExecutionMeaning.CONFIRMED -> CampaignExecutionTone.SUCCESS
            CampaignExecutionMeaning.REQUESTED, CampaignExecutionMeaning.FAILED ->
                CampaignExecutionTone.CAUTION
            CampaignExecutionMeaning.NO_RECORD, CampaignExecutionMeaning.UNVERIFIED ->
                CampaignExecutionTone.NEUTRAL
        }
        val english = locale.language.equals("en", ignoreCase = true)
        val summary = when (meaning) {
            CampaignExecutionMeaning.NO_RECORD -> if (english)
                "No execution record. Authorization alone does not place a call."
            else "Brak zapisu uruchomienia. Sama autoryzacja nie rozpoczyna połączenia."

            CampaignExecutionMeaning.REQUESTED -> if (english)
                "Requested AI stop and cleanup of the owned call. Check diagnostics for confirmation."
            else "Wysłano polecenie zatrzymania AI i zakończenia własnego połączenia. Potwierdzenie sprawdź w diagnostyce."

            CampaignExecutionMeaning.CONFIRMED -> if (english)
                "Confirmed cleanup of the owned call in the last record. This does not confirm task success."
            else "Potwierdzono zakończenie własnego połączenia w ostatnim zapisie. To nie potwierdza efektu zadania."

            CampaignExecutionMeaning.FAILED -> if (english)
                "Owned-call cleanup was not confirmed. Check diagnostics."
            else "Nie potwierdzono zakończenia własnego połączenia. Sprawdź diagnostykę."

            CampaignExecutionMeaning.UNVERIFIED -> if (english)
                "An execution event was recorded; its outcome remains unverified. See diagnostics."
            else "Zapisano zdarzenie wykonania; wynik pozostaje niepotwierdzony. Szczegóły w diagnostyce."
        }
        return CampaignExecutionScreenState(meaning = meaning, tone = tone, summary = summary)
    }
}
