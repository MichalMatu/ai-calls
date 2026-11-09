package pl.michalmatu.aicallbridge.audioengines

/** Preferences, never call authority. No profile contains a target, grant or prompt. */
internal enum class SpeechInputId { ANDROID_STT, PARAKEET_PL, GEMMA_AUDIO_DIRECT }
internal enum class DialogueModelId { GEMMA_4_E2B, GEMMA_4_E4B, QWEN_3_1_7B, QWEN_3_0_6B, QWEN_3_5_2B }
internal enum class SpeechOutputId { ANDROID_TTS_PL, PIPER_PL, KOKORO, POCKET_TTS }

internal data class AudioEngineProfile(
    val input: SpeechInputId = SpeechInputId.ANDROID_STT,
    val dialogue: DialogueModelId = DialogueModelId.GEMMA_4_E2B,
    val output: SpeechOutputId = SpeechOutputId.ANDROID_TTS_PL,
    val languageTag: String = "pl-PL",
    val voiceId: String? = null,
) {
    companion object {
        val STABLE = AudioEngineProfile()
    }
}

/** This is a technical feature matrix, not a claim that a model has been installed. */
internal data class AudioEngineChoice(
    val id: String,
    val title: String,
    val languageTag: String,
    val detail: String,
    val integrated: Boolean,
)

internal object AudioEngineRegistry {
    val inputs = listOf(
        AudioEngineChoice("ANDROID_STT", "Android STT", "pl-PL", "PCM16 16 kHz · offline", true),
        AudioEngineChoice("PARAKEET_PL", "Parakeet TDT 0.6B v3", "pl-PL",
            "Adapter MediaPipe 1.2.0; wymagany model 615 MB i test native S22", true),
        AudioEngineChoice("GEMMA_AUDIO_DIRECT", "Gemma Audio Direct", "pl-PL",
            "Eksperymentalny adapter PCM→AudioBytes; brak dowodu S22 i pamięci", false),
    )
    val dialogues = pl.michalmatu.aicallbridge.textagent.LanguageModelCatalog.entries.map {
        AudioEngineChoice(it.id.name, it.title, "pl-PL", it.detail,
            it.id != DialogueModelId.QWEN_3_5_2B)
    }
    val outputs = listOf(
        AudioEngineChoice("ANDROID_TTS_PL", "Android TTS", "pl-PL",
            "Lokalny głos systemowy · PCM16 16 kHz", true),
        AudioEngineChoice("PIPER_PL", "Piper PL", "pl-PL",
            "Systemowy silnik Piper: wymagane osobne APK i polski głos offline", true),
        AudioEngineChoice("KOKORO", "Kokoro 82M", "brak pl-PL",
            "Obecna paczka nie obsługuje polskiego", false),
        AudioEngineChoice("POCKET_TTS", "PocketTTS 100M", "brak pl-PL",
            "Obecna paczka nie obsługuje polskiego", false),
    )

    /** An unsupported composition never silently falls back to another engine. */
    fun incompatibility(profile: AudioEngineProfile): String? = when {
        profile.dialogue == DialogueModelId.QWEN_3_5_2B -> "qwen35_lab_only_unqualified"
        profile.languageTag != "pl-PL" -> "unsupported_language"
        profile.input == SpeechInputId.GEMMA_AUDIO_DIRECT -> "audio_direct_s22_unqualified"
        profile.output == SpeechOutputId.KOKORO -> "kokoro_no_polish"
        profile.output == SpeechOutputId.POCKET_TTS -> "pocket_tts_no_polish"
        profile.voiceId != null &&
            (profile.voiceId.isBlank() || profile.voiceId.length > 160) -> "invalid_voice_id"
        else -> null
    }
}
