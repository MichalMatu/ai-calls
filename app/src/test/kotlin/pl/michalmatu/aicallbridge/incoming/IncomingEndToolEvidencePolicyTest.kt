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

    @Test fun takeoverThenManualHangupIsUnknownNotAiSuccess() {
        assertEquals(IncomingEndToolOutcome.UNKNOWN,
            IncomingEndToolEvidencePolicy.decide(true, false))
        assertEquals(IncomingEndToolOutcome.UNKNOWN,
            IncomingEndToolEvidencePolicy.decide(false, false))
    }
}
