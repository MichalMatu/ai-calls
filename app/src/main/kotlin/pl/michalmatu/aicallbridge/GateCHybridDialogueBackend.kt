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
        observer: DialogueSkillDecisionObserver? = null,
    ): TextCallAgentBackend {
        require(
            provider == TextLlmProvider.LOCAL_PHONE_LLM || provider == TextLlmProvider.EDGE_GALLERY,
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
            observer = observer,
        )
        val relayBackend = InteractiveChatRelayBackend(
            mailbox = ChatRelayMailbox(File(context.filesDir, ChatRelayMailbox.DIRECTORY_NAME)),
            sessionId = relaySessionId,
        )
        return FailoverTextCallAgentBackend(
            primary = localSkillBackend,
            fallback = relayBackend,
        )
    }
}

internal class GateCHybridDecisionLog : DialogueSkillDecisionObserver {
    private val lock = Any()
    private val decisions = mutableListOf<DialogueSkillDecision>()

    override fun onDecision(decision: DialogueSkillDecision) {
        synchronized(lock) { decisions += decision }
    }

    fun snapshot(): List<DialogueSkillDecision> = synchronized(lock) { decisions.toList() }
}
