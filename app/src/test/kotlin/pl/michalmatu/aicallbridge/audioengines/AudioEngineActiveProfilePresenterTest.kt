package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class AudioEngineActiveProfilePresenterTest {
    @Test fun invalidStoredProfileIsNotSilentlyReplacedWithStableDefault() {
        assertSame(AudioEngineActiveState.Invalid, AudioEngineActiveProfilePresenter.present(null))
    }

    @Test fun stableProfileHasUserFacingEngineNamesAndAutomaticVoice() {
        val state = AudioEngineActiveProfilePresenter.present(AudioEngineProfile.STABLE)
            as AudioEngineActiveState.Configured
        assertEquals("Android STT", state.inputTitle)
        assertEquals("Gemma 4 E2B", state.dialogueTitle)
        assertEquals("Android TTS", state.outputTitle)
        assertNull(state.voiceId)
    }

    @Test fun everyIntegratedCombinationUsesExistingRegistryLabels() {
        val selections = listOf(
            AudioEngineProfile(input = SpeechInputId.PARAKEET_PL,
                dialogue = DialogueModelId.GEMMA_4_E2B,
                output = SpeechOutputId.PIPER_PL, voiceId = "pl-PL-local"),
            AudioEngineProfile(dialogue = DialogueModelId.USER_LITERT,
                customModelId = "user-" + "a".repeat(64)),
            AudioEngineProfile(dialogue = DialogueModelId.GEMMA_4_E2B),
        )
        for (profile in selections) {
            val presented = AudioEngineActiveProfilePresenter.present(profile)
                as AudioEngineActiveState.Configured
            assertEquals(AudioEngineRegistry.inputs.single { it.id == profile.input.name }.title,
                presented.inputTitle)
            assertEquals(AudioEngineRegistry.dialogues.single { it.id == profile.dialogue.name }.title,
                presented.dialogueTitle)
            assertEquals(AudioEngineRegistry.outputs.single { it.id == profile.output.name }.title,
                presented.outputTitle)
            assertEquals(profile.voiceId, presented.voiceId)
        }
    }

    @Test fun missingRegistryLabelsFallBackToExactEnumNameNotDifferentEngine() {
        val profile = AudioEngineProfile(input = SpeechInputId.PARAKEET_PL,
            dialogue = DialogueModelId.QWEN_3_0_6B, output = SpeechOutputId.PIPER_PL)
        val state = AudioEngineActiveProfilePresenter.present(
            profile, emptyList(), emptyList(), emptyList(),
        ) as AudioEngineActiveState.Configured
        assertEquals("PARAKEET_PL", state.inputTitle)
        assertEquals("QWEN_3_0_6B", state.dialogueTitle)
        assertEquals("PIPER_PL", state.outputTitle)
    }

    @Test fun summaryNeverMutatesSelectedVoiceOrPreferenceSnapshot() {
        val profile = AudioEngineProfile(voiceId = "pl-PL-offline")
        val actual = AudioEngineActiveProfilePresenter.present(profile)
            as AudioEngineActiveState.Configured
        assertEquals("pl-PL-offline", actual.voiceId)
        assertEquals(AudioEngineProfile(voiceId = "pl-PL-offline"), profile)
        assertEquals(AudioEngineProfile.STABLE, AudioEngineProfile.STABLE)
    }

    @Test fun unsupportedEngineIsNotSilentlySubstitutedInReadOnlyPresentation() {
        val profile = AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT,
            output = SpeechOutputId.KOKORO)
        val state = AudioEngineActiveProfilePresenter.present(profile)
            as AudioEngineActiveState.Configured
        assertTrue(state.inputTitle.contains("Gemma"))
        assertTrue(state.outputTitle.contains("Kokoro"))
        assertNotEquals("Android STT", state.inputTitle)
        assertNotEquals("Android TTS", state.outputTitle)
    }
}
