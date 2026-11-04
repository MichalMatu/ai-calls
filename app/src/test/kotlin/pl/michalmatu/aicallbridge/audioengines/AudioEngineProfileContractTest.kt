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
        for (input in listOf(SpeechInputId.PARAKEET_PL, SpeechInputId.GEMMA_AUDIO_DIRECT)) {
            assertNotNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(input = input)))
        }
        assertNotNull(AudioEngineRegistry.incompatibility(
            AudioEngineProfile(dialogue = DialogueModelId.GEMMA_4_E4B),
        ))
        for (output in listOf(SpeechOutputId.PIPER_PL, SpeechOutputId.KOKORO, SpeechOutputId.POCKET_TTS)) {
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

    @Test fun selectedOfflineVoiceIsASeparatePreferenceNotACapability() {
        val selected = AudioEngineProfile(voiceId = "pl-pl-x-local")
        assertNull(AudioEngineRegistry.incompatibility(selected))
        assertEquals(null, AudioEngineProfile.STABLE.voiceId)
    }
}
