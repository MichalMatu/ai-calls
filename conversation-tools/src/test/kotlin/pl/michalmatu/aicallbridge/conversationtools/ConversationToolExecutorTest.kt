package pl.michalmatu.aicallbridge.conversationtools

import org.junit.Assert.*
import org.junit.Test

class ConversationToolExecutorTest {
    @Test fun diagnosticObserverFailureDoesNotBlockAuthorizedExecution() {
        val port = Port()
        val executor = ConversationToolExecutor(port)
        val result = executor.execute(1, ConversationToolId.END_CALL) { error("synthetic_observer_failure") }
        assertEquals(ConversationToolResult.REQUESTED, result)
        assertEquals(1, port.requests)
        assertTrue(executor.diagnosticDeliveryFailed)
    }

    private class Port : ConversationToolPort {
        var enabled = true
        var allowed = true
        var requests = 0
        var fail = false
        override fun enabled(tool: ConversationToolId) = enabled
        override fun authorized(tool: ConversationToolId) = allowed
        override fun requestEndCall(turn: Int): Boolean { requests++; check(!fail); return true }
    }
    @Test fun stagesDistinguishProposalValidationAndAndroidRequestFromConfirmedDisconnect() {
        val port = Port(); val events = mutableListOf<ConversationToolEvent>()
        val result = ConversationToolExecutor(port).execute(1, ConversationToolId.END_CALL, events::add)
        assertEquals(ConversationToolResult.REQUESTED, result)
        assertEquals(listOf(ConversationToolStage.REQUESTED, ConversationToolStage.VALIDATED, ConversationToolStage.DISPATCHED), events.map { it.stage })
        assertFalse(events.any { it.stage == ConversationToolStage.CONFIRMED })
        assertEquals(1, port.requests)
    }
    @Test fun disabledAndUnauthorizedToolsNeverReachThePhonePort() {
        for (disabled in listOf(true, false)) {
            val port = Port().apply { enabled = !disabled; allowed = disabled }
            val events = mutableListOf<ConversationToolEvent>()
            assertEquals(if (disabled) ConversationToolResult.DISABLED else ConversationToolResult.NOT_AUTHORIZED,
                ConversationToolExecutor(port).execute(1, ConversationToolId.END_CALL, events::add))
            assertEquals(0, port.requests); assertEquals(ConversationToolStage.REJECTED, events.last().stage)
        }
    }
    @Test fun revocationFromObserverWinsImmediatelyBeforeDispatch() {
        val port = Port()
        val result = ConversationToolExecutor(port).execute(1, ConversationToolId.END_CALL) {
            if (it.stage == ConversationToolStage.VALIDATED) port.allowed = false
        }
        assertEquals(ConversationToolResult.NOT_AUTHORIZED, result); assertEquals(0, port.requests)
    }
    @Test fun duplicateRequestUsesTheKnownResultWithoutSecondExternalAction() {
        val port = Port(); val executor = ConversationToolExecutor(port)
        repeat(3) { assertEquals(ConversationToolResult.REQUESTED, executor.execute(1, ConversationToolId.END_CALL) { }) }
        assertEquals(1, port.requests)
    }
    @Test fun failedDispatchIsReportedAndNotBlindlyRetried() {
        val port = Port().apply { fail = true }; val executor = ConversationToolExecutor(port)
        repeat(2) { assertEquals(ConversationToolResult.FAILED, executor.execute(1, ConversationToolId.END_CALL) { }) }
        assertEquals(1, port.requests)
    }
    @Test fun laterTurnCanUseNewlyEnabledCapabilityAfterEarlierRejection() {
        val port = Port().apply { enabled = false }; val executor = ConversationToolExecutor(port)
        assertEquals(ConversationToolResult.DISABLED, executor.execute(1, ConversationToolId.END_CALL) { })
        port.enabled = true
        assertEquals(ConversationToolResult.REQUESTED, executor.execute(2, ConversationToolId.END_CALL) { })
        assertEquals(1, port.requests)
    }
}
