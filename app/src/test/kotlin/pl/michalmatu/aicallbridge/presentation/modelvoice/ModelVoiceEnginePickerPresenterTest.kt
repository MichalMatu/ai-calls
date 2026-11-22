package pl.michalmatu.aicallbridge.presentation.modelvoice

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.PiperServiceContract

class ModelVoiceEnginePickerPresenterTest {
    private val engines = listOf<String?>(null, "org.example.engine", PiperServiceContract.PACKAGE)
    private val labels = listOf("System", "Installed", "Piper")

    @Test fun polishShowsAllAvailableEnginesAndSelectsSystem() {
        val options = ModelVoiceEnginePickerPresenter.present(engines, labels, AudioEngineProfile()) {
            error("missing label must be evaluated lazily")
        }
        assertEquals(engines, options.ids)
        assertEquals(labels, options.labels)
        assertEquals(0, options.selected)
    }

    @Test fun englishHidesPiperWithoutRemovingOtherEngines() {
        val options = ModelVoiceEnginePickerPresenter.present(
            engines, labels, AudioEngineProfile(languageTag = "en-US")) {
            error("missing label must be evaluated lazily")
        }
        assertEquals(engines.dropLast(1), options.ids)
        assertEquals(labels.dropLast(1), options.labels)
        assertEquals(0, options.selected)
    }

    @Test fun unavailableSavedEngineIsRepresentedAndRemainsSelected() {
        var requests = 0
        val options = ModelVoiceEnginePickerPresenter.present(
            engines, labels, AudioEngineProfile(languageTag = "en-US",
                ttsEnginePackage = PiperServiceContract.PACKAGE)) {
            requests++
            "Unavailable"
        }
        assertEquals(engines, options.ids)
        assertEquals(listOf("System", "Installed", "Unavailable"), options.labels)
        assertEquals(2, options.selected)
        assertEquals(1, requests)
    }

    @Test fun legacyZipAndMissingEngineBehaviorIsPreserved() {
        val options = ModelVoiceEnginePickerPresenter.present(
            listOf<String?>(null, "org.example.engine", "unlabelled"),
            listOf("System", "Installed"),
            AudioEngineProfile(ttsEnginePackage = "org.example.missing")) { "Unavailable" }
        assertEquals(listOf<String?>(null, "org.example.engine", "org.example.missing"), options.ids)
        assertEquals(listOf("System", "Installed", "Unavailable"), options.labels)
        assertEquals(2, options.selected)
    }
}
