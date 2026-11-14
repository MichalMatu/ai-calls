package pl.michalmatu.aicallbridge.textagent

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class NativeConversationWindowTest {
    private fun full() = IllegalStateException(
        "FAILED_PRECONDITION: Chosen prefill work group size exceeds available state entries (103).")
    private fun window(count: Int) = NativeConversationWindow().apply {
        repeat(count) { remember("user-$it", "model-$it") }
    }

    @Test fun thirdTurnCapacityFailureKeepsNewestPairAndRetriesSameUnchangedInput() {
        val window = window(2)
        val prompt = "PRIVATE_CURRENT_MESSAGE".repeat(100)
        val inputs = mutableListOf<String>()
        val rebuilt = mutableListOf<List<NativeConversationTurn>>()
        val result = sendWithContextRecovery(2, window, { true }, {
            window.snapshot().also(rebuilt::add).size
        }) { retained ->
            inputs += prompt
            if (retained > 1) throw full()
            "one-complete-reply"
        }
        assertEquals("one-complete-reply", result)
        assertEquals(listOf(prompt, prompt), inputs)
        assertEquals(listOf(NativeConversationTurn("user-1", "model-1")), rebuilt.single())
        assertEquals(2, window.completedTurns)
    }

    @Test fun capacityRecoveryMakesProgressWithoutAnArbitraryRetryOrDialogueTurnLimit() {
        val window = window(30)
        var rebuilds = 0
        var sends = 0
        val result = sendWithContextRecovery(30, window, { true }, {
            rebuilds++
            window.snapshot().size
        }) { retained ->
            sends++
            if (retained > 0) throw full()
            "success"
        }
        assertEquals("success", result)
        assertEquals(30, rebuilds)
        assertEquals(31, sends)
        window.remember("next", "reply")
        assertEquals(31, window.completedTurns)
    }

    @Test fun capacityFailureDuringRecreationAlsoShrinksHistoryBeforeRetry() {
        val window = window(2)
        val rebuiltSizes = mutableListOf<Int>()
        val result = sendWithContextRecovery(2, window, { true }, {
            val retained = window.snapshot().size
            rebuiltSizes += retained
            if (retained > 0) throw full()
            retained
        }) { retained ->
            if (retained > 0) throw full()
            "success"
        }
        assertEquals("success", result)
        assertEquals(listOf(1, 0), rebuiltSizes)
    }

    @Test fun nativePublicErrorCodeNeverIncludesPrivateModelPathOrCallText() {
        val privateMessage = "INVALID_ARGUMENT: /sdcard/PRIVATE-MODEL.litertlm " +
            "caller said 555-0100 and PRIVATE_TEXT"
        val safe = nativePublicErrorCode(IllegalStateException(privateMessage))
        assertEquals("IllegalStateException_INVALID_ARGUMENT", safe)
        assertFalse(safe.contains("PRIVATE"))
        assertFalse(safe.contains("litertlm"))
        assertFalse(safe.contains("555"))
        assertEquals("IllegalArgumentException_UNKNOWN",
            nativePublicErrorCode(IllegalArgumentException("caller PRIVATE_TEXT")))
    }

    @Test fun nativeResourceExhaustedInputTokenVariantAlsoRecovers() {
        val window = window(1)
        var sends = 0
        val result = sendWithContextRecovery(1, window, { true }, { 0 }) { retained ->
            sends++
            if (retained > 0) throw IllegalStateException(
                "Failed to call nativeSendMessage: RESOURCE_EXHAUSTED: Input token count exceeds remaining context")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(2, sends)
        assertEquals("RESOURCE_EXHAUSTED", nativeStatusCode(IllegalStateException(
            "RESOURCE_EXHAUSTED: Input token count exceeds remaining context")))
        assertEquals("UNKNOWN", nativeStatusCode(IllegalStateException("PRIVATE_TEXT")))
        assertEquals("unclassified", nativeFailureHints(IllegalStateException("PRIVATE_TEXT +12025550101")))
        assertEquals("context-token", nativeFailureHints(IllegalStateException("PRIVATE_TEXT token context")))
    }

    @Test fun prefillLengthVersusStateCapacityVariantRecovers() {
        val window = window(1)
        var sends = 0
        val result = sendWithContextRecovery(1, window, { true }, { 0 }) { retained ->
            sends++
            if (retained > 0) throw IllegalStateException(
                "Failed to call nativeSendMessage: FAILED_PRECONDITION: Prefill length exceeds available state capacity")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(2, sends)
        assertFalse(isNativeContextCapacityFailure(IllegalArgumentException(
            "INVALID_ARGUMENT: prefill shape exceeds state tensor rank")))
    }

    @Test fun unrelatedNativeErrorsDoNotDiscardHistoryOrRetry() {
        val window = window(2)
        var resets = 0
        val failure = IllegalStateException("RESOURCE_EXHAUSTED: GPU allocation failed")
        try {
            sendWithContextRecovery(1, window, { true }, { resets++; it }) { throw failure }
            fail("failure must propagate")
        } catch (error: IllegalStateException) { assertSame(failure, error) }
        assertEquals(0, resets)
        assertEquals(2, window.snapshot().size)
    }

    @Test fun inputThatCannotFitWithoutHistoryFailsOnceWithoutTruncatingCurrentMessage() {
        val window = window(0)
        var sends = 0
        try {
            sendWithContextRecovery(0, window, { true }, { fail("no history to evict"); it }) {
                sends++; throw full()
            }
            fail("capacity failure must propagate")
        } catch (_: IllegalStateException) { }
        assertEquals(1, sends)
    }

    @Test fun stopDuringNativeFailurePreventsRecoveryAndPreservesEarlierTurns() {
        val window = window(2)
        var current = true
        var resets = 0
        try {
            sendWithContextRecovery(2, window, { current }, { resets++; it }) {
                current = false
                throw full()
            }
            fail("STOP must cancel")
        } catch (_: CancellationException) { }
        assertEquals(0, resets)
        assertEquals(2, window.snapshot().size)
    }

    @Test fun stopDuringRecreationPreventsAnySecondNativeSend() {
        val window = window(2)
        var current = true
        var sends = 0
        try {
            sendWithContextRecovery(2, window, { current }, { current = false; 1 }) {
                sends++; throw full()
            }
            fail("STOP must cancel")
        } catch (_: CancellationException) { }
        assertEquals(1, sends)
    }

    @Test fun explicitResetDropsPriorCallHistoryAndDebugStringIsRedacted() {
        val window = window(2)
        assertFalse(window.snapshot().toString().contains("user-"))
        window.clear()
        assertTrue(window.snapshot().isEmpty())
        assertEquals(0, window.completedTurns)
        assertTrue(isNativeContextCapacityFailure(IllegalStateException("outer", full())))
        assertFalse(isNativeContextCapacityFailure(IllegalArgumentException("invalid context format")))
    }

    @Test fun repairInstructionCanEvictOldPairsDuringConversationCreation() {
        val window = window(3)
        val retained = mutableListOf<Int>()
        val result = recreateWithContextRecovery(window, { true }, {
            val size = window.snapshot().size
            retained += size
            if (size > 1) throw full()
            "recreated"
        })
        assertEquals("recreated", result)
        assertEquals(listOf(3, 2, 1), retained)
        assertEquals(listOf(NativeConversationTurn("user-2", "model-2")), window.snapshot())
    }

    @Test fun stopDuringRepairRecreationDoesNotDiscardHistoryOrTryAgain() {
        val window = window(2)
        var current = true
        var attempts = 0
        try {
            recreateWithContextRecovery(window, { current }, {
                attempts++; current = false; throw full()
            })
            fail("STOP must cancel")
        } catch (_: CancellationException) { }
        assertEquals(1, attempts)
        assertEquals(2, window.snapshot().size)
    }

    @Test fun repairRecreationDoesNotRetryAllocationFailureOrTruncateTheSystemInstruction() {
        val window = window(1)
        val allocation = IllegalStateException("RESOURCE_EXHAUSTED: GPU allocation failed")
        try {
            recreateWithContextRecovery(window, { true }, { throw allocation })
            fail("allocation failure must propagate")
        } catch (error: IllegalStateException) { assertSame(allocation, error) }
        assertEquals(1, window.snapshot().size)
        var attempts = 0
        try {
            recreateWithContextRecovery(window(0), { true }, { attempts++; throw full() })
            fail("unfit instruction must fail")
        } catch (_: IllegalStateException) { }
        assertEquals(1, attempts)
    }
}
