package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Characterizes the previous inline picker transformation, including saved voice reset. */
class ModelVoiceSelectionPolicyTest {
    @Test fun englishUsesAndroidSpeechAndDropsPiper() {
        val previous = AudioEngineProfile(
            input = SpeechInputId.PARAKEET_PL,
            output = SpeechOutputId.PIPER_PL,
            ttsEnginePackage = PiperServiceContract.PACKAGE,
            voiceId = "polish-local-voice",
        )
        val selected = ModelVoiceSelectionPolicy.forLanguage(previous, "en-US")
        assertEquals("en-US", selected.languageTag)
        assertEquals(SpeechInputId.ANDROID_STT, selected.input)
        assertEquals(SpeechOutputId.ANDROID_TTS_PL, selected.output)
        assertNull(selected.voiceId)
        assertNull(selected.ttsEnginePackage)
        assertEquals("pl-PL", previous.languageTag)
    }

    @Test fun englishKeepsNonPiperEngineAsBefore() {
        val previous = AudioEngineProfile(
            input = SpeechInputId.GEMMA_AUDIO_DIRECT,
            ttsEnginePackage = "com.example.voice",
            voiceId = "selected",
        )
        val next = ModelVoiceSelectionPolicy.forLanguage(previous, "en-US")
        assertEquals("com.example.voice", next.ttsEnginePackage)
        assertEquals(SpeechInputId.ANDROID_STT, next.input)
        assertNull(next.voiceId)
    }

    @Test fun polishKeepsEngineInputOutputButClearsVoiceSelection() {
        val previous = AudioEngineProfile(
            languageTag = "en-US",
            input = SpeechInputId.PARAKEET_PL,
            output = SpeechOutputId.PIPER_PL,
            ttsEnginePackage = PiperServiceContract.PACKAGE,
            voiceId = "old",
        )
        val next = ModelVoiceSelectionPolicy.forLanguage(previous, "pl-PL")
        assertEquals(previous.input, next.input)
        assertEquals(previous.output, next.output)
        assertEquals(previous.ttsEnginePackage, next.ttsEnginePackage)
        assertEquals("pl-PL", next.languageTag)
        assertNull(next.voiceId)
    }
}
