package pl.michalmatu.aicallbridge.campaign

import android.content.Context

internal data class CampaignRuntimeRecord(val message: String, val recordedAtEpochMs: Long?)

/**
 * Last service event is historical evidence, not a live-call flag or carrier result.
 * Timestamp is committed atomically with its text; old untimed records stay readable.
 */
internal class CampaignRuntimeStatusStore(context: Context, preferencesName: String = PREFERENCES_NAME) {
    private val preferences =
        (context.applicationContext ?: context).getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun save(status: String, recordedAtEpochMs: Long = System.currentTimeMillis()) {
        require(status.length <= MAX_STATUS_CHARS) { "campaign_status_too_large" }
        require(recordedAtEpochMs > 0L) { "invalid_record_timestamp" }
        check(preferences.edit()
            .putString(KEY_STATUS, status)
            .putLong(KEY_RECORDED_AT, recordedAtEpochMs)
            .commit()) {
            "campaign_status_persist_failed"
        }
    }

    fun load(): String? =
        preferences.getString(KEY_STATUS, null)?.takeIf { it.isNotBlank() }

    fun loadRecord(): CampaignRuntimeRecord? {
        val message = load() ?: return null
        val timestamp = preferences.getLong(KEY_RECORDED_AT, 0L).takeIf { it > 0L }
        return CampaignRuntimeRecord(message, timestamp)
    }

    companion object {
        private const val PREFERENCES_NAME = "campaign_runtime_status_v1"
        private const val KEY_STATUS = "status"
        private const val KEY_RECORDED_AT = "recorded_at_epoch_ms"
        private const val MAX_STATUS_CHARS = 512
    }
}
