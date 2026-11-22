package pl.michalmatu.aicallbridge.audioengines

/**
 * Pure selection policy for the model/voice editor.
 * The Activity still decides whether the picker changed and when to persist.
 */
internal object ModelVoiceSelectionPolicy {
    fun forLanguage(profile: AudioEngineProfile, target: String): AudioEngineProfile =
        profile.copy(languageTag = target,
            input = if (target == "en-US") SpeechInputId.ANDROID_STT else profile.input,
            output = if (target == "en-US") SpeechOutputId.ANDROID_TTS_PL else profile.output,
            voiceId = null,
            ttsEnginePackage = if (target == "en-US" &&
                profile.effectiveTtsEnginePackage == PiperServiceContract.PACKAGE)
                null else profile.ttsEnginePackage)
}
