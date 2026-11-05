package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class DialogueEngineProvidersTest {
    @Test fun everyCatalogModelResolvesWithoutSilentFallback() {
        assertEquals(4, DialogueModelId.entries.size)
        for (model in DialogueModelId.entries) {
            assertEquals(model, DialogueEngineProviders.resolve(
                AudioEngineProfile(dialogue = model),
            ).id)
            assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(dialogue = model)))
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
