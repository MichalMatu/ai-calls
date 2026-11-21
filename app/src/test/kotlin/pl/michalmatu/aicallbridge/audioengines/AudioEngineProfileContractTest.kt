package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class AudioEngineProfileContractTest {
    @Test fun stableProfilePreservesTheShippedPath() {
        val value = AudioEngineProfile.STABLE
        assertEquals(SpeechInputId.ANDROID_STT, value.input)
        assertEquals(DialogueModelId.GEMMA_4_E2B, value.dialogue)
        assertEquals(SpeechOutputId.ANDROID_TTS_PL, value.output)
        assertEquals("pl-PL", value.languageTag)
        assertNull(value.voiceId)
        assertNull(AudioEngineRegistry.incompatibility(value))
    }

    @Test fun generationDefaultsPreserveEngineBudgetsAndRejectInvalidOverrides() {
        val defaults = AudioEngineProfile.STABLE.generation
        assertEquals(4_096, defaults.contextBudget(DialogueModelId.GEMMA_4_E2B, structured = true))
        assertEquals(2_048, defaults.contextBudget(DialogueModelId.USER_LITERT, structured = true))
        assertEquals(2_048, defaults.contextBudget(DialogueModelId.GEMMA_4_E2B, structured = false))
        val custom = DialogueGenerationSettings(6_144, 768, 0.4, 16, 0.9)
        assertTrue(custom.valid())
        assertEquals(6_144, custom.contextBudget(DialogueModelId.GEMMA_4_E2B, structured = true))
        for (invalid in listOf(custom.copy(contextTokens = 0), custom.copy(outputTokens = 2048),
            custom.copy(temperature = Double.NaN), custom.copy(topK = 0), custom.copy(topP = 0.0))) {
            assertEquals("invalid_dialogue_generation_settings", AudioEngineRegistry.incompatibility(
                AudioEngineProfile(generation = invalid)))
        }
    }

    @Test fun unimplementedEnginesCannotMasqueradeAsReady() {
        assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT)))
        assertEquals("model_audio_input_unavailable", AudioEngineRegistry.incompatibility(
            AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT, dialogue = DialogueModelId.QWEN_3_1_7B)))
        assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(input = SpeechInputId.PARAKEET_PL)))
        assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(output = SpeechOutputId.PIPER_PL)))
        for (model in DialogueModelId.entries) {
            val status = AudioEngineRegistry.incompatibility(AudioEngineProfile(dialogue = model))
            when (model) {
                DialogueModelId.QWEN_3_5_2B -> assertEquals("qwen35_lab_only_unqualified", status)
                DialogueModelId.USER_LITERT -> assertEquals("user_model_identity_invalid", status)
                else -> assertNull(status)
            }
        }
        for (output in listOf(SpeechOutputId.KOKORO, SpeechOutputId.POCKET_TTS)) {
            assertNotNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(output = output)))
        }
    }

    @Test fun rejectsUnknownOrUnqualifiedLanguagesWithoutFallback() {
        assertNull(SpeechInputId.entries.firstOrNull { it.name == "FUTURE_MODEL" })
        assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(languageTag = "en-US")))
        assertEquals("unsupported_language", AudioEngineRegistry.incompatibility(
            AudioEngineProfile(languageTag = "de-DE"),
        ))
        assertEquals("invalid_voice_id", AudioEngineRegistry.incompatibility(
            AudioEngineProfile(voiceId = " "),
        ))
    }


    @Test fun fullEngineCombinationMatrixNeverSilentlyDowngrades() {
        for (input in SpeechInputId.entries) {
            for (dialogue in DialogueModelId.entries) {
                for (output in SpeechOutputId.entries) {
                    val profile = AudioEngineProfile(input = input, dialogue = dialogue, output = output)
                    val shouldBeIntegrated = (input != SpeechInputId.GEMMA_AUDIO_DIRECT || dialogue == DialogueModelId.GEMMA_4_E2B) &&
                        output != SpeechOutputId.KOKORO &&
                        output != SpeechOutputId.POCKET_TTS &&
                        dialogue != DialogueModelId.QWEN_3_5_2B &&
                        dialogue != DialogueModelId.USER_LITERT
                    assertEquals(
                        "Wrong static integration for $input/$dialogue/$output",
                        shouldBeIntegrated,
                        AudioEngineRegistry.incompatibility(profile) == null,
                    )
                }
            }
        }
    }

    @Test fun importedModelNeedsExactFileIdentity() {
        assertEquals("user_model_identity_invalid", AudioEngineRegistry.incompatibility(
            AudioEngineProfile(dialogue = DialogueModelId.USER_LITERT)))
        assertEquals("user_model_identity_invalid", AudioEngineRegistry.incompatibility(
            AudioEngineProfile(dialogue = DialogueModelId.USER_LITERT,
                customModelId = "../outside")))
        assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(
            dialogue = DialogueModelId.USER_LITERT, customModelId = "user-" + "a".repeat(64))))
    }

    @Test fun selectedOfflineVoiceIsASeparatePreferenceNotACapability() {
        val selected = AudioEngineProfile(voiceId = "pl-pl-x-local")
        assertNull(AudioEngineRegistry.incompatibility(selected))
        assertEquals(null, AudioEngineProfile.STABLE.voiceId)
    }
}
