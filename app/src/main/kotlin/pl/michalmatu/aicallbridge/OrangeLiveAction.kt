package pl.michalmatu.aicallbridge

/**
 * Fixed, reviewed developer actions for the controlled Orange IVR discovery loop.
 *
 * This is not a free-text surface. Each action id maps to one reviewed response or to an
 * observation-only mode that cannot release speech.
 */
internal enum class OrangeLiveAction(
    val wireId: String,
    val reviewedResponse: String?,
) {
    GREETING("greeting", "Dzień dobry."),
    LIST_CAPABILITIES("list_capabilities", "Jakie sprawy możesz załatwić?"),
    INVOICE_STATUS("invoice_status", "Chcę sprawdzić fakturę."),
    INVOICE_TOPIC("invoice_topic", "Faktura."),
    INTERNET_PROBLEM("internet_problem", "Mam problem z internetem."),
    MOBILE_DATA_PROBLEM("mobile_data_problem", "Nie działają mi dane komórkowe."),
    OUTAGE_TOPIC("outage_topic", "Awaria."),
    WIFI_PROBLEM("wifi_problem", "Mam problem z Wi-Fi."),
    COVERAGE_INFO("coverage_info", "Chcę sprawdzić zasięg."),
    INTERNET_CONFIG("internet_config", "Jak skonfigurować internet w telefonie?"),
    MMS_CONFIG("mms_config", "Jak skonfigurować MMS w telefonie?"),
    MANUAL_NETWORK_SELECTION("manual_network_selection", "Jak włączyć ręczny wybór sieci operatora?"),
    SMS_PROBLEM("sms_problem", "Nie mogę wysyłać SMS-ów."),
    SMS_RECEIVE_PROBLEM("sms_receive_problem", "Nie mogę odbierać SMS-ów."),
    VOICE_QUALITY_PROBLEM("voice_quality_problem", "Podczas rozmów zanika głos."),
    OUTGOING_CALL_PROBLEM("outgoing_call_problem", "Nie mogę wykonywać połączeń."),
    INCOMING_CALL_PROBLEM("incoming_call_problem", "Nie mogę odbierać połączeń."),
    ROAMING_INFO("roaming_info", "Roaming."),
    ROAMING_PRICES("roaming_prices", "Chcę sprawdzić ceny w roamingu."),
    OBSERVE_ONLY("observe_only", null),
    ;

    companion object {
        fun fromWireId(raw: String?): OrangeLiveAction {
            val normalized = raw?.trim().orEmpty()
            if (normalized.isEmpty()) return GREETING
            return entries.singleOrNull { it.wireId == normalized }
                ?: throw IllegalArgumentException("unknown_orange_live_action")
        }
    }
}
