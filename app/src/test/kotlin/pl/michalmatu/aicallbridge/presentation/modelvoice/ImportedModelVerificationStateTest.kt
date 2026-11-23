package pl.michalmatu.aicallbridge.presentation.modelvoice

import org.junit.Assert.*
import org.junit.Test

class ImportedModelVerificationStateTest {
    @Test fun singleFlightAndStaleCompletions() {
        val state = ImportedModelVerificationState()
        assertTrue(state.begin("one"))
        assertFalse(state.begin("two"))
        assertEquals("one", state.checkingId)
        assertFalse(state.complete("two", "ignored"))
        assertTrue(state.complete("one", "native_failed"))
        assertNull(state.checkingId)
        assertEquals("native_failed", state.failures["one"])
    }

    @Test fun errorsStayPerModelAndSuccessClearsOnlyOne() {
        val state = ImportedModelVerificationState()
        state.begin("a"); state.complete("a", "error-a")
        state.begin("b"); state.complete("b", "error-b")
        assertEquals(2, state.failures.size)
        assertTrue(state.begin("a"))
        assertNull(state.failures["a"])
        assertTrue(state.complete("a", null))
        assertEquals(mapOf("b" to "error-b"), state.failures)
    }

    @Test fun cancelledCheckIgnoresLateCallback() {
        val state = ImportedModelVerificationState()
        state.begin("a")
        state.cancel()
        assertNull(state.checkingId)
        assertFalse(state.complete("a", "late_failure"))
        assertTrue(state.failures.isEmpty())
    }
}
