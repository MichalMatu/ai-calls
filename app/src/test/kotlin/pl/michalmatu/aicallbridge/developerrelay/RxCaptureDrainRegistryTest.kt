package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RxCaptureDrainRegistryTest {
    @Test fun remoteHangupDrainsUnfinishedWindowBeforeEndExactlyOnce() {
        val registry = RxCaptureDrainRegistry()
        val events = mutableListOf<String>()
        val window = registry.open { events += "RX_WAV" }
        registry.finishAll()
        events += "END"
        assertEquals(listOf("RX_WAV", "END"), events)
        assertFalse(window.finish())
        registry.finishAll()
        assertEquals(listOf("RX_WAV", "END"), events)
    }

    @Test fun previouslyFinalizedWindowIsNotReplayedButNewOpenWindowIsDrained() {
        val registry = RxCaptureDrainRegistry()
        val events = mutableListOf<String>()
        val first = registry.open { events += "rx-part-001" }
        assertTrue(first.finish())
        val second = registry.open { events += "rx-part-002" }
        registry.finishAll()
        assertFalse(second.finish())
        assertEquals(listOf("rx-part-001", "rx-part-002"), events)
    }

    @Test fun lateOpenAfterStopNeverEmitsAudioAndCannotReopenArchive() {
        val registry = RxCaptureDrainRegistry()
        registry.finishAll()
        var filesWritten = 0
        val late = registry.open { filesWritten++ }
        assertFalse(late.finish())
        registry.finishAll()
        assertEquals(0, filesWritten)
    }

    @Test fun reentrantLateCallbackDuringFlushCannotWriteAfterEnd() {
        val registry = RxCaptureDrainRegistry()
        val events = mutableListOf<String>()
        registry.open {
            events += "rx"
            val late = registry.open { events += "forbidden" }
            assertFalse(late.finish())
        }
        registry.finishAll()
        events += "END"
        assertEquals(listOf("rx", "END"), events)
    }

    @Test fun recorderFlushFailureDoesNotSkipOtherOpenWindows() {
        val registry = RxCaptureDrainRegistry()
        var remaining = 0
        registry.open { throw IllegalStateException("simulated_write_failure") }
        registry.open { remaining++ }
        registry.finishAll()
        assertEquals(1, remaining)
    }
}
