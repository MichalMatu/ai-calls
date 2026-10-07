package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

internal class CampaignAuthorizationStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun save(grant: CampaignAuthorizationGrant) {
        check(
            preferences.edit()
                .clear()
                .putString(KEY_GRANT_ID, grant.grantId)
                .putInt(KEY_VERSION, grant.version)
                .putLong(KEY_CREATED_AT, grant.createdAtEpochMs)
                .putLong(KEY_EXPIRES_AT, grant.expiresAtEpochMs)
                .putLong(KEY_REVOKED_AT, grant.revokedAtEpochMs ?: NOT_REVOKED)
                .putString(KEY_ACCOUNT_SCOPE, grant.accountScope)
                .putStringSet(KEY_TARGETS, grant.allowedTargets)
                .putStringSet(KEY_ACTIONS, grant.allowedActions)
                .putStringSet(KEY_SERVICES, grant.allowedServices.map { it.name }.toSet())
                .putStringSet(
                    KEY_EFFECT_VALUES,
                    grant.allowedEffectValues.map(Boolean::toString).toSet(),
                )
                .putStringSet(
                    KEY_DISCLOSURE_FIELDS,
                    grant.allowedDisclosureFields.map { it.name }.toSet(),
                )
                .putInt(KEY_MAX_ATTEMPTS, grant.maxAttempts)
                .putInt(KEY_ATTEMPTS_USED, grant.attemptsUsed)
                .putString(KEY_ISSUER_EVIDENCE_REF, grant.issuerEvidenceRef)
                .commit(),
        ) { "campaign_grant_persist_failed" }
    }

    @Synchronized
    fun load(): CampaignAuthorizationGrant? {
        val grantId = preferences.getString(KEY_GRANT_ID, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching {
            CampaignAuthorizationGrant(
                grantId = grantId,
                version = preferences.getInt(KEY_VERSION, 0),
                createdAtEpochMs = preferences.getLong(KEY_CREATED_AT, -1L),
                expiresAtEpochMs = preferences.getLong(KEY_EXPIRES_AT, -1L),
                revokedAtEpochMs = preferences.getLong(KEY_REVOKED_AT, NOT_REVOKED)
                    .takeUnless { it == NOT_REVOKED },
                accountScope = preferences.getString(KEY_ACCOUNT_SCOPE, null).orEmpty(),
                allowedTargets = preferences.getStringSet(KEY_TARGETS, emptySet()).orEmpty().toSet(),
                allowedActions = preferences.getStringSet(KEY_ACTIONS, emptySet()).orEmpty().toSet(),
                allowedServices = preferences.getStringSet(KEY_SERVICES, emptySet()).orEmpty()
                    .map(CallService::valueOf)
                    .toSet(),
                allowedEffectValues = preferences.getStringSet(KEY_EFFECT_VALUES, emptySet()).orEmpty()
                    .map(String::toBooleanStrict)
                    .toSet(),
                allowedDisclosureFields =
                    preferences.getStringSet(KEY_DISCLOSURE_FIELDS, emptySet()).orEmpty()
                        .map(IdentityFieldId::valueOf)
                        .toSet(),
                maxAttempts = preferences.getInt(KEY_MAX_ATTEMPTS, 0),
                attemptsUsed = preferences.getInt(KEY_ATTEMPTS_USED, 0),
                issuerEvidenceRef =
                    preferences.getString(KEY_ISSUER_EVIDENCE_REF, null).orEmpty(),
            )
        }.getOrNull()
    }

    @Synchronized
    fun reserveAttempt(grantId: String): Result<CampaignAuthorizationGrant> = runCatching {
        val current = requireNotNull(load()) { "campaign_grant_missing" }
        require(current.grantId == grantId) { "campaign_grant_changed" }
        require(current.revokedAtEpochMs == null) { "campaign_grant_revoked" }
        val updated = current.withReservedAttempt()
        save(updated)
        updated
    }

    @Synchronized
    fun revoke(nowEpochMs: Long): CampaignAuthorizationGrant? {
        val current = load() ?: return null
        val revoked = current.revoked(nowEpochMs)
        save(revoked)
        return revoked
    }

    companion object {
        private const val PREFERENCES_NAME = "campaign_authorization_grant_v1"
        private const val KEY_GRANT_ID = "grant_id"
        private const val KEY_VERSION = "version"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_REVOKED_AT = "revoked_at"
        private const val KEY_ACCOUNT_SCOPE = "account_scope"
        private const val KEY_TARGETS = "targets"
        private const val KEY_ACTIONS = "actions"
        private const val KEY_SERVICES = "services"
        private const val KEY_EFFECT_VALUES = "effect_values"
        private const val KEY_DISCLOSURE_FIELDS = "disclosure_fields"
        private const val KEY_MAX_ATTEMPTS = "max_attempts"
        private const val KEY_ATTEMPTS_USED = "attempts_used"
        private const val KEY_ISSUER_EVIDENCE_REF = "issuer_evidence_ref"
        private const val NOT_REVOKED = Long.MIN_VALUE
    }
}
