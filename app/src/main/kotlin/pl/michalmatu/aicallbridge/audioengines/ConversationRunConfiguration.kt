package pl.michalmatu.aicallbridge.audioengines

import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** One immutable attempt, independent of task, Telecom and mutable preferences. */
internal sealed interface ConversationRunConfiguration {
    val audioProfile: AudioEngineProfile

    data class ScriptAssisted(override val audioProfile: AudioEngineProfile) : ConversationRunConfiguration

    data class ModelDirect(
        override val audioProfile: AudioEngineProfile,
        val systemInstruction: String,
    ) : ConversationRunConfiguration {
        /** Cache and cold creation must refer to the same selected model and prompt. */
        fun acquireModel(
            takePrepared: (AudioEngineProfile, String) -> TextCallAgentBackend?,
            create: (AudioEngineProfile, String) -> TextCallAgentBackend,
        ): TextCallAgentBackend = takePrepared(audioProfile, systemInstruction)
            ?: create(audioProfile, systemInstruction)

        override fun toString(): String =
            "ModelDirect(audioProfile=$audioProfile, systemInstruction=REDACTED)"
    }
}
