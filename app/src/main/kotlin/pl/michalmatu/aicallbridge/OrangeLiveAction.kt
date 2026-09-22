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
