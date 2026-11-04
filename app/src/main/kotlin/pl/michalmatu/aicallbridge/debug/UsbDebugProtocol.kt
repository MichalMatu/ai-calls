package pl.michalmatu.aicallbridge.debug

/** Stable shell diagnostic operation names; no generic write-to-preferences escape hatch. */
internal object UsbDebugProtocol {
    const val VERSION = 1
    val actions = setOf(
        "status", "evidence_status", "profile_check", "profile_set", "profile_reset",
        "grant_revoke", "evidence_disable",
    )
    val mutableActions = setOf("profile_set", "profile_reset", "grant_revoke", "evidence_disable")
    fun accepts(action: String): Boolean = action in actions
    fun needsOfflineVoiceProbe(action: String): Boolean =
        action == "profile_set" || action == "profile_check"
}
