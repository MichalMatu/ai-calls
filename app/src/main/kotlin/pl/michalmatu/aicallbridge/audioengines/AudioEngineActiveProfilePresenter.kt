package pl.michalmatu.aicallbridge.audioengines

/** Immutable view-only snapshot, never preference authority or runtime readiness. */
internal sealed interface AudioEngineActiveState {
    data object Invalid : AudioEngineActiveState
    data class Configured(
        val inputTitle: String,
        val dialogueTitle: String,
        val outputTitle: String,
        val voiceId: String?,
    ) : AudioEngineActiveState
}

/**
 * Resolve the existing engine labels once. A corrupt profile stays Invalid; a
 * missing catalog label uses the exact enum identifier instead of claiming
 * that another engine was selected or initialized.
 */
internal object AudioEngineActiveProfilePresenter {
    fun present(
        profile: AudioEngineProfile?,
        inputs: List<AudioEngineChoice> = AudioEngineRegistry.inputs,
        dialogues: List<AudioEngineChoice> = AudioEngineRegistry.dialogues,
        outputs: List<AudioEngineChoice> = AudioEngineRegistry.outputs,
    ): AudioEngineActiveState {
        if (profile == null) return AudioEngineActiveState.Invalid
        fun title(choices: List<AudioEngineChoice>, id: String): String =
            choices.firstOrNull { it.id == id }?.title ?: id
        return AudioEngineActiveState.Configured(
            inputTitle = title(inputs, profile.input.name),
            dialogueTitle = title(dialogues, profile.dialogue.name),
            outputTitle = title(outputs, profile.output.name),
            voiceId = profile.voiceId,
        )
    }
}
