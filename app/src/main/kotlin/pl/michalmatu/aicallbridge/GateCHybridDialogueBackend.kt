package pl.michalmatu.aicallbridge

import android.content.Context
import java.io.File
import pl.michalmatu.aicallbridge.developerrelay.ChatRelayEnvelope
import pl.michalmatu.aicallbridge.developerrelay.ChatRelayMailbox
import pl.michalmatu.aicallbridge.developerrelay.InteractiveChatRelayBackend
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.textagent.DialogueActionBackendFactory
import pl.michalmatu.aicallbridge.textagent.DialogueActionDecision
import pl.michalmatu.aicallbridge.textagent.DialogueActionDecisionObserver
import pl.michalmatu.aicallbridge.textagent.DialogueActionExecutor
import pl.michalmatu.aicallbridge.textagent.DialogueActionId
import pl.michalmatu.aicallbridge.textagent.DialogueActionPolicy
import pl.michalmatu.aicallbridge.textagent.DialogueTaskContext
import pl.michalmatu.aicallbridge.textagent.FailoverTextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

internal enum class GateCHybridResponseSource {
    LOCAL_ACTION,
    HOST_ACTION,
    CHAT_RELAY,
}

internal data class GateCHybridDiagnosticsSnapshot(
    val decisions: List<DialogueActionDecision>,
    val localSkillErrors: List<String>,
    val responseSources: List<GateCHybridResponseSource>,
)

internal class GateCHybridDiagnostics : DialogueActionDecisionObserver {
    private val lock = Any()
    private val decisions = mutableListOf<DialogueActionDecision>()
    private val localSkillErrors = mutableListOf<String>()
    private val responseSources = mutableListOf<GateCHybridResponseSource>()

    override fun onDecision(decision: DialogueActionDecision) {
        synchronized(lock) { decisions += decision }
    }

    fun recordLocalSkillError(reason: String) {
        synchronized(lock) { localSkillErrors += sanitize(reason) }
    }

    fun recordResponseSource(source: GateCHybridResponseSource) {
        synchronized(lock) { responseSources += source }
    }

    fun snapshot(): GateCHybridDiagnosticsSnapshot = synchronized(lock) {
        GateCHybridDiagnosticsSnapshot(
            decisions = decisions.toList(),
            localSkillErrors = localSkillErrors.toList(),
            responseSources = responseSources.toList(),
        )
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(160)
}

/**
 * Gemma-first dialogue router used by the controlled live call.
 *
 * Gemma interprets each transcript into a structured action. Application-owned executors produce
 * speech, request an authorized runtime fact from the host, or emit the reviewed external-effect
 * control token. Model errors, low confidence and TAKE_OVER fall through to the interactive relay.
 */
internal object GateCHybridDialogueBackendFactory {
    const val DISCLOSE_PHONE_CONTROL = "[[DISCLOSE_AUTHORIZED_FACT:PHONE]]"

    fun create(
        context: Context,
        provider: TextLlmProvider,
        relaySessionId: String,
        diagnostics: GateCHybridDiagnostics,
        effectCommitControl: String? = null,
        canConfirmEffect: () -> Boolean = { false },
    ): TextCallAgentBackend {
        require(
            provider == TextLlmProvider.LOCAL_PHONE_LLM || provider == TextLlmProvider.LOCAL_GEMMA_4,
        ) { "gate_c_hybrid_requires_phone_local_model" }
        if (effectCommitControl != null) {
            require(effectCommitControl.isNotBlank()) { "effect_commit_control_required" }
        }
        ChatRelayEnvelope(relaySessionId, 1, "probe").validate()

        val relayBackend = InteractiveChatRelayBackend(
            mailbox = ChatRelayMailbox(File(context.filesDir, ChatRelayMailbox.DIRECTORY_NAME)),
            sessionId = relaySessionId,
        )
        val hostActionRelay = relayBackend.observed(
            onComplete = {
                diagnostics.recordResponseSource(GateCHybridResponseSource.HOST_ACTION)
            },
        )
        val supervisorRelay = relayBackend.observed(
            onComplete = {
                diagnostics.recordResponseSource(GateCHybridResponseSource.CHAT_RELAY)
            },
        )
        val actionBackend = DialogueActionBackendFactory.create(
            context = context.applicationContext,
            provider = provider,
            policy = actionPolicy(allowEffectConfirmation = effectCommitControl != null),
            executor = actionExecutor(
                hostActionRelay = hostActionRelay,
                diagnostics = diagnostics,
                effectCommitControl = effectCommitControl,
                canConfirmEffect = canConfirmEffect,
            ),
            observer = diagnostics,
            taskContext = actionContext(allowEffectConfirmation = effectCommitControl != null),
        )
        return FailoverTextCallAgentBackend(
            primary = actionBackend.observed(
                onError = diagnostics::recordLocalSkillError,
            ),
            fallback = supervisorRelay,
        )
    }

    internal fun actionContext(
        allowEffectConfirmation: Boolean = true,
    ) = DialogueTaskContext(
        subject = "Włączenie stałego zastrzegania prezentacji numeru telefonu (CLIR) dla bieżącej usługi.",
        authorizedEffect = if (allowEffectConfirmation) {
            "Włączyć lub aktywować usługę CLIR, czyli stałe zastrzeganie prezentacji numeru telefonu."
        } else {
            null
        },
        argumentHints = mapOf(
            "PHONE" to "numer telefonu, numer usługi lub numer abonenta, którego dotyczy bieżące zadanie",
        ),
    )

    internal fun actionPolicy(
        allowEffectConfirmation: Boolean = true,
    ): DialogueActionPolicy {
        val actions = mutableSetOf(
            DialogueActionId.ASK_REPEAT,
            DialogueActionId.ASK_CLARIFY,
            DialogueActionId.ACKNOWLEDGE_NEUTRAL,
            DialogueActionId.STATE_TASK_SUBJECT,
            DialogueActionId.DISCLOSE_AUTHORIZED_FACT,
        )
        if (allowEffectConfirmation) {
            actions += DialogueActionId.CONFIRM_AUTHORIZED_EFFECT
        }
        return DialogueActionPolicy(
            allowedActions = actions,
            allowedArguments = mapOf(
                DialogueActionId.DISCLOSE_AUTHORIZED_FACT to setOf("PHONE"),
            ),
            minimumConfidence = 0.72,
        )
    }

    internal fun actionExecutor(
        hostActionRelay: TextCallAgentBackend,
        diagnostics: GateCHybridDiagnostics,
        effectCommitControl: String?,
        canConfirmEffect: () -> Boolean,
    ) = DialogueActionExecutor { _, decision, listener ->
        fun completeLocal(text: String) {
            diagnostics.recordResponseSource(GateCHybridResponseSource.LOCAL_ACTION)
            listener.onComplete(text)
        }

        when (decision.actionId) {
            DialogueActionId.ASK_REPEAT ->
                completeLocal("Proszę powtórzyć.")

            DialogueActionId.ASK_CLARIFY ->
                completeLocal("Proszę doprecyzować.")

            DialogueActionId.ACKNOWLEDGE_NEUTRAL ->
                completeLocal("Rozumiem.")

            DialogueActionId.STATE_TASK_SUBJECT ->
                completeLocal("Chodzi o blokadę prezentacji numeru, usługę CLIR.")

            DialogueActionId.DISCLOSE_AUTHORIZED_FACT -> {
                if (decision.argument != "PHONE") {
                    listener.onError("dialogue_action_fact_not_supported")
                } else {
                    hostActionRelay.generate(DISCLOSE_PHONE_CONTROL, listener)
                }
            }

            DialogueActionId.CONFIRM_AUTHORIZED_EFFECT -> {
                val control = effectCommitControl
                if (control == null) {
                    listener.onError("dialogue_action_effect_not_configured")
                } else if (!canConfirmEffect()) {
                    listener.onError("dialogue_action_effect_not_ready")
                } else {
                    completeLocal(control)
                }
            }

            DialogueActionId.TAKE_OVER ->
                listener.onError("dialogue_action_takeover_required")
        }
    }

    private fun TextCallAgentBackend.observed(
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
    ): TextCallAgentBackend {
        val delegate = this
        return object : TextCallAgentBackend {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                delegate.generate(userText, object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        onComplete()
                        listener.onComplete(text)
                    }

                    override fun onError(reason: String) {
                        onError(reason)
                        listener.onError(reason)
                    }
                })
            }

            override fun cancel() = delegate.cancel()
            override fun close() = delegate.close()
        }
    }
}
