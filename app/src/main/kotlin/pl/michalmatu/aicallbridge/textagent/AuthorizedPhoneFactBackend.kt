package pl.michalmatu.aicallbridge.textagent

import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.identity.CallTaskMode
import pl.michalmatu.aicallbridge.identity.FactDisclosureDecision
import pl.michalmatu.aicallbridge.identity.FactDisclosurePolicy
import pl.michalmatu.aicallbridge.identity.FactDisclosureRequest
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.identity.PersistentIdentityVault
import pl.michalmatu.aicallbridge.identity.ScopedIdentityFactRead
import pl.michalmatu.aicallbridge.identity.ScopedIdentityFactReader
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId

internal data class AuthorizedPhoneFactScope(
    val task: CallTask,
    val target: CallResolvedTarget,
    val state: TaskGraphStateId,
    val generation: Long,
    val mode: CallTaskMode,
    val authorized: Boolean,
)

internal class AuthorizedPhoneFactBackend(
    private val vault: PersistentIdentityVault,
    private val policy: FactDisclosurePolicy,
    private val scopeProvider: () -> AuthorizedPhoneFactScope,
    private val onDisclosure: () -> Unit = {},
) : TextCallAgentBackend {
    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        if (userText.trim() != DISCLOSE_PHONE_CONTROL) {
            listener.onError(ERROR_CONTROL_NOT_SUPPORTED)
            return
        }

        try {
            val scope = scopeProvider()
            val available = vault.availableFields().getOrThrow()
            val authorizedFields =
                if (scope.authorized) setOf(IdentityFieldId.PHONE) else emptySet()
            val snapshot = AuthorizedFactSnapshot(
                task = scope.task,
                target = scope.target,
                generation = scope.generation,
                availableFields = available,
                authorizedFields = authorizedFields,
                allowedDisclosureStates = if (scope.authorized) {
                    mapOf(IdentityFieldId.PHONE to setOf(scope.state))
                } else {
                    emptyMap()
                },
            )
            val request = FactDisclosureRequest(
                task = scope.task,
                target = scope.target,
                currentState = scope.state,
                fieldId = IdentityFieldId.PHONE,
                mode = scope.mode,
                snapshotGeneration = scope.generation,
            )

            // Non-granted scopes retain the existing explicit approval response, and
            // never read the secret. All granted reads use the shared late vault port.
            if (!scope.authorized) {
                listener.onError(when (policy.decide(request, snapshot)) {
                    FactDisclosureDecision.ASK_USER -> ERROR_REQUIRES_USER
                    else -> ERROR_DENIED
                })
                return
            }

            val fact = ScopedIdentityFactReader(
                vault = vault,
                policy = policy,
                activeAuthority = { requested -> liveScopeMatches(scope, requested) },
            ).read(request, snapshot)
            when (fact) {
                ScopedIdentityFactRead.ApprovalRequired -> listener.onError(ERROR_REQUIRES_USER)
                ScopedIdentityFactRead.Denied -> listener.onError(ERROR_RESOLUTION_FAILED)
                is ScopedIdentityFactRead.Authorized -> {
                    val speech = fact.secret.withPlaintext(::formatPhoneSpeech)
                    // Revocation can also happen during formatting or diagnostics.
                    if (!liveScopeMatches(scope, request)) {
                        listener.onError(ERROR_RESOLUTION_FAILED)
                        return
                    }
                    onDisclosure()
                    if (!liveScopeMatches(scope, request)) {
                        listener.onError(ERROR_RESOLUTION_FAILED)
                        return
                    }
                    listener.onComplete(speech)
                }
            }
        } catch (_: Throwable) {
            listener.onError(ERROR_RESOLUTION_FAILED)
        }
    }

    /**
     * The backend belongs to one owned Call; a changed policy/STOP/turn or generation
     * must invalidate its disclosure even if the decrypted value was already fetched.
     */
    private fun liveScopeMatches(
        initial: AuthorizedPhoneFactScope,
        request: FactDisclosureRequest,
    ): Boolean = runCatching {
        val current = scopeProvider()
        current == initial && current.authorized &&
            current.task == request.task && current.target == request.target &&
            current.state == request.currentState && current.mode == request.mode &&
            current.generation == request.snapshotGeneration
    }.getOrDefault(false)

    override fun cancel() = Unit

    override fun close() = Unit

    private fun formatPhoneSpeech(value: String): String {
        require(value.all { it in '0'..'9' || it in ALLOWED_FORMATTING })
        val digits = value.filter { it in '0'..'9' }
        require(digits.length in MIN_DIGITS..MAX_DIGITS)
        return "Numer usługi to " + digits.toCharArray().joinToString(" ") + "."
    }

    internal companion object {
        const val DISCLOSE_PHONE_CONTROL = "[[DISCLOSE_AUTHORIZED_FACT:PHONE]]"
        const val ERROR_CONTROL_NOT_SUPPORTED = "authorized_fact_control_not_supported"
        const val ERROR_REQUIRES_USER = "phone_disclosure_requires_user"
        const val ERROR_DENIED = "phone_disclosure_denied"
        const val ERROR_RESOLUTION_FAILED = "authorized_phone_fact_resolution_failed"

        private const val ALLOWED_FORMATTING = " +-()"
        private const val MIN_DIGITS = 7
        private const val MAX_DIGITS = 15
    }
}
