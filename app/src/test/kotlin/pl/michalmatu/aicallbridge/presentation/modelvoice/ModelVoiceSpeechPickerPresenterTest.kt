package pl.michalmatu.aicallbridge.presentation.modelvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audioengines.AudioEngineChoice
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.audioengines.PiperServiceContract
import pl.michalmatu.aicallbridge.audioengines.SpeechInputId

class ModelVoiceSpeechPickerPresenterTest {
    private val choices = listOf(
        AudioEngineChoice("PARAKEET_PL", "Parakeet", advanced = true),
        AudioEngineChoice("ANDROID_STT", "Android"),
        AudioEngineChoice("GEMMA_AUDIO_DIRECT", "Gemma"),
    )

    @Test fun inputOrderKeepsBasicAheadOfAdvancedAndGroupHeadings() {
        val options = ModelVoiceSpeechPickerPresenter.inputOptions(AudioEngineProfile(), choices)
        assertEquals(listOf(SpeechInputId.ANDROID_STT, SpeechInputId.GEMMA_AUDIO_DIRECT,
            SpeechInputId.PARAKEET_PL), options.map { it.input })
        assertEquals(listOf(false, false, true), options.map { it.showAdvancedHeading })
        assertEquals(listOf(true, false, false), options.map { it.selected })
    }

    @Test fun englishDisablesNonAndroidInputsWithoutRemovingThem() {
        val options = ModelVoiceSpeechPickerPresenter.inputOptions(
            AudioEngineProfile(languageTag = "en-US"), choices)
        assertEquals(listOf(true, false, false), options.map { it.enabled })
        assertEquals(3, options.size)
    }

    @Test fun unqualifiedGemmaInputRemainsDisabledButParakeetStaysVisible() {
        val profile = AudioEngineProfile(dialogue = DialogueModelId.GEMMA_4_E4B)
        val options = ModelVoiceSpeechPickerPresenter.inputOptions(profile, choices)
        assertEquals(listOf(true, false, true), options.map { it.enabled })
    }

    @Test fun qualifiedGemmaInputRemainsEnabled() {
        val options = ModelVoiceSpeechPickerPresenter.inputOptions(
            AudioEngineProfile(dialogue = DialogueModelId.GEMMA_4_E2B), choices)
        assertTrue(options.first { it.input == SpeechInputId.GEMMA_AUDIO_DIRECT }.enabled)
    }

    private val engines = listOf<String?>(null, "org.example.engine", PiperServiceContract.PACKAGE)
    private val labels = listOf("System", "Installed", "Piper")

    private fun voices(profile: AudioEngineProfile = AudioEngineProfile(),
        names: List<String> = listOf("pl-a", "pl-b"), ready: Boolean = true) =
        ModelVoiceSpeechPickerPresenter.voiceOptions(
            profile, engines, labels, names, ready, "Missing")

    @Test fun loadingPickerOnlyShowsEngineAndNoVoiceRows() {
        val state = voices(ready = false)
        assertTrue(state.loading)
        assertTrue(state.rows.isEmpty())
        assertNull(state.missingVoice)
        assertEquals("System", state.engineLabel)
    }

    @Test fun systemAutoVoiceAndSortedInventoryKeepNumericPositions() {
        val state = voices()
        assertFalse(state.loading)
        assertEquals(listOf(null, "pl-a", "pl-b"), state.rows.map { it.id })
        assertEquals(listOf(0, 1, 2), state.rows.map { it.position })
        assertEquals(listOf(true, false, false), state.rows.map { it.selected })
    }

    @Test fun selectedVoicePreservedAndMissingVoiceRemainsUnchecked() {
        assertEquals(listOf(false, true, false),
            voices(profile = AudioEngineProfile(voiceId = "pl-a")).rows.map { it.selected })
        assertTrue(voices(profile = AudioEngineProfile(voiceId = "removed")).rows.none { it.selected })
    }

    @Test fun emptyInventoryStillShowsAutomaticVoiceAndEngineSpecificWarning() {
        val system = voices(names = emptyList())
        assertEquals(ModelVoiceSpeechPickerPresenter.MissingVoice.GENERIC, system.missingVoice)
        assertEquals(listOf(null), system.rows.map { it.id })
        assertEquals(ModelVoiceSpeechPickerPresenter.MissingVoice.PIPER,
            voices(AudioEngineProfile(ttsEnginePackage = PiperServiceContract.PACKAGE),
                names = emptyList()).missingVoice)
    }

    @Test fun unavailableExplicitEngineUsesFallbackLabel() {
        val state = voices(AudioEngineProfile(ttsEnginePackage = "org.example.notInstalled"))
        assertEquals("Missing", state.engineLabel)
        assertEquals(3, state.rows.size)
    }

    @Test fun originalPickerInputListNotMutated() {
        ModelVoiceSpeechPickerPresenter.inputOptions(AudioEngineProfile(), choices)
        assertEquals("PARAKEET_PL", choices.first().id)
    }
}
