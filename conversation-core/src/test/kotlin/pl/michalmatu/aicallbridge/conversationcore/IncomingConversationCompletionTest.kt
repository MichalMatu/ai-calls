package pl.michalmatu.aicallbridge.conversationcore

import org.junit.Assert.*
import org.junit.Test

class IncomingConversationCompletionTest {
    private class Call
    private val policy = IncomingAssistantPolicy("accepted", AnsweringMode.AI, "sim-a", true)
    private inner class Fixture {
        val call = Call()
        val disconnected = mutableListOf<Call>()
        val stops = mutableListOf<ConversationStopReason>()
        var closingHook: (() -> Unit)? = null
        var disconnectFails = false
        val core = IncomingAssistantCoordinator(object : IncomingAssistantPort<Call> {
            override fun readinessFailure(call: Call): String? = null
            override fun answer(call: Call) = Unit
            override fun startAudio(call: Call, policy: IncomingAssistantPolicy, generation: Long) = Unit
            override fun stopAudio(call: Call, generation: Long, reason: ConversationStopReason) { stops += reason }
            override fun disconnect(call: Call) { check(!disconnectFails); disconnected += call }
            override fun changed(call: Call, state: IncomingAssistantState, reason: String?) {
                if (state == IncomingAssistantState.ENDING) closingHook?.invoke()
            }
        })
        val generation: Long
        init {
            core.observe(call, IncomingCallPhase.RINGING, "sim-a", true, true, policy)
            core.observe(call, IncomingCallPhase.ACTIVE, "sim-a", true, true, policy)
            generation = checkNotNull(core.snapshot(call)).generation
            core.audioStarted(call, generation)
        }
    }

    @Test fun completedPlaybackDisconnectsOnlyExactOwnedCallOnce() {
        val f = Fixture()
        assertFalse(f.core.completeConversation(Call(), f.generation, policy))
        assertTrue(f.core.completeConversation(f.call, f.generation, policy))
        assertEquals(listOf(f.call), f.disconnected)
        assertEquals(IncomingAssistantState.ENDING, f.core.snapshot(f.call)?.state)
        assertFalse(f.core.completeConversation(f.call, f.generation, policy))
        assertFalse(f.core.isAuthorized(f.call, f.generation))
    }

    @Test fun takeoverAndOldGenerationCannotDisconnectResumedConversation() {
        val f = Fixture()
        f.core.takeOver(f.call)
        assertEquals(listOf(ConversationStopReason.HUMAN_TAKEOVER), f.stops)
        assertFalse(f.core.completeConversation(f.call, f.generation, policy))
        assertTrue(f.core.resume(f.call, policy))
        val next = checkNotNull(f.core.snapshot(f.call)).generation
        assertFalse(f.core.completeConversation(f.call, f.generation, policy))
        f.core.audioStarted(f.call, next)
        assertTrue(f.core.isAuthorized(f.call, next))
        assertTrue(f.disconnected.isEmpty())
    }

    @Test fun endHoldRevocationAndDifferentAccountRejectLateCompletion() {
        for (kind in 0..3) {
            val f = Fixture()
            val current = if (kind == 2) policy.copy(mode = AnsweringMode.MANUAL) else policy
            when (kind) {
                0 -> f.core.observe(f.call, IncomingCallPhase.ENDED, "sim-a", true, true, policy)
                1 -> f.core.observe(f.call, IncomingCallPhase.HELD, "sim-a", true, true, policy)
                2 -> f.core.policyChanged(current, ConversationStopReason.POLICY_REVOKED)
                3 -> f.core.observe(f.call, IncomingCallPhase.ACTIVE, "sim-b", true, true, policy)
            }
            assertFalse(f.core.completeConversation(f.call, f.generation, current))
            assertTrue(f.disconnected.isEmpty())
        }
    }

    @Test fun anotherActiveCallAndFreshRevocationWinImmediatelyBeforeDisconnect() {
        val f = Fixture()
        assertFalse(f.core.completeConversation(f.call, f.generation, policy.copy(mode = AnsweringMode.MANUAL)))
        f.core.observe(f.call, IncomingCallPhase.ACTIVE, "sim-a", true, false, policy)
        assertFalse(f.core.completeConversation(f.call, f.generation, policy))
        assertTrue(f.disconnected.isEmpty())
    }

    @Test fun reentrantTakeoverAtClosingNotificationWinsOverDisconnect() {
        val f = Fixture()
        f.closingHook = { f.core.takeOver(f.call) }
        assertFalse(f.core.completeConversation(f.call, f.generation, policy))
        assertTrue(f.disconnected.isEmpty())
    }

    @Test fun failedDisconnectLeavesCallManualAndDoesNotRetryOnItsOwn() {
        val f = Fixture(); f.disconnectFails = true
        assertFalse(f.core.completeConversation(f.call, f.generation, policy))
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
        assertEquals("incoming_disconnect_failed", f.core.snapshot(f.call)?.reason)
        assertFalse(f.core.completeConversation(f.call, f.generation, policy))
        assertTrue(f.disconnected.isEmpty())
    }

    @Test fun remoteEndAndOperatorStopHaveDifferentTypedReasons() {
        val ended = Fixture()
        ended.core.observe(ended.call, IncomingCallPhase.ENDED, "sim-a", true, true, policy)
        assertEquals(listOf(ConversationStopReason.CALL_ENDED), ended.stops)
        val stopped = Fixture()
        stopped.core.policyChanged(policy.copy(mode = AnsweringMode.MANUAL), ConversationStopReason.OPERATOR_STOP)
        assertEquals(listOf(ConversationStopReason.OPERATOR_STOP), stopped.stops)
    }
}
