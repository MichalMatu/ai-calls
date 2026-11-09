package pl.michalmatu.aicallbridge.conversationcore

import org.junit.Assert.*
import org.junit.Test

class IncomingAssistantCoordinatorTest {
    private val policy = IncomingAssistantPolicy("accepted", AnsweringMode.AI, "sim-a", true)
    private class Call
    private class Port : IncomingAssistantPort<Call> {
        val answered = mutableListOf<Call>()
        val started = mutableListOf<Pair<Call, Long>>()
        val stopped = mutableListOf<Pair<Call, Long>>()
        val disconnected = mutableListOf<Call>()
        var failure: String? = null
        var readinessThrows = false
        var stopThrows = false
        var reentrantAnswer: (() -> Unit)? = null
        var reentrantReadiness: (() -> Unit)? = null
        var reentrantChanged: ((IncomingAssistantState) -> Unit)? = null
        override fun readinessFailure(call: Call): String? { reentrantReadiness?.invoke(); check(!readinessThrows); return failure }
        override fun answer(call: Call) { answered += call; reentrantAnswer?.invoke() }
        override fun startAudio(call: Call, policy: IncomingAssistantPolicy, generation: Long) { started += call to generation }
        override fun stopAudio(call: Call, generation: Long, reason: ConversationStopReason) {
            stopped += call to generation
            if (stopThrows) throw IllegalStateException("synthetic_audio_shutdown")
        }
        override fun disconnect(call: Call) { disconnected += call }
        override fun changed(call: Call, state: IncomingAssistantState, reason: String?) { reentrantChanged?.invoke(state) }
    }
    private class Fixture {
        val port = Port()
        val core = IncomingAssistantCoordinator(port)
        val call = Call()
        fun observe(phase: IncomingCallPhase, policy: IncomingAssistantPolicy, account: String? = "sim-a", incoming: Boolean = true, exclusive: Boolean = true) =
            core.observe(call, phase, account, incoming, exclusive, policy)
    }
    @Test fun manualNeverAnswersOrStartsAndPolicyEnableDoesNotHijackExistingCall() {
        val f=Fixture()
        f.observe(IncomingCallPhase.RINGING, policy.copy(mode=AnsweringMode.MANUAL))
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ACTIVE, policy)
        assertTrue(f.port.answered.isEmpty()); assertTrue(f.port.started.isEmpty())
    }
    @Test fun autoAnswersOnceAndStartsOnlyAfterExactCallActive() {
        val f=Fixture()
        repeat(4) { f.observe(IncomingCallPhase.RINGING, policy) }
        assertEquals(listOf(f.call),f.port.answered); assertTrue(f.port.started.isEmpty())
        repeat(4) { f.observe(IncomingCallPhase.ACTIVE, policy) }
        assertEquals(1,f.port.started.size)
        val generation=f.port.started.single().second
        assertTrue(f.core.isAuthorized(f.call,generation))
        assertFalse(f.core.isAuthorized(Call(),generation))
    }
    @Test fun delayedAccountDetailsAreBoundBeforeAnswer() {
        val f=Fixture(); f.observe(IncomingCallPhase.RINGING, policy,account=null)
        assertTrue(f.port.answered.isEmpty())
        f.observe(IncomingCallPhase.RINGING,policy)
        assertEquals(1,f.port.answered.size)
    }
    @Test fun differentSimOutgoingAndOtherActiveCallRemainManual() {
        for (kind in 0..2) {
            val f=Fixture()
            f.observe(IncomingCallPhase.RINGING,policy,
                account=if(kind==0) "sim-b" else "sim-a",incoming=kind!=1,exclusive=kind!=2)
            f.observe(IncomingCallPhase.ACTIVE,policy)
            assertTrue(f.port.answered.isEmpty()); assertTrue(f.port.started.isEmpty())
        }
    }
    @Test fun userAnswersBeforeAutoDecisionAndFutureCallbacksCannotStartAi() {
        val f=Fixture()
        f.observe(IncomingCallPhase.RINGING,policy,account=null)
        f.core.takeOver(f.call)
        f.observe(IncomingCallPhase.ACTIVE,policy)
        assertTrue(f.port.started.isEmpty())
    }
    @Test fun takeoverRevokesBeforeStopAndStaleCompletionDoesNotAffectResumedGeneration() {
        val f=Fixture(); f.observe(IncomingCallPhase.RINGING,policy); f.observe(IncomingCallPhase.ACTIVE,policy)
        val old=f.port.started.single().second
        f.core.takeOver(f.call)
        assertFalse(f.core.isAuthorized(f.call,old)); assertEquals(listOf(f.call to old),f.port.stopped)
        repeat(3) { f.observe(IncomingCallPhase.ACTIVE,policy) }
        assertEquals(1,f.port.started.size)
        assertTrue(f.core.resume(f.call,policy))
        val next=f.port.started.last().second
        assertNotEquals(old,next)
        f.core.audioFinished(f.call,old,"late_completion")
        assertTrue(f.core.isAuthorized(f.call,next))
    }
    @Test fun revocationHoldingAndRemoteEndStopOnlyTheirAudio() {
        for (kind in 0..2) {
            val f=Fixture(); f.observe(IncomingCallPhase.RINGING,policy); f.observe(IncomingCallPhase.ACTIVE,policy)
            val generation=f.port.started.single().second
            when(kind) {
                0 -> f.core.policyChanged(policy.copy(mode=AnsweringMode.MANUAL), ConversationStopReason.POLICY_REVOKED)
                1 -> f.observe(IncomingCallPhase.HELD,policy)
                2 -> f.observe(IncomingCallPhase.ENDED,policy)
            }
            assertFalse(f.core.isAuthorized(f.call,generation))
            assertEquals(listOf(f.call to generation),f.port.stopped)
        }
    }
    @Test fun missingReadinessDoesNotAnswerOrRequireRepeatedConfirmation() {
        val f=Fixture(); f.port.failure="record_audio_permission_missing"
        f.observe(IncomingCallPhase.RINGING,policy)
        assertTrue(f.port.answered.isEmpty()); assertEquals(IncomingAssistantState.MANUAL,f.core.snapshot(f.call)?.state)
        f.port.failure=null
        assertTrue(f.core.resume(f.call,policy))
        assertEquals(1,f.port.answered.size)
    }
    @Test fun answerMayPublishActiveSynchronouslyWithoutDoubleStart() {
        val f=Fixture(); f.port.reentrantAnswer={f.observe(IncomingCallPhase.ACTIVE,policy)}
        f.observe(IncomingCallPhase.RINGING,policy)
        assertEquals(1,f.port.started.size)
    }
    @Test fun callAlreadyActiveAtServiceReconnectRemainsManualUntilExplicitRequest() {
        val f=Fixture(); f.observe(IncomingCallPhase.ACTIVE,policy)
        assertTrue(f.port.started.isEmpty()); assertTrue(f.core.resume(f.call,policy))
        assertEquals(1,f.port.started.size)
    }
    @Test fun secondCallCannotStealActiveAssistantGeneration() {
        val f=Fixture(); f.observe(IncomingCallPhase.RINGING,policy); f.observe(IncomingCallPhase.ACTIVE,policy)
        val generation=f.port.started.single().second
        val other=Call(); f.core.observe(other,IncomingCallPhase.RINGING,"sim-a",true,true,policy)
        assertEquals(1,f.port.answered.size)
        assertTrue(f.core.isAuthorized(f.call,generation))
    }
    @Test fun configurationReadFailureKeepsCallAvailableToHumanWithoutAnswering() {
        val f=Fixture(); f.port.readinessThrows=true
        f.observe(IncomingCallPhase.RINGING,policy)
        assertTrue(f.port.answered.isEmpty())
        assertEquals(IncomingAssistantState.MANUAL,f.core.snapshot(f.call)?.state)
        assertEquals("incoming_readiness_failed",f.core.snapshot(f.call)?.reason)
    }

    @Test fun reentrantTakeoverDuringReadinessCannotAnswer() {
        val f = Fixture()
        f.port.reentrantReadiness = { f.core.takeOver(f.call) }
        f.observe(IncomingCallPhase.RINGING, policy)
        assertTrue(f.port.answered.isEmpty())
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
    }

    @Test fun reentrantStopOnAnswerTransitionCannotAnswer() {
        val f = Fixture()
        f.port.reentrantChanged = { state ->
            if (state == IncomingAssistantState.ANSWERING) f.core.takeOver(f.call)
        }
        f.observe(IncomingCallPhase.RINGING, policy)
        assertTrue(f.port.answered.isEmpty())
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
    }

    @Test fun reentrantTakeoverDuringStartCannotLaunchAudio() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.port.reentrantChanged = { state ->
            if (state == IncomingAssistantState.STARTING) f.core.takeOver(f.call)
        }
        f.observe(IncomingCallPhase.ACTIVE, policy)
        assertTrue(f.port.started.isEmpty())
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
    }

    @Test fun anotherActiveCallRevokesAudioBeforeOwnershipCanContinue() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ACTIVE, policy)
        val current = f.port.started.single().second
        f.observe(IncomingCallPhase.ACTIVE, policy, exclusive = false)
        assertFalse(f.core.isAuthorized(f.call, current))
        assertEquals(listOf(f.call to current), f.port.stopped)
        assertEquals("another_call_active", f.core.snapshot(f.call)?.reason)
    }

    @Test fun passiveAccountChangeDuringReadinessCannotClaimOldSim() {
        val f = Fixture()
        f.observe(IncomingCallPhase.ACTIVE, policy)
        f.port.reentrantReadiness = {
            f.observe(IncomingCallPhase.ACTIVE, policy, account = "sim-b")
        }
        assertFalse(f.core.resume(f.call, policy))
        assertTrue(f.port.started.isEmpty())
        assertTrue(f.port.answered.isEmpty())
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
    }

    @Test fun passiveLossOfExclusivityDuringReadinessCannotStartAudio() {
        val f = Fixture()
        f.observe(IncomingCallPhase.ACTIVE, policy)
        f.port.reentrantReadiness = {
            f.observe(IncomingCallPhase.ACTIVE, policy, exclusive = false)
        }
        assertFalse(f.core.resume(f.call, policy))
        assertTrue(f.port.started.isEmpty())
        assertFalse(f.core.isAuthorized(f.call, 1L))
    }

    @Test fun secondCallClaimDuringReadinessCannotStartConcurrentAi() {
        val f = Fixture()
        f.observe(IncomingCallPhase.ACTIVE, policy)
        val other = Call()
        f.port.reentrantReadiness = {
            f.port.reentrantReadiness = null
            f.core.observe(other, IncomingCallPhase.RINGING, "sim-a", true, true, policy)
            f.core.observe(other, IncomingCallPhase.ACTIVE, "sim-a", true, true, policy)
        }
        assertFalse(f.core.resume(f.call, policy))
        val generation = f.port.started.single().second
        assertEquals(other, f.port.started.single().first)
        assertTrue(f.core.isAuthorized(other, generation))
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
    }

    @Test fun unchangedReadinessObservationDoesNotSpuriouslyDenyResume() {
        val f = Fixture()
        f.observe(IncomingCallPhase.ACTIVE, policy)
        f.port.reentrantReadiness = {
            f.observe(IncomingCallPhase.ACTIVE, policy)
        }
        assertTrue(f.core.resume(f.call, policy))
        assertEquals(1, f.port.started.size)
    }

    @Test fun sameSimPolicyReplacementRevokesCurrentAiWithoutHangingUp() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ACTIVE, policy)
        val first = f.port.started.single().second

        val replacement = policy.copy(id = "new-consent")
        f.core.policyChanged(replacement, ConversationStopReason.POLICY_REVOKED)
        assertFalse(f.core.isAuthorized(f.call, first))
        assertEquals(listOf(f.call to first), f.port.stopped)
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
        assertTrue(f.port.disconnected.isEmpty())

        // New approval never hijacks the ongoing call, but explicit resume is allowed.
        f.observe(IncomingCallPhase.ACTIVE, replacement)
        assertEquals(1, f.port.started.size)
        assertTrue(f.core.resume(f.call, replacement))
        val second = f.port.started.last().second
        assertNotEquals(first, second)
        assertTrue(f.core.isAuthorized(f.call, second))
        f.core.audioFinished(f.call, first, "late_old_call")
        assertTrue(f.core.isAuthorized(f.call, second))
    }

    @Test fun changingEvidenceConsentRevokesAiEvenWithSamePolicyId() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ACTIVE, policy)
        val first = f.port.started.single().second
        f.core.policyChanged(policy.copy(recordEvidence = false), ConversationStopReason.POLICY_REVOKED)

        assertFalse(f.core.isAuthorized(f.call, first))
        assertEquals(listOf(f.call to first), f.port.stopped)
        assertTrue(f.port.disconnected.isEmpty())
    }

    @Test fun endCallRequiresExactLivePolicyNotJustMatchingSim() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ACTIVE, policy)
        val generation = f.port.started.single().second

        assertFalse(f.core.completeConversation(f.call, generation, policy.copy(id = "replacement")))
        assertFalse(f.core.completeConversation(f.call, generation, policy.copy(recordEvidence = false)))
        assertTrue(f.port.disconnected.isEmpty())
        assertTrue(f.core.isAuthorized(f.call, generation))

        assertTrue(f.core.completeConversation(f.call, generation, policy))
        assertEquals(listOf(f.call), f.port.disconnected)
    }

    @Test fun reentrantPolicyChangeOnEndCallTransitionCannotDisconnect() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ACTIVE, policy)
        val generation = f.port.started.single().second
        f.port.reentrantChanged = { state ->
            if (state == IncomingAssistantState.ENDING) {
                f.core.policyChanged(policy.copy(id = "revoked"), ConversationStopReason.POLICY_REVOKED)
            }
        }
        assertFalse(f.core.completeConversation(f.call, generation, policy))
        assertTrue(f.port.disconnected.isEmpty())
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
    }

    @Test fun refreshingUnchangedPolicyDoesNotInterruptActiveAudio() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ACTIVE, policy)
        val generation = f.port.started.single().second

        f.core.policyChanged(policy.copy(), ConversationStopReason.POLICY_REVOKED)
        assertTrue(f.core.isAuthorized(f.call, generation))
        assertTrue(f.port.stopped.isEmpty())
    }

    @Test fun passivePolicyReplacementRevokesLiveAiEvenWhenSettingsCallbackWasMissed() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ACTIVE, policy)
        val old = f.port.started.single().second
        val replacement = policy.copy(id = "fresh-grant")
        f.observe(IncomingCallPhase.ACTIVE, replacement)
        assertEquals("incoming_policy_changed", f.core.snapshot(f.call)?.reason)
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
        assertFalse(f.core.isAuthorized(f.call, old))
        assertEquals(listOf(f.call to old), f.port.stopped)
        assertTrue(f.port.disconnected.isEmpty())
        assertTrue(f.core.resume(f.call, replacement))
        val resumed = f.port.started.last().second
        assertNotEquals(old, resumed)
        assertTrue(f.core.isAuthorized(f.call, resumed))
    }

    @Test fun ringingConsentChangeWithoutPolicyCallbackNeverAutoAnswers() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy, account = null)
        f.observe(IncomingCallPhase.RINGING, policy.copy(recordEvidence = false))
        assertTrue(f.port.answered.isEmpty())
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
        assertEquals("incoming_policy_changed", f.core.snapshot(f.call)?.reason)
    }

    @Test fun reentrantReadinessPolicyDriftCannotClaimOldSession() {
        val f = Fixture()
        f.observe(IncomingCallPhase.ACTIVE, policy)
        f.port.reentrantReadiness = {
            f.observe(IncomingCallPhase.ACTIVE, policy.copy(recordEvidence = false))
        }
        assertFalse(f.core.resume(f.call, policy))
        assertTrue(f.port.started.isEmpty())
        assertEquals(IncomingAssistantState.MANUAL, f.core.snapshot(f.call)?.state)
    }

    @Test fun throwingAudioCleanupDoesNotCrashHumanTakeoverOrStealResumedCall() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ACTIVE, policy)
        val old = f.port.started.single().second
        f.port.stopThrows = true
        f.core.takeOver(f.call)
        assertFalse(f.core.isAuthorized(f.call, old))
        assertEquals(listOf(f.call to old), f.port.stopped)
        assertTrue(f.port.disconnected.isEmpty())
        f.port.stopThrows = false
        assertTrue(f.core.resume(f.call, policy))
        val resumed = f.port.started.last().second
        f.core.audioFinished(f.call, old, "late_cleanup")
        assertTrue(f.core.isAuthorized(f.call, resumed))
    }

    @Test fun endedCallCannotBeTakenOverOrResumed() {
        val f = Fixture()
        f.observe(IncomingCallPhase.RINGING, policy)
        f.observe(IncomingCallPhase.ENDED, policy)
        f.core.takeOver(f.call)
        assertEquals(IncomingAssistantState.ENDED, f.core.snapshot(f.call)?.state)
        assertFalse(f.core.resume(f.call, policy))
    }
}
