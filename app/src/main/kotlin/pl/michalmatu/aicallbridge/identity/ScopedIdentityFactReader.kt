package pl.michalmatu.aicallbridge.identity

/**
 * A value is exposed only to an app-owned call tool, never to global model instructions.
 * [IdentitySecretValue] keeps ordinary diagnostics redacted.
 */
sealed interface ScopedIdentityFactRead {
    data class Authorized(
        val fieldId: IdentityFieldId,
        val secret: IdentitySecretValue,
    ) : ScopedIdentityFactRead {
        override fun toString(): String =
            "ScopedIdentityFactRead.Authorized(field=$fieldId, value=REDACTED)"
    }

    data object ApprovalRequired : ScopedIdentityFactRead
    data object Denied : ScopedIdentityFactRead
}

/**
 * Late, typed vault read using the existing policy and the active exact-call authority callback.
 * Snapshot permission alone is not a live grant: authority is checked before policy and after
 * decrypt, so a STOP/takeover during a vault read cannot return a now-stale secret.
 *
 * The caller must bind [activeAuthority] to its exact live Call, task and generation.
 * This read does not disclose, speak, or transmit anything by itself.
 */
class ScopedIdentityFactReader(
    private val vault: PersistentIdentityVault,
    private val activeAuthority: (FactDisclosureRequest) -> Boolean,
    private val policy: FactDisclosurePolicy = DefaultFactDisclosurePolicy(),
) {
    fun read(
        request: FactDisclosureRequest,
        snapshot: AuthorizedFactSnapshot,
    ): ScopedIdentityFactRead {
        if (!safeAuthority(request)) return ScopedIdentityFactRead.Denied
        return when (policy.decide(request, snapshot)) {
            FactDisclosureDecision.DENY -> ScopedIdentityFactRead.Denied
            FactDisclosureDecision.ASK_USER -> ScopedIdentityFactRead.ApprovalRequired
            FactDisclosureDecision.ALLOW -> {
                val secret = vault.get(request.fieldId).getOrNull() ?: return ScopedIdentityFactRead.Denied
                if (!safeAuthority(request)) ScopedIdentityFactRead.Denied
                else ScopedIdentityFactRead.Authorized(request.fieldId, secret)
            }
        }
    }

    private fun safeAuthority(request: FactDisclosureRequest): Boolean =
        runCatching { activeAuthority(request) }.getOrDefault(false)
}
