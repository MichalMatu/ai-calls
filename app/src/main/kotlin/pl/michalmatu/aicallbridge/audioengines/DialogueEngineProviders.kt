package pl.michalmatu.aicallbridge.audioengines

import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

import android.content.Context
import pl.michalmatu.aicallbridge.textagent.DialogueActionId
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.NativeModelTiming
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Swappable text-dialogue boundary. Inference cannot authorize a call, a fact, or external effect.
 * Every caller still routes model output through the existing application approval layer.
 */
internal interface DialogueEngineProvider {
    val id: DialogueModelId
    fun createDirect(context: Context, systemInstruction: String): TextCallAgentBackend
    fun createConversationReply(context: Context, systemInstruction: String, tools: Set<ConversationToolId>): TextCallAgentBackend
    /** Explicit native budget for off-call measurements; no change to call permissions. */
    fun createLaboratoryConversationReply(context: Context, systemInstruction: String,
        tools: Set<ConversationToolId>, contextTokenBudget: Int = 4_096,
        onTiming: (NativeModelTiming) -> Unit = {}): TextCallAgentBackend =
        createConversationReply(context, systemInstruction, tools)
    fun createActionClassifier(
        context: Context,
        systemInstruction: String,
        allowedActions: Set<DialogueActionId>,
    ): TextCallAgentBackend
}

/** Every catalog model uses the same native lifecycle and application output approval. */
internal class LiteRtDialogueEngineProvider(override val id: DialogueModelId,
    private val audioInput: Boolean = false,
    private val profile: AudioEngineProfile? = null) : DialogueEngineProvider {
    override fun createConversationReply(context: Context, systemInstruction: String, tools: Set<ConversationToolId>): TextCallAgentBackend =
        Gemma4LiteRtTextBackendFactory.createConversationReply(context, systemInstruction, id, tools,
            audioInput = audioInput,
            selectedFile = profile?.let { Gemma4LiteRtTextBackendFactory.modelFile(context, it) })

    override fun createLaboratoryConversationReply(context: Context, systemInstruction: String,
        tools: Set<ConversationToolId>, contextTokenBudget: Int,
        onTiming: (NativeModelTiming) -> Unit): TextCallAgentBackend =
        Gemma4LiteRtTextBackendFactory.createConversationReply(context, systemInstruction, id,
            tools, contextTokenBudget = contextTokenBudget, onTiming = onTiming)

    override fun createDirect(context: Context, systemInstruction: String): TextCallAgentBackend =
        Gemma4LiteRtTextBackendFactory.createDirectDialogue(context, systemInstruction, id)

    override fun createActionClassifier(
        context: Context, systemInstruction: String, allowedActions: Set<DialogueActionId>,
    ): TextCallAgentBackend = Gemma4LiteRtTextBackendFactory.createActionClassifier(
        context, systemInstruction, allowedActions, id,
    )
}

internal object DialogueEngineProviders {
    fun resolve(profile: AudioEngineProfile): DialogueEngineProvider {
        require(AudioEngineRegistry.incompatibility(profile) == null) {
            "dialogue_engine_profile_unavailable"
        }
        return LiteRtDialogueEngineProvider(profile.dialogue, profile.input == SpeechInputId.GEMMA_AUDIO_DIRECT, profile)
    }
}
