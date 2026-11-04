package pl.michalmatu.aicallbridge.campaign

import android.content.Context

/**
 * Dialogue implementation is a user-selected execution strategy, NOT new authorization.
 * A missing/unknown preference always resumes with the original script-first behavior.
 */
internal enum class CampaignDialogueMode {
    SCRIPT_GEMMA,
    GEMMA_DIRECT;

    companion object {
        fun fromWire(value: String?): CampaignDialogueMode? =
            entries.firstOrNull { it.name == value }

        fun explicitStart(value: String?): CampaignDialogueMode? =
            if (value == null) SCRIPT_GEMMA else fromWire(value)
    }
}

internal class CampaignDialogueModeStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "campaign_dialogue_mode_v1", Context.MODE_PRIVATE,
    )

    fun bind(grantId: String, mode: CampaignDialogueMode) {
        require(grantId.isNotBlank())
        preferences.edit()
            .putString("grant_id", grantId)
            .putString("mode", mode.name)
            .apply()
    }

    fun forGrant(grantId: String): CampaignDialogueMode {
        if (preferences.getString("grant_id", null) != grantId) {
            return CampaignDialogueMode.SCRIPT_GEMMA
        }
        return CampaignDialogueMode.fromWire(preferences.getString("mode", null))
            ?: CampaignDialogueMode.SCRIPT_GEMMA
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
