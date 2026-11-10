package pl.michalmatu.aicallbridge.incoming

import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingEndToolEvidencePolicyTest {
    @Test fun awaitingOwnedDisconnectIsNotPrematureSuccess() {
        assertEquals(IncomingEndToolOutcome.PENDING,
            IncomingEndToolEvidencePolicy.decide(false, true))
    }

    @Test fun onlyDisconnectWhileEndingConfirmsEndCall() {
        assertEquals(IncomingEndToolOutcome.CONFIRMED,
            IncomingEndToolEvidencePolicy.decide(true, true))
    }

    @Test fun telecomSuccessIsDeferredUntilRuntimeDrainsWav() {
        val gate = IncomingEndToolArchiveGate()
        gate.onRuntimeStart()
        assertEquals(true, gate.runtimePending())
        assertEquals(null, gate.onTelecomEnd(IncomingEndToolOutcome.CONFIRMED))
        assertEquals(IncomingEndToolOutcome.CONFIRMED, gate.onRuntimeFinished())
        assertEquals(false, gate.runtimePending())
    }

    @Test fun humanHangupRemainsUnknownAfterDrainAndRepeatedRemoval() {
        val gate = IncomingEndToolArchiveGate()
        gate.onRuntimeStart()
        assertEquals(null, gate.onTelecomEnd(IncomingEndToolOutcome.UNKNOWN))
        assertEquals(null, gate.onTelecomEnd(IncomingEndToolOutcome.CONFIRMED))
        assertEquals(IncomingEndToolOutcome.UNKNOWN, gate.onRuntimeFinished())
        assertEquals(IncomingEndToolOutcome.UNKNOWN,
            gate.onTelecomEnd(IncomingEndToolOutcome.CONFIRMED))
    }

    @Test fun serviceTeardownCannotFinalizeArchiveBeforeRuntimeDrains() {
        val gate = IncomingEndToolArchiveGate()
        gate.onRuntimeStart()
        assertEquals(null, gate.onTelecomEnd(IncomingEndToolOutcome.UNKNOWN))
        assertEquals(true, gate.runtimePending())
        assertEquals(IncomingEndToolOutcome.UNKNOWN, gate.onRuntimeFinished())
        assertEquals(false, gate.runtimePending())
    }

    @Test fun alreadyObservedCallEndIsNotOverwrittenByServiceTeardown() {
        val gate = IncomingEndToolArchiveGate()
        gate.onRuntimeStart()
        assertEquals(null, gate.onTelecomEnd(IncomingEndToolOutcome.CONFIRMED))
        assertEquals(null, gate.onTelecomEnd(IncomingEndToolOutcome.UNKNOWN))
        assertEquals(IncomingEndToolOutcome.CONFIRMED, gate.onRuntimeFinished())
    }

    @Test fun noRuntimeAllowsImmediateExactTerminalEvidence() {
        val gate = IncomingEndToolArchiveGate()
        assertEquals(IncomingEndToolOutcome.CONFIRMED,
            gate.onTelecomEnd(IncomingEndToolOutcome.CONFIRMED))
        assertEquals(IncomingEndToolOutcome.CONFIRMED, gate.onRuntimeFinished())
    }

    @Test fun disconnectingRetainsExactEndingOwnerUntilDisconnected() {
        val fence = IncomingEndToolDisconnectFence()
        fence.noteDisconnecting(true)
        assertEquals(true, fence.ownedByEnding(false))
        assertEquals(IncomingEndToolOutcome.PENDING,
            IncomingEndToolEvidencePolicy.decide(false, fence.ownedByEnding(false)))
        assertEquals(IncomingEndToolOutcome.CONFIRMED,
            IncomingEndToolEvidencePolicy.decide(true, fence.ownedByEnding(false)))
    }

    @Test fun manualDisconnectingCannotCreateAnAiEndCallReceipt() {
        val fence = IncomingEndToolDisconnectFence()
        fence.noteDisconnecting(false)
        assertEquals(false, fence.ownedByEnding(false))
        assertEquals(IncomingEndToolOutcome.UNKNOWN,
            IncomingEndToolEvidencePolicy.decide(true, fence.ownedByEnding(false)))
    }

    @Test fun takeoverThenManualHangupIsUnknownNotAiSuccess() {
        assertEquals(IncomingEndToolOutcome.UNKNOWN,
            IncomingEndToolEvidencePolicy.decide(true, false))
        assertEquals(IncomingEndToolOutcome.UNKNOWN,
            IncomingEndToolEvidencePolicy.decide(false, false))
    }
}
