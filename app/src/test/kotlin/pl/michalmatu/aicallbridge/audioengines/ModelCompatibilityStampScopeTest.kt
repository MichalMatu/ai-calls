package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelCompatibilityStampScopeTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun audioProofIsBoundToVoiceEngineAndLanguageButTextProofIsNot() {
        val file = temporary.newFile("fake-model.litertlm").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val baseline = AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT,
            voiceId = "pl-test-a", ttsEnginePackage = "com.google.android.tts")
        val audio = checkNotNull(ModelCompatibilityStamp.of(file, baseline, true))
        assertEquals(audio, ModelCompatibilityStamp.of(file, baseline, true))
        assertNotEquals(audio, ModelCompatibilityStamp.of(file,
            baseline.copy(voiceId = "pl-test-b"), true))
        assertNotEquals(audio, ModelCompatibilityStamp.of(file,
            baseline.copy(ttsEnginePackage = "com.samsung.SMT"), true))
        assertNotEquals(audio, ModelCompatibilityStamp.of(file,
            baseline.copy(languageTag = "en-US"), true))
        assertNotEquals(audio, ModelCompatibilityStamp.of(file,
            baseline.copy(voiceId = null), true))
        assertEquals(ModelCompatibilityStamp.of(file),
            ModelCompatibilityStamp.of(file, baseline.copy(voiceId = "pl-test-b"), false))
    }

    @Test fun audioProofChangesWithModelBytesAndRequiresValidFile() {
        val file = temporary.newFile("model.litertlm").apply { writeBytes(byteArrayOf(1, 2)) }
        val profile = AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT)
        val before = ModelCompatibilityStamp.of(file, profile, true)
        file.appendBytes(byteArrayOf(3, 4))
        assertNotEquals(before, ModelCompatibilityStamp.of(file, profile, true))
        assertNull(ModelCompatibilityStamp.of(temporary.root.resolve("absent.litertlm"), profile, true))
    }
}
