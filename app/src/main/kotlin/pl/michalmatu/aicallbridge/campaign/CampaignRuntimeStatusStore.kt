package pl.michalmatu.aicallbridge.campaign

import android.content.Context

internal class CampaignRuntimeStatusStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(status: String) {
        require(status.length <= MAX_STATUS_CHARS) { "campaign_status_too_large" }
        check(preferences.edit().putString(KEY_STATUS, status).commit()) {
            "campaign_status_persist_failed"
        }
    }

    fun load(): String? =
        preferences.getString(KEY_STATUS, null)?.takeIf { it.isNotBlank() }

    companion object {
        private const val PREFERENCES_NAME = "campaign_runtime_status_v1"
        private const val KEY_STATUS = "status"
        private const val MAX_STATUS_CHARS = 512
    }
}
