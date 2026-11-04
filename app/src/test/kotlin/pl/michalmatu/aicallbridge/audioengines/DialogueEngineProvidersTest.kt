package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class DialogueEngineProvidersTest {
    @Test fun stableDefaultResolvesToExistingE2BBackendOnly() {
        assertSame(Gemma4E2BDialogueEngineProvider, DialogueEngineProviders.resolve(
            AudioEngineProfile.STABLE,
        ))
        assertEquals(DialogueModelId.GEMMA_4_E2B, DialogueEngineProviders.resolve(
            AudioEngineProfile(voiceId = "pl-local-test"),
        ).id)
    }

    @Test fun e4bCannotFallBackSilentlyToE2B() {
        try {
            DialogueEngineProviders.resolve(AudioEngineProfile(dialogue = DialogueModelId.GEMMA_4_E4B))
            fail("Unsupported dialogue engine must be rejected")
        } catch (_: IllegalArgumentException) {
            // Model installation, verified identity and runtime are required first.
        }
    }

    @Test fun noAlternateInputCanClaimTheExistingTextAdapter() {
        try {
            DialogueEngineProviders.resolve(AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT))
            fail("Native audio adapter does not exist")
        } catch (_: IllegalArgumentException) {
            // Model's text backend is not a PCM STT provider.
        }
    }
}
