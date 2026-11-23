package pl.michalmatu.aicallbridge.presentation.modelvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile

class ModelVoiceProfileControllerTest {
    private class FakeStore(var saved: AudioEngineProfile? = AudioEngineProfile.STABLE) {
        var readCount = 0
        var writeCount = 0
        var allowWrite = true
        fun load(): AudioEngineProfile? { readCount++; return saved }
        fun write(next: AudioEngineProfile): Boolean {
            writeCount++
            if (allowWrite) saved = next
            return allowWrite
        }
        fun controller() = ModelVoiceProfileController(::load, ::write)
    }

    @Test fun missingProfileUsesStableDraftWithoutWriting() {
        val store = FakeStore(null)
        val state = store.controller()
        assertEquals(AudioEngineProfile.STABLE, state.draft)
        assertEquals(1, store.readCount)
        assertEquals(0, store.writeCount)
    }

    @Test fun matchingSavedProfileIsANoopWithoutWriting() {
        val store = FakeStore()
        val state = store.controller()
        assertEquals(ModelVoiceProfileController.SaveResult.Unchanged, state.select(state.draft))
        assertEquals(2, store.readCount)
        assertEquals(0, store.writeCount)
    }

    @Test fun failedSaveAndInvalidProfileDoNotChangeDraft() {
        val store = FakeStore()
        val state = store.controller()
        val invalid = AudioEngineProfile(languageTag = "de-DE")
        assertEquals(ModelVoiceProfileController.SaveResult.Rejected("unsupported_language"), state.select(invalid))
        assertEquals(0, store.writeCount)
        store.allowWrite = false
        assertEquals(ModelVoiceProfileController.SaveResult.WriteFailed,
            state.select(AudioEngineProfile.STABLE.copy(languageTag = "pl-PL")))
        assertEquals(1, store.writeCount)
        assertEquals(AudioEngineProfile.STABLE, state.draft)
    }

    @Test fun changesOnlyTriggerVoiceInventoryReloadForEngineOrLanguage() {
        val store = FakeStore()
        val state = store.controller()
        assertEquals(ModelVoiceProfileController.SaveResult.Saved(false),
            state.select(state.draft.copy(voiceId = "example")))
        assertEquals(ModelVoiceProfileController.SaveResult.Saved(true),
            state.select(state.draft.copy(languageTag = "pl-PL")))
        assertEquals(ModelVoiceProfileController.SaveResult.Saved(true),
            state.select(state.draft.copy(ttsEnginePackage = "org.example.tts")))
        assertEquals(3, store.writeCount)
    }

    @Test fun savedExternalChangeIsAdoptedOnlyAfterScreenCancelsProbe() {
        val store = FakeStore()
        val state = store.controller()
        store.saved = AudioEngineProfile.STABLE.copy(languageTag = "pl-PL")
        val pending = state.pendingExternalChange()
        assertTrue(pending!!.engineChanged)
        assertEquals(AudioEngineProfile.STABLE, state.draft)
        state.acceptExternalChange(pending)
        assertEquals("pl-PL", state.draft.languageTag)
        assertEquals(0, store.writeCount)
        assertNull(state.pendingExternalChange())
    }

    @Test fun missingPersistedStateMakesSameDraftWriteAgain() {
        val store = FakeStore()
        val state = store.controller()
        store.saved = null
        assertNull(state.pendingExternalChange())
        assertEquals(ModelVoiceProfileController.SaveResult.Saved(false), state.select(state.draft))
        assertEquals(1, store.writeCount)
        assertEquals(AudioEngineProfile.STABLE, store.saved)
    }
}
