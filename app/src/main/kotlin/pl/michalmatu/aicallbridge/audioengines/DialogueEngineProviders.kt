package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import pl.michalmatu.aicallbridge.textagent.DialogueActionId
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Swappable text-dialogue boundary. Inference cannot authorize a call, a fact, or external effect.
 * Every caller still routes model output through the existing application approval layer.
 */
internal interface DialogueEngineProvider {
    val id: DialogueModelId
    fun createDirect(context: Context, systemInstruction: String): TextCallAgentBackend
    fun createActionClassifier(
        context: Context,
        systemInstruction: String,
        allowedActions: Set<DialogueActionId>,
    ): TextCallAgentBackend
}

/** Exact pre-existing LiteRT-LM Gemma 4 E2B runtime; no new model path or native options. */
internal object Gemma4E2BDialogueEngineProvider : DialogueEngineProvider {
    override val id = DialogueModelId.GEMMA_4_E2B

    override fun createDirect(context: Context, systemInstruction: String): TextCallAgentBackend =
        Gemma4LiteRtTextBackendFactory.createDirectDialogue(context, systemInstruction)

    override fun createActionClassifier(
        context: Context,
        systemInstruction: String,
        allowedActions: Set<DialogueActionId>,
    ): TextCallAgentBackend =
        Gemma4LiteRtTextBackendFactory.createActionClassifier(
            context, systemInstruction, allowedActions,
        )
}

internal object DialogueEngineProviders {
    fun resolve(profile: AudioEngineProfile): DialogueEngineProvider {
        require(AudioEngineRegistry.incompatibility(profile) == null) {
            "dialogue_engine_profile_unavailable"
        }
        return when (profile.dialogue) {
            DialogueModelId.GEMMA_4_E2B -> Gemma4E2BDialogueEngineProvider
            DialogueModelId.GEMMA_4_E4B -> error("e4b_dialogue_provider_not_integrated")
        }
    }
}
