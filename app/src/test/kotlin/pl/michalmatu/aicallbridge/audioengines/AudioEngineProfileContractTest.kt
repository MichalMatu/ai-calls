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

    @Test fun unimplementedEnginesCannotMasqueradeAsReady() {
        for (input in listOf(SpeechInputId.GEMMA_AUDIO_DIRECT)) {
            assertNotNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(input = input)))
        }
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

    @Test fun rejectsUnknownOrNonPolishConfigurationsWithoutFallback() {
        assertNull(SpeechInputId.entries.firstOrNull { it.name == "FUTURE_MODEL" })
        assertEquals("unsupported_language", AudioEngineRegistry.incompatibility(
            AudioEngineProfile(languageTag = "en-US"),
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
                    val shouldBeIntegrated = input != SpeechInputId.GEMMA_AUDIO_DIRECT &&
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
