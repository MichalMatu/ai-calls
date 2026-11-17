package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** SIP/SRTP state contract only. No PJSIP native load, real SIP or network. */
public final class S8SecureSipDialogGateTest {
    @Test public void unverifiedTlsOrForeignIdentityCannotRing() {
        var events = new ArrayList<String>();
        var media = new Media(events);
        var signaling = new Signaling(events);
        var gate = new S8SecureSipDialogGate(signaling, media, ignored -> {});
        signaling.tls = false;
        assertFalse(gate.onVerifiedIncomingInvite(media.identity));
        assertEquals(S8SecureSipDialogGate.State.IDLE, gate.snapshot().state());
        signaling.tls = true;
        assertFalse(gate.onVerifiedIncomingInvite(new Object()));
        assertFalse(gate.accept(media.identity));
        assertEquals(0, signaling.answers);
        gate.close();
    }

    @Test public void inviteAcceptanceWaitsForAuthenticatedSrtpAndRejectsBareRtp() {
        var events = new ArrayList<String>();
        var media = new Media(events);
        var signaling = new Signaling(events);
        var gate = new S8SecureSipDialogGate(signaling, media, ignored -> {});
        assertTrue(gate.onVerifiedIncomingInvite(media.identity));
        assertEquals(S8SecureSipDialogGate.State.RINGING, gate.snapshot().state());
        assertTrue(gate.accept(media.identity));
        assertEquals(1, signaling.answers);
        assertEquals(S8SecureSipDialogGate.State.NEGOTIATING, gate.snapshot().state());
        assertFalse(gate.snapshot().mediaReady());
        assertFalse(gate.onAuthenticatedSrtp(media.identity)); // No SRTP keys.
        assertFalse(gate.onAuthenticatedSrtp(new Object()));
        media.security = S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED;
        assertTrue(gate.onAuthenticatedSrtp(media.identity));
        assertTrue(gate.snapshot().mediaReady());
        assertEquals(S8SecureSipDialogGate.State.ACTIVE, gate.snapshot().state());
        gate.takeOverNow();
        assertEquals(List.of("answer", "media-close", "bye", "signaling-close"), events);
        assertFalse(gate.onAuthenticatedSrtp(media.identity));
        assertFalse(gate.snapshot().mediaReady());
    }

    @Test public void ringingStopRejectsInviteWithoutSendingBye() {
        var events = new ArrayList<String>();
        var media = new Media(events);
        var signaling = new Signaling(events);
        var gate = new S8SecureSipDialogGate(signaling, media, ignored -> {});
        assertTrue(gate.onVerifiedIncomingInvite(media.identity));
        gate.takeOverNow();
        gate.takeOverNow();
        assertEquals(S8SecureSipDialogGate.State.STOPPED, gate.snapshot().state());
        assertEquals(List.of("media-close", "reject", "signaling-close"), events);
        assertEquals(0, signaling.byes);
        assertFalse(gate.accept(media.identity));
    }

    @Test public void remoteByeAcknowledgesOnlyWithoutReenteringSipDialog() {
        var events = new ArrayList<String>();
        var media = new Media(events);
        var signaling = new Signaling(events);
        var gate = new S8SecureSipDialogGate(signaling, media, ignored -> {});
        assertTrue(gate.onVerifiedIncomingInvite(media.identity));
        assertTrue(gate.accept(media.identity));
        media.security = S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED;
        assertTrue(gate.onAuthenticatedSrtp(media.identity));
        gate.onRemoteBye(new Object());
        assertEquals(S8SecureSipDialogGate.State.ACTIVE, gate.snapshot().state());
        gate.onRemoteBye(media.identity);
        assertEquals(S8SecureSipDialogGate.State.STOPPED, gate.snapshot().state());
        assertEquals(List.of("answer", "media-close", "ack-bye", "signaling-close"), events);
        assertEquals(0, signaling.byes);
    }

    @Test public void remoteCancelIsBoundToUnansweredInviteAndAcknowledgedOnce() {
        var events = new ArrayList<String>();
        var media = new Media(events);
        var signaling = new Signaling(events);
        var gate = new S8SecureSipDialogGate(signaling, media, ignored -> {});
        assertTrue(gate.onVerifiedIncomingInvite(media.identity));
        gate.onRemoteCancel(new Object());
        assertEquals(S8SecureSipDialogGate.State.RINGING, gate.snapshot().state());
        assertTrue(events.isEmpty());
        gate.onRemoteCancel(media.identity);
        gate.onRemoteCancel(media.identity);
        assertEquals(S8SecureSipDialogGate.State.STOPPED, gate.snapshot().state());
        assertEquals(List.of("media-close", "cancel-ack", "signaling-close"), events);
        assertEquals(0, signaling.answers);
        assertEquals(0, signaling.byes);
    }

    @Test public void cancelCannotReplaceByeOnceAnswerWasSent() {
        var events = new ArrayList<String>();
        var media = new Media(events);
        var signaling = new Signaling(events);
        var gate = new S8SecureSipDialogGate(signaling, media, ignored -> {});
        assertTrue(gate.onVerifiedIncomingInvite(media.identity));
        assertTrue(gate.accept(media.identity));
        gate.onRemoteCancel(media.identity);
        assertEquals(S8SecureSipDialogGate.State.NEGOTIATING, gate.snapshot().state());
        assertEquals(List.of("answer"), events);
        gate.takeOverNow();
        assertEquals(List.of("answer", "media-close", "bye", "signaling-close"), events);
    }

    @Test public void nativeKeyLossFailsClosedAndRevokesMediaFirst() {
        var events = new ArrayList<String>();
        var media = new Media(events);
        var signaling = new Signaling(events);
        var gate = new S8SecureSipDialogGate(signaling, media, ignored -> {});
        assertTrue(gate.onVerifiedIncomingInvite(media.identity));
        assertTrue(gate.accept(media.identity));
        media.security = S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED;
        assertTrue(gate.onAuthenticatedSrtp(media.identity));
        gate.onMediaSecurityLost(media.identity);
        assertEquals(S8SecureSipDialogGate.State.FAILED, gate.snapshot().state());
        assertFalse(gate.snapshot().mediaReady());
        assertEquals(List.of("answer", "media-close", "bye", "signaling-close"), events);
    }

    private static final class Media implements S8SecureSipDialogGate.VerifiedMedia {
        final Object identity = new Object();
        final List<String> events;
        volatile S8SecureSipDialogGate.MediaSecurity security =
            S8SecureSipDialogGate.MediaSecurity.UNVERIFIED;
        Media(List<String> events) { this.events = events; }
        @Override public S8SecureSipDialogGate.MediaSecurity mediaSecurity() {
            return security;
        }
        @Override public Object exactCallIdentity() { return identity; }
        @Override public void close() {
            events.add("media-close");
            security = S8SecureSipDialogGate.MediaSecurity.UNVERIFIED;
        }
    }

    private static final class Signaling implements S8SecureSipDialogGate.Signaling {
        final List<String> events;
        boolean tls = true;
        int answers;
        int byes;
        Signaling(List<String> events) { this.events = events; }
        @Override public boolean tlsPeerVerified() { return tls; }
        @Override public void answerInvite(Object ignored) {
            answers++;
            events.add("answer");
        }
        @Override public void rejectInvite(Object ignored) { events.add("reject"); }
        @Override public void endDialog(Object ignored) { byes++; events.add("bye"); }
        @Override public void acknowledgeRemoteBye(Object ignored) { events.add("ack-bye"); }
        @Override public void acknowledgeRemoteCancel(Object ignored) {
            events.add("cancel-ack");
        }
        @Override public void close() { events.add("signaling-close"); }
    }
}
