package pl.michalmatu.aicallbridge.presentation.modelvoice

import pl.michalmatu.aicallbridge.audioengines.AudioEngineChoice
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry
import pl.michalmatu.aicallbridge.audioengines.PiperServiceContract
import pl.michalmatu.aicallbridge.audioengines.SpeechInputId

/** Pure picker choices: Android owns labels, dialogs, saving and voice playback. */
internal object ModelVoiceSpeechPickerPresenter {
    data class InputChoice(
        val input: SpeechInputId,
        val showAdvancedHeading: Boolean,
        val selected: Boolean,
        val enabled: Boolean,
    )

    fun inputOptions(profile: AudioEngineProfile, available: List<AudioEngineChoice>): List<InputChoice> {
        val ordered = available.filter { !it.advanced } + available.filter { it.advanced }
        return ordered.map { choice ->
            val input = SpeechInputId.valueOf(choice.id)
            InputChoice(
                input = input,
                showAdvancedHeading = choice.advanced,
                selected = profile.input == input,
                enabled = (profile.languageTag != "en-US" || input == SpeechInputId.ANDROID_STT) &&
                    (input != SpeechInputId.GEMMA_AUDIO_DIRECT || AudioEngineRegistry.supportsAudio(profile)),
            )
        }
    }

    data class VoiceChoice(val id: String?, val position: Int, val selected: Boolean)
    enum class MissingVoice { PIPER, GENERIC }
    data class VoiceOptions(
        val engineLabel: String,
        val loading: Boolean,
        val missingVoice: MissingVoice?,
        val rows: List<VoiceChoice>,
    )

    fun voiceOptions(
        profile: AudioEngineProfile,
        engineIds: List<String?>,
        engineLabels: List<String>,
        localVoiceIds: List<String>,
        voicesReady: Boolean,
        unavailableEngineLabel: String,
    ): VoiceOptions {
        val label = engineLabels.getOrNull(engineIds.indexOf(profile.effectiveTtsEnginePackage))
            ?: unavailableEngineLabel
        if (!voicesReady) return VoiceOptions(label, loading = true, missingVoice = null, rows = emptyList())
        val ids = listOf<String?>(null) + localVoiceIds
        return VoiceOptions(
            engineLabel = label,
            loading = false,
            missingVoice = when {
                localVoiceIds.isNotEmpty() -> null
                profile.effectiveTtsEnginePackage == PiperServiceContract.PACKAGE -> MissingVoice.PIPER
                else -> MissingVoice.GENERIC
            },
            rows = ids.mapIndexed { index, id ->
                VoiceChoice(id = id, position = index, selected = profile.voiceId == id)
            },
        )
    }
}
