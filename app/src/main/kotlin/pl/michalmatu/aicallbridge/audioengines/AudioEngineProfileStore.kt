package pl.michalmatu.aicallbridge.audioengines

import android.content.Context

/**
 * Atomic local preference; explicitly separate from CampaignAuthorizationGrant and prompt skills.
 * No keys is a clean migration to the shipped Android STT/E2B/Android TTS path.
 * Unexpected saved identifiers are rejected, never interpreted as a new permission or engine.
 */
internal class AudioEngineProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "audio_engine_profile_v1", Context.MODE_PRIVATE,
    )

    fun load(): AudioEngineProfile? {
        if (!prefs.contains("version")) return AudioEngineProfile.STABLE
        if (prefs.getInt("version", -1) != 1) return null
        val input = SpeechInputId.entries.firstOrNull {
            it.name == prefs.getString("input", null)
        } ?: return null
        val dialogue = DialogueModelId.entries.firstOrNull {
            it.name == prefs.getString("dialogue", null)
        } ?: return null
        val output = SpeechOutputId.entries.firstOrNull {
            it.name == prefs.getString("output", null)
        } ?: return null
        val language = prefs.getString("language", null) ?: return null
        val voice = prefs.getString("voice", null)
        return AudioEngineProfile(input, dialogue, output, language, voice)
            .takeIf { AudioEngineRegistry.incompatibility(it) == null }
    }

    fun save(profile: AudioEngineProfile): Boolean {
        require(AudioEngineRegistry.incompatibility(profile) == null) {
            "engine_profile_not_supported"
        }
        return prefs.edit()
            .putInt("version", 1)
            .putString("input", profile.input.name)
            .putString("dialogue", profile.dialogue.name)
            .putString("output", profile.output.name)
            .putString("language", profile.languageTag)
            .putString("voice", profile.voiceId)
            .commit()
    }
}
