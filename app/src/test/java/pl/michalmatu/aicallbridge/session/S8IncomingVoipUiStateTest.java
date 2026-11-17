package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;
import org.junit.Test;

public final class S8IncomingVoipUiStateTest {
    @Test public void noNativeTransportCannotDisplayAvailableActions() {
        for (S8SecureSipDialogGate.State state : S8SecureSipDialogGate.State.values()) {
            var view = S8IncomingVoipUiState.from(
                new S8SecureSipDialogGate.Snapshot(state, 5, true), false);
            assertEquals("native_sip_unavailable", view.statusCode());
            assertFalse(view.canAccept());
            assertFalse(view.canReject());
            assertFalse(view.canHangUp());
            assertFalse(view.authenticatedMedia());
        }
    }

    @Test public void onlyRingingStatePermitsAcceptOrReject() {
        var ringing = S8IncomingVoipUiState.from(
            new S8SecureSipDialogGate.Snapshot(
                S8SecureSipDialogGate.State.RINGING, 1, false), true);
        assertEquals("ringing", ringing.statusCode());
        assertTrue(ringing.canAccept());
        assertTrue(ringing.canReject());
        assertFalse(ringing.canHangUp());

        var waiting = S8IncomingVoipUiState.from(
            new S8SecureSipDialogGate.Snapshot(
                S8SecureSipDialogGate.State.IDLE, 1, false), true);
        assertEquals("waiting", waiting.statusCode());
        assertFalse(waiting.canAccept());
        assertFalse(waiting.canReject());
    }

    @Test public void secureActiveAndInterruptedNegotiatingOfferHangupOnly() {
        var negotiating = S8IncomingVoipUiState.from(
            new S8SecureSipDialogGate.Snapshot(
                S8SecureSipDialogGate.State.NEGOTIATING, 1, false), true);
        assertTrue(negotiating.canHangUp());
        assertFalse(negotiating.authenticatedMedia());
        var connected = S8IncomingVoipUiState.from(
            new S8SecureSipDialogGate.Snapshot(
                S8SecureSipDialogGate.State.ACTIVE, 1, true), true);
        assertEquals("connected", connected.statusCode());
        assertTrue(connected.authenticatedMedia());
        assertTrue(connected.canHangUp());
        assertFalse(connected.canAccept());
    }

    @Test public void activeWithoutAuthenticatedMediaNeverClaimsConnected() {
        var view = S8IncomingVoipUiState.from(
            new S8SecureSipDialogGate.Snapshot(
                S8SecureSipDialogGate.State.ACTIVE, 1, false), true);
        assertEquals("security_failure", view.statusCode());
        assertFalse(view.authenticatedMedia());
        assertFalse(view.canAccept());
        assertTrue(view.canHangUp());
    }

    @Test public void endedOrFailedCannotBeAnswered() {
        for (var state : new S8SecureSipDialogGate.State[]{
            S8SecureSipDialogGate.State.STOPPED,
            S8SecureSipDialogGate.State.FAILED
        }) {
            var view = S8IncomingVoipUiState.from(
                new S8SecureSipDialogGate.Snapshot(state, 1, false), true);
            assertFalse(view.canAccept());
            assertFalse(view.canReject());
            assertFalse(view.canHangUp());
        }
    }
}
