package pl.michalmatu.aicallbridge.presentation.modelvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelVoiceTtsAsyncStateTest {
    @Test fun startsUnreadyWithoutPendingPreview() {
        val state = ModelVoiceTtsAsyncState()
        assertFalse(state.voicesReady)
        assertNull(state.takeQueuedPreview())
    }

    @Test fun newLoadResetsReadyFlagAndFencesPriorGeneration() {
        val state = ModelVoiceTtsAsyncState()
        val first = state.beginLoad()
        state.markReady()
        assertTrue(state.voicesReady)
        val second = state.beginLoad()
        assertFalse(state.voicesReady)
        assertFalse(state.accepts(first))
        assertTrue(state.accepts(second))
    }

    @Test fun nullVoiceIsARealAutomaticPreviewRequest() {
        val state = ModelVoiceTtsAsyncState()
        assertNull(state.takeQueuedPreview())
        state.queuePreview(null)
        assertEquals(ModelVoiceTtsAsyncState.QueuedPreview(null), state.takeQueuedPreview())
        assertNull(state.takeQueuedPreview())
    }

    @Test fun latestPreviewRequestWins() {
        val state = ModelVoiceTtsAsyncState()
        state.queuePreview("old")
        state.queuePreview("new")
        assertEquals("new", state.takeQueuedPreview()?.voiceId)
        assertNull(state.takeQueuedPreview())
    }

    @Test fun pendingPreviewSurvivesReload() {
        val state = ModelVoiceTtsAsyncState()
        state.queuePreview("pl-local")
        val generation = state.beginLoad()
        assertTrue(state.accepts(generation))
        assertFalse(state.voicesReady)
        state.markReady()
        assertEquals("pl-local", state.takeQueuedPreview()?.voiceId)
    }

    @Test fun cancellationDoesNotChangeReadinessOrGeneration() {
        val state = ModelVoiceTtsAsyncState()
        val generation = state.beginLoad()
        state.markReady()
        state.queuePreview("pl")
        state.cancelPreview()
        assertNull(state.takeQueuedPreview())
        assertTrue(state.voicesReady)
        assertTrue(state.accepts(generation))
    }

    @Test fun engineSwitchClearsQueuedPreviewWithoutInvalidatingExistingToken() {
        val state = ModelVoiceTtsAsyncState()
        val generation = state.beginLoad()
        state.queuePreview("old-engine-voice")
        state.onEngineChanged()
        assertNull(state.takeQueuedPreview())
        assertTrue(state.accepts(generation))
        state.queuePreview(null)
        assertEquals(ModelVoiceTtsAsyncState.QueuedPreview(null), state.takeQueuedPreview())
    }

    @Test fun closingFencesLateCallbacks() {
        val state = ModelVoiceTtsAsyncState()
        val generation = state.beginLoad()
        state.queuePreview("pl")
        state.cancelPreview()
        state.invalidate()
        assertFalse(state.accepts(generation))
        assertNull(state.takeQueuedPreview())
    }
}
