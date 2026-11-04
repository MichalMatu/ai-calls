package pl.michalmatu.aicallbridge.audioengines

/** Preferences, never call authority. No profile contains a target, grant or prompt. */
internal enum class SpeechInputId { ANDROID_STT, PARAKEET_PL, GEMMA_AUDIO_DIRECT }
internal enum class DialogueModelId { GEMMA_4_E2B, GEMMA_4_E4B }
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
            "Eksperymentalne · brak adaptera PCM w LiteRT", false),
    )
    val dialogues = listOf(
        AudioEngineChoice("GEMMA_4_E2B", "Gemma 4 E2B", "pl-PL",
            "Istniejący lokalny backend RAW / SCRIPT", true),
        AudioEngineChoice("GEMMA_4_E4B", "Gemma 4 E4B", "pl-PL",
            "Brak wdrożonej instancji modelu i pomiarów pamięci", false),
    )
    val outputs = listOf(
        AudioEngineChoice("ANDROID_TTS_PL", "Android TTS", "pl-PL",
            "Lokalny głos systemowy · PCM16 16 kHz", true),
        AudioEngineChoice("PIPER_PL", "Piper PL", "pl-PL",
            "Brak zatwierdzonego modelu i adaptera Android", false),
        AudioEngineChoice("KOKORO", "Kokoro 82M", "brak pl-PL",
            "Obecna paczka nie obsługuje polskiego", false),
        AudioEngineChoice("POCKET_TTS", "PocketTTS 100M", "brak pl-PL",
            "Obecna paczka nie obsługuje polskiego", false),
    )

    /** An unsupported composition never silently falls back to another engine. */
    fun incompatibility(profile: AudioEngineProfile): String? = when {
        profile.languageTag != "pl-PL" -> "unsupported_language"
        profile.input == SpeechInputId.GEMMA_AUDIO_DIRECT -> "audio_direct_not_integrated"
        profile.dialogue == DialogueModelId.GEMMA_4_E4B -> "e4b_not_integrated"
        profile.output == SpeechOutputId.PIPER_PL -> "piper_not_integrated"
        profile.output == SpeechOutputId.KOKORO -> "kokoro_no_polish"
        profile.output == SpeechOutputId.POCKET_TTS -> "pocket_tts_no_polish"
        profile.voiceId != null &&
            (profile.voiceId.isBlank() || profile.voiceId.length > 160) -> "invalid_voice_id"
        else -> null
    }
}
