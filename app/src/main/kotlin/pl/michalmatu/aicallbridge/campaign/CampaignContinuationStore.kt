package pl.michalmatu.aicallbridge.campaign

import android.content.Context

/** Durable intent to continue only the specific grant/account that the user started. */
internal data class CampaignContinuationState(
    val grantId: String,
    val accountScope: String,
    val nextAttemptAtEpochMs: Long,
    val incompleteAttempts: Int,
)

internal object CampaignContinuationPolicy {
    private const val BASE_DELAY_MS = 30_000L
    private const val MAX_DELAY_MS = 5L * 60_000L

    /**
     * Retry pacing, not a maximum attempt quota. Do not rapidly redial a carrier IVR
     * after a short, interrupted call. The grant expiry, revocation or STOP ends the run.
     */
    fun retryDelayMs(incompleteAttempts: Int): Long {
        val shift = (incompleteAttempts - 1).coerceIn(0, 4)
        return (BASE_DELAY_MS shl shift).coerceAtMost(MAX_DELAY_MS)
    }
}

internal class CampaignContinuationStore(context: Context) {
    private val preferences = (context.applicationContext ?: context)
        .getSharedPreferences("campaign_continuation_v1", Context.MODE_PRIVATE)

    fun load(): CampaignContinuationState? = synchronized(LOCK) {
        val grantId = preferences.getString("grant_id", null)?.takeIf { it.isNotBlank() }
            ?: return@synchronized null
        val accountScope = preferences.getString("account_scope", null)
            ?.takeIf { it.isNotBlank() } ?: return@synchronized null
        CampaignContinuationState(
            grantId = grantId,
            accountScope = accountScope,
            nextAttemptAtEpochMs = preferences.getLong("next_at", 0L),
            incompleteAttempts = preferences.getInt("incomplete", 0).coerceAtLeast(0),
        )
    }

    fun begin(grantId: String, accountScope: String, nowEpochMs: Long) = synchronized(LOCK) {
        require(grantId.isNotBlank() && accountScope.isNotBlank()) { "continuation_scope_missing" }
        save(CampaignContinuationState(grantId, accountScope, nowEpochMs, 0))
    }

    fun prepareAttempt(expectedGrantId: String, nowEpochMs: Long): CampaignContinuationState? =
        synchronized(LOCK) {
            val current = load()?.takeIf { it.grantId == expectedGrantId }
                ?: return@synchronized null
            val updated = current.copy(
                nextAttemptAtEpochMs = nowEpochMs +
                    CampaignContinuationPolicy.retryDelayMs(current.incompleteAttempts + 1),
            )
            save(updated)
            updated
        }

    fun planRetry(expectedGrantId: String, nowEpochMs: Long): CampaignContinuationState? =
        synchronized(LOCK) {
            val current = load()?.takeIf { it.grantId == expectedGrantId }
                ?: return@synchronized null
            val attempts = if (current.incompleteAttempts == Int.MAX_VALUE) Int.MAX_VALUE
                else current.incompleteAttempts + 1
            val updated = current.copy(
                incompleteAttempts = attempts,
                nextAttemptAtEpochMs = nowEpochMs + CampaignContinuationPolicy.retryDelayMs(attempts),
            )
            save(updated)
            updated
        }

    fun clear() = synchronized(LOCK) {
        check(preferences.edit().clear().commit()) { "continuation_stop_persist_failed" }
    }

    private fun save(value: CampaignContinuationState) {
        check(preferences.edit()
            .putString("grant_id", value.grantId)
            .putString("account_scope", value.accountScope)
            .putLong("next_at", value.nextAttemptAtEpochMs)
            .putInt("incomplete", value.incompleteAttempts)
            .commit()) { "continuation_state_persist_failed" }
    }

    private companion object {
        val LOCK = Any()
    }
}
