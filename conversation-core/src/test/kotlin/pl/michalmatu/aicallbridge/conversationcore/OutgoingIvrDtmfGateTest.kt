package pl.michalmatu.aicallbridge.conversationcore

import org.junit.Assert.*
import org.junit.Test

class OutgoingIvrDtmfGateTest {
    private val request = OutgoingIvrDtmfRequest(7L, 1, 0, '1')

    @Test fun authorizedDigitDispatchesOnceAndDuplicateDoesNotReplay() {
        var sends = 0
        val gate = OutgoingIvrDtmfGate({ it == 7L }, { true }) {
            sends++
            true
        }
        assertEquals(OutgoingIvrDtmfResult.DISPATCHED, gate.request(request))
        assertEquals(OutgoingIvrDtmfResult.REUSED, gate.request(request))
        assertEquals(1, sends)
    }

    @Test fun differentDigitForSameStepIsRejectedRatherThanReplaced() {
        val gate = OutgoingIvrDtmfGate({ true }, { true }) { true }
        assertEquals(OutgoingIvrDtmfResult.DISPATCHED, gate.request(request))
        assertEquals(OutgoingIvrDtmfResult.CONFLICT, gate.request(request.copy(digit = '2')))
    }

    @Test fun invalidDigitGenerationOrStepNeverDispatches() {
        var sends = 0
        val gate = OutgoingIvrDtmfGate({ true }, { true }) { sends++; true }
        for (candidate in listOf(request.copy(digit = 'x'), request.copy(callGeneration = 0),
                request.copy(turn = 0), request.copy(stepIndex = -1))) {
            assertEquals(OutgoingIvrDtmfResult.INVALID_REQUEST, gate.request(candidate))
        }
        assertEquals(0, sends)
    }

    @Test fun exactCallMustStillBeActive() {
        var sends = 0
        val gate = OutgoingIvrDtmfGate({ it == 6L }, { true }) { sends++; true }
        assertEquals(OutgoingIvrDtmfResult.NOT_ACTIVE, gate.request(request))
        assertEquals(0, sends)
    }

    @Test fun deniedGrantCannotDispatch() {
        var sends = 0
        val gate = OutgoingIvrDtmfGate({ true }, { false }) { sends++; true }
        assertEquals(OutgoingIvrDtmfResult.NOT_AUTHORIZED, gate.request(request))
        assertEquals(0, sends)
    }

    @Test fun stopDuringAuthorizationIsFencedAtDispatchBoundary() {
        var active = true
        var sends = 0
        val gate = OutgoingIvrDtmfGate({ active }, {
            active = false
            true
        }) { sends++; true }
        assertEquals(OutgoingIvrDtmfResult.NOT_ACTIVE, gate.request(request))
        assertEquals(0, sends)
    }

    @Test fun revokedGrantBetweenChecksIsRejectedBeforeDispatch() {
        var checks = 0
        val gate = OutgoingIvrDtmfGate({ true }, { checks++ == 0 }) { true }
        assertEquals(OutgoingIvrDtmfResult.NOT_AUTHORIZED, gate.request(request))
        assertEquals(2, checks)
    }

    @Test fun unknownDispatchOutcomeNeverReplaysHardwareEffect() {
        var calls = 0
        val gate = OutgoingIvrDtmfGate({ true }, { true }) {
            calls++
            throw IllegalStateException("lost telecom response")
        }
        assertEquals(OutgoingIvrDtmfResult.UNKNOWN, gate.request(request))
        assertEquals(OutgoingIvrDtmfResult.REUSED, gate.request(request))
        assertEquals(1, calls)
    }

    @Test fun independentStepAndCallGenerationCanProceed() {
        val sent = mutableListOf<OutgoingIvrDtmfRequest>()
        val gate = OutgoingIvrDtmfGate({ true }, { true }) {
            sent += it
            true
        }
        assertEquals(OutgoingIvrDtmfResult.DISPATCHED, gate.request(request))
        assertEquals(OutgoingIvrDtmfResult.DISPATCHED, gate.request(request.copy(stepIndex = 1, digit = '#')))
        assertEquals(OutgoingIvrDtmfResult.DISPATCHED, gate.request(request.copy(callGeneration = 8L)))
        assertEquals(3, sent.size)
    }
}
