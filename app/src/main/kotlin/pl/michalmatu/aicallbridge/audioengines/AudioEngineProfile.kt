package pl.michalmatu.aicallbridge.audioengines

/** Preferences, never call authority. No profile contains a target, grant or prompt. */
internal enum class SpeechInputId { ANDROID_STT, PARAKEET_PL, GEMMA_AUDIO_DIRECT }
internal enum class DialogueModelId {
    GEMMA_4_E2B, GEMMA_4_E4B, QWEN_3_1_7B, QWEN_3_0_6B, QWEN_3_5_2B, USER_LITERT;

    val requiresTextCheck: Boolean get() = this == GEMMA_4_E4B || this == USER_LITERT

    companion object {
        val builtInChoices = listOf(GEMMA_4_E2B, GEMMA_4_E4B)
    }
}
internal enum class SpeechOutputId { ANDROID_TTS_PL, PIPER_PL, KOKORO, POCKET_TTS }

internal data class AudioEngineProfile(
    val input: SpeechInputId = SpeechInputId.ANDROID_STT,
    val dialogue: DialogueModelId = DialogueModelId.GEMMA_4_E2B,
    val output: SpeechOutputId = SpeechOutputId.ANDROID_TTS_PL,
    val languageTag: String = "pl-PL",
    val voiceId: String? = null,
    /** Required only for a model explicitly imported from a LiteRT-LM file. */
    val customModelId: String? = null,
    val ttsEnginePackage: String? = null,
    val generation: DialogueGenerationSettings = DialogueGenerationSettings.DEFAULTS,
) {
    val speechLanguage: String get() = languageTag.substringBefore('-')

    val effectiveTtsEnginePackage: String? get() = ttsEnginePackage
        ?: if (output == SpeechOutputId.PIPER_PL) PiperServiceContract.PACKAGE else null
    companion object {
        val STABLE = AudioEngineProfile(languageTag = "en-US")
    }
}

/** Available configuration choices. Installation and native readiness are checked separately. */
internal data class AudioEngineChoice(val id: String, val title: String, val advanced: Boolean = false)

internal object AudioEngineRegistry {
    val inputs = listOf(
        AudioEngineChoice("ANDROID_STT", "Android STT"),
        AudioEngineChoice("PARAKEET_PL", "Parakeet", advanced = true),
        AudioEngineChoice("GEMMA_AUDIO_DIRECT", "Gemma · audio"),
    )
    val dialogues = listOf(
        AudioEngineChoice(DialogueModelId.GEMMA_4_E2B.name, "Gemma 4 E2B"),
        AudioEngineChoice(DialogueModelId.GEMMA_4_E4B.name, "Gemma 4 E4B"),
        AudioEngineChoice(DialogueModelId.USER_LITERT.name, "Imported LiteRT-LM model"),
    )
    val outputs = listOf(AudioEngineChoice("ANDROID_TTS_PL", "Android TTS"))

    /** An unsupported composition never silently falls back to another engine. */
    fun incompatibility(profile: AudioEngineProfile): String? = when {
        !profile.generation.valid() -> "invalid_dialogue_generation_settings"
        profile.dialogue == DialogueModelId.USER_LITERT &&
            (profile.customModelId == null || !Regex("user-[0-9a-f]{64}").matches(profile.customModelId)) ->
            "user_model_identity_invalid"
        profile.dialogue == DialogueModelId.QWEN_3_5_2B -> "qwen35_lab_only_unqualified"
        profile.languageTag !in setOf("pl-PL", "en-US") -> "unsupported_language"
        profile.languageTag == "en-US" && profile.input != SpeechInputId.ANDROID_STT ->
            "english_android_stt_required"
        profile.languageTag == "en-US" &&
            (profile.output != SpeechOutputId.ANDROID_TTS_PL ||
                profile.effectiveTtsEnginePackage == PiperServiceContract.PACKAGE) ->
            "english_android_tts_required"
        profile.input == SpeechInputId.GEMMA_AUDIO_DIRECT && !supportsAudio(profile) ->
            "model_audio_input_unavailable"
        profile.output == SpeechOutputId.KOKORO -> "kokoro_no_polish"
        profile.output == SpeechOutputId.POCKET_TTS -> "pocket_tts_no_polish"
        profile.voiceId != null &&
            (profile.voiceId.isBlank() || profile.voiceId.length > 160) -> "invalid_voice_id"
        profile.ttsEnginePackage != null &&
            !Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(profile.ttsEnginePackage) ->
            "invalid_tts_engine"
        else -> null
    }

    fun supportsAudio(profile: AudioEngineProfile): Boolean =
        profile.dialogue == DialogueModelId.GEMMA_4_E2B ||
            (profile.dialogue == DialogueModelId.USER_LITERT && profile.customModelId ==
                "user-" + pl.michalmatu.aicallbridge.textagent.Gemma4ModelCatalog.GEMMA_4_E2B_IT.sha256)
}
