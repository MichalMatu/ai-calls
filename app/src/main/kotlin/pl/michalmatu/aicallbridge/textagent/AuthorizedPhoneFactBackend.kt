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

            when (policy.decide(request, snapshot)) {
                FactDisclosureDecision.ALLOW -> Unit
                FactDisclosureDecision.ASK_USER -> {
                    listener.onError(ERROR_REQUIRES_USER)
                    return
                }
                FactDisclosureDecision.DENY -> {
                    listener.onError(ERROR_DENIED)
                    return
                }
            }

            val secret = vault.get(IdentityFieldId.PHONE).getOrThrow()
                ?: throw IllegalStateException("phone_fact_unavailable")
            val speech = secret.withPlaintext(::formatPhoneSpeech)
            onDisclosure()
            listener.onComplete(speech)
        } catch (_: Throwable) {
            listener.onError(ERROR_RESOLUTION_FAILED)
        }
    }

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
