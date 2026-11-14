package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class DialogueEngineProvidersTest {
    @Test fun everyCatalogModelResolvesWithoutSilentFallback() {
        assertEquals(6, DialogueModelId.entries.size)
        for (model in DialogueModelId.entries.filterNot {
            it == DialogueModelId.QWEN_3_5_2B || it == DialogueModelId.USER_LITERT
        }) {
            assertEquals(model, DialogueEngineProviders.resolve(
                AudioEngineProfile(dialogue = model),
            ).id)
            assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(dialogue = model)))
        }
    }

    @Test fun userImportNeedsAnExplicitHashedIdentity() {
        val profile = AudioEngineProfile(dialogue = DialogueModelId.USER_LITERT,
            customModelId = "user-" + "b".repeat(64))
        assertEquals(DialogueModelId.USER_LITERT, DialogueEngineProviders.resolve(profile).id)
    }

    @Test fun labOnlyCandidateCannotBeActivatedAsTelephoneProfile() {
        try {
            DialogueEngineProviders.resolve(AudioEngineProfile(dialogue = DialogueModelId.QWEN_3_5_2B))
            fail("Qwen 3.5 not accepted for Call")
        } catch (_: IllegalArgumentException) {
            // Explicit native Lab smoke uses the model factory, not telephone profile selection.
        }
    }

    @Test fun gemmaAudioProfileIsExplicitWhileStableProfileRemainsAndroidStt() {
        assertEquals(SpeechInputId.ANDROID_STT, AudioEngineProfile.STABLE.input)
        val audio = AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT)
        assertEquals(DialogueModelId.GEMMA_4_E2B, DialogueEngineProviders.resolve(audio).id)
        assertEquals(SpeechOutputId.ANDROID_TTS_PL, audio.output)
    }

    @Test fun audioIsADeclaredModelCapabilityWithoutChangingTheSelectedIdentity() {
        val profile = AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT)
        assertEquals(DialogueModelId.GEMMA_4_E2B, DialogueEngineProviders.resolve(profile).id)
        assertTrue(AudioEngineRegistry.supportsAudio(profile))
        val imported = profile.copy(dialogue = DialogueModelId.USER_LITERT, customModelId = "user-" + "b".repeat(64))
        assertEquals("model_audio_input_unavailable", AudioEngineRegistry.incompatibility(imported))
    }
}
