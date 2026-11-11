package pl.michalmatu.aicallbridge.audioengines

import android.content.Context

/**
 * Atomic local preference; explicitly separate from CampaignAuthorizationGrant and prompt skills.
 * No keys is a clean migration to the shipped Android STT/E2B/Android TTS path.
 * Unexpected saved identifiers are rejected, never interpreted as a new permission or engine.
 */
internal class AudioEngineProfileStore(
    context: Context,
    preferencesName: String = "audio_engine_profile_v1",
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        preferencesName, Context.MODE_PRIVATE,
    )

    fun load(): AudioEngineProfile? {
        if (!prefs.contains("version")) {
            // No previous preference means migration to the unchanged shipped baseline.
            // A partially/corruptly populated preference must not silently downgrade.
            return if (listOf("input", "dialogue", "output", "language", "voice", "custom_model_id", "tts_engine")
                .none { prefs.contains(it) }
            ) AudioEngineProfile.STABLE else null
        }
        val version = prefs.getInt("version", -1)
        if (version !in 1..2) return null
        val input = SpeechInputId.entries.firstOrNull {
            it.name == prefs.getString("input", null)
        } ?: return null
        val previouslySavedDialogue = DialogueModelId.entries.firstOrNull {
            it.name == prefs.getString("dialogue", null)
        } ?: return null
        // Remove retired built-in choices without changing the STT/TTS/voice configuration.
        val dialogue = if (previouslySavedDialogue != DialogueModelId.USER_LITERT)
            DialogueModelId.GEMMA_4_E2B else previouslySavedDialogue
        val customId = if (dialogue == DialogueModelId.USER_LITERT)
            prefs.getString("custom_model_id", null) else null
        val previousOutput = SpeechOutputId.entries.firstOrNull {
            it.name == prefs.getString("output", null)
        } ?: return null
        val output = if (previousOutput == SpeechOutputId.PIPER_PL) SpeechOutputId.ANDROID_TTS_PL else previousOutput
        val engine = prefs.getString("tts_engine", null)
            ?: if (previousOutput == SpeechOutputId.PIPER_PL) PiperServiceContract.PACKAGE else null
        val language = prefs.getString("language", null) ?: return null
        val voice = prefs.getString("voice", null)
        val profile = AudioEngineProfile(input, dialogue, output, language, voice, customId, engine)
            .takeIf { AudioEngineRegistry.incompatibility(it) == null } ?: return null
        if ((version == 1 || previouslySavedDialogue != dialogue || previousOutput != output) && !save(profile)) return null
        return profile
    }

    fun save(profile: AudioEngineProfile): Boolean {
        require(AudioEngineRegistry.incompatibility(profile) == null) {
            "engine_profile_not_supported"
        }
        return prefs.edit()
            .putInt("version", 2)
            .putString("input", profile.input.name)
            .putString("dialogue", profile.dialogue.name)
            .putString("output", profile.output.name)
            .putString("language", profile.languageTag)
            .putString("voice", profile.voiceId)
            .putString("custom_model_id", if (profile.dialogue == DialogueModelId.USER_LITERT)
                profile.customModelId else null)
            .putString("tts_engine", profile.effectiveTtsEnginePackage)
            .commit()
    }
}
