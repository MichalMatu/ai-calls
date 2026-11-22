package pl.michalmatu.aicallbridge.presentation.modelvoice

import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.PiperServiceContract

/** Read-only options for the TTS-engine picker. Android still owns the dialog. */
internal object ModelVoiceEnginePickerPresenter {
    data class Options(val ids: List<String?>, val labels: List<String>, val selected: Int)

    fun present(
        engineIds: List<String?>,
        engineLabels: List<String>,
        profile: AudioEngineProfile,
        missingLabel: () -> String,
    ): Options {
        val allowed = engineIds.zip(engineLabels).filter { pair ->
            profile.languageTag != "en-US" || pair.first != PiperServiceContract.PACKAGE
        }
        val ids = allowed.map { it.first }.toMutableList()
        val labels = allowed.map { it.second }.toMutableList()
        if (profile.effectiveTtsEnginePackage !in ids) {
            ids.add(profile.effectiveTtsEnginePackage)
            labels.add(missingLabel())
        }
        return Options(ids, labels, ids.indexOf(profile.effectiveTtsEnginePackage))
    }
}
