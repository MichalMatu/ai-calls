package pl.michalmatu.aicallbridge

import android.content.Context
import java.io.File
import pl.michalmatu.aicallbridge.developerrelay.ChatRelayEnvelope
import pl.michalmatu.aicallbridge.developerrelay.ChatRelayMailbox
import pl.michalmatu.aicallbridge.developerrelay.InteractiveChatRelayBackend
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.textagent.DialogueSkillDecision
import pl.michalmatu.aicallbridge.textagent.DialogueSkillDecisionObserver
import pl.michalmatu.aicallbridge.textagent.DialogueSkillId
import pl.michalmatu.aicallbridge.textagent.DialogueSkillPolicy
import pl.michalmatu.aicallbridge.textagent.FailoverTextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.LocalDialogueSkillBackendFactory
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

internal enum class GateCHybridResponseSource {
    LOCAL_SKILL,
    CHAT_RELAY,
}

internal data class GateCHybridDiagnosticsSnapshot(
    val decisions: List<DialogueSkillDecision>,
    val localSkillErrors: List<String>,
    val responseSources: List<GateCHybridResponseSource>,
)

internal class GateCHybridDiagnostics : DialogueSkillDecisionObserver {
    private val lock = Any()
    private val decisions = mutableListOf<DialogueSkillDecision>()
    private val localSkillErrors = mutableListOf<String>()
    private val responseSources = mutableListOf<GateCHybridResponseSource>()

    override fun onDecision(decision: DialogueSkillDecision) {
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
 * Explicit developer-only Gate C hybrid backend.
 *
 * The selected phone-local model may classify only bounded conversational repair skills. Exact
 * spoken text is application-owned. Any model error, low confidence or TAKE_OVER classification
 * falls through exactly once to the existing ChatRelay mailbox, where a host/ChatGPT response is
 * still subject to the normal application output-approval policy in LocalTextCallSession.
 */
internal object GateCHybridDialogueBackendFactory {
    fun create(
        context: Context,
        provider: TextLlmProvider,
        relaySessionId: String,
        diagnostics: GateCHybridDiagnostics,
    ): TextCallAgentBackend {
        require(
            provider == TextLlmProvider.LOCAL_PHONE_LLM || provider == TextLlmProvider.LOCAL_GEMMA_4,
        ) { "gate_c_hybrid_requires_phone_local_model" }
        ChatRelayEnvelope(relaySessionId, 1, "probe").validate()

        val skillPolicy = DialogueSkillPolicy(
            allowedResponses = mapOf(
                DialogueSkillId.ASK_REPEAT to "Proszę powtórzyć.",
                DialogueSkillId.ASK_CLARIFY to "Proszę doprecyzować.",
                DialogueSkillId.ACKNOWLEDGE_NEUTRAL to "Rozumiem.",
            ),
            minimumConfidence = 0.72,
        )
        val localSkillBackend = LocalDialogueSkillBackendFactory.create(
            context = context.applicationContext,
            provider = provider,
            policy = skillPolicy,
            observer = diagnostics,
        )
        val relayBackend = InteractiveChatRelayBackend(
            mailbox = ChatRelayMailbox(File(context.filesDir, ChatRelayMailbox.DIRECTORY_NAME)),
            sessionId = relaySessionId,
        )
        return compose(localSkillBackend, relayBackend, diagnostics)
    }

    internal fun compose(
        localSkillBackend: TextCallAgentBackend,
        relayBackend: TextCallAgentBackend,
        diagnostics: GateCHybridDiagnostics,
    ): TextCallAgentBackend {
        val observedLocalSkillBackend = localSkillBackend.observed(
            onComplete = {
                diagnostics.recordResponseSource(GateCHybridResponseSource.LOCAL_SKILL)
            },
            onError = diagnostics::recordLocalSkillError,
        )
        val observedRelayBackend = relayBackend.observed(
            onComplete = {
                diagnostics.recordResponseSource(GateCHybridResponseSource.CHAT_RELAY)
            },
        )
        return FailoverTextCallAgentBackend(
            primary = observedLocalSkillBackend,
            fallback = observedRelayBackend,
        )
    }

    private fun TextCallAgentBackend.observed(
        onComplete: () -> Unit,
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
