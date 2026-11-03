package pl.michalmatu.aicallbridge.campaign

/**
 * Reconcile a user START or a permitted Android service restoration against the
 * persisted, user-authorized campaign. This never authorizes a target itself.
 *
 * START for the same grant must not erase retry/backoff state. A new grant may be
 * queued while the previous owned call is still winding down, but never dialed
 * concurrently with it.
 */
internal enum class CampaignStartDisposition {
    BEGIN_NEW,
    REUSE_SAVED,
    ALREADY_RUNNING,
    QUEUE_REPLACEMENT,
}

internal object CampaignStartReconciliationPolicy {
    fun decide(
        saved: CampaignContinuationState?,
        requestedGrantId: String,
        requestedAccountScope: String,
        ownedCallRunning: Boolean,
    ): CampaignStartDisposition {
        require(requestedGrantId.isNotBlank() && requestedAccountScope.isNotBlank()) {
            "campaign_start_scope_missing"
        }
        if (saved?.grantId == requestedGrantId &&
            saved.accountScope == requestedAccountScope
        ) {
            return if (ownedCallRunning) CampaignStartDisposition.ALREADY_RUNNING
            else CampaignStartDisposition.REUSE_SAVED
        }
        return if (ownedCallRunning) CampaignStartDisposition.QUEUE_REPLACEMENT
        else CampaignStartDisposition.BEGIN_NEW
    }
}
