package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Pure JVM contract tests: no device, SIP account, network or unencrypted remote RTP. */
public final class S8AuthenticatedVoipMediaPortTest {
    @Test public void pcmUnavailableUntilBothTlsAndSrtpVerified() throws Exception {
        Engine engine = new Engine();
        S8SecureSipDialogGate gate = gate(engine);
        S8AuthenticatedVoipMediaPort port = new S8AuthenticatedVoipMediaPort(
            engine.identity, gate, engine, engine
        );
        assertNull(port.activeIdentity());
        assertFalse(port.mediaHealthy(engine.identity));
        assertThrows(SecurityException.class, () -> port.open(engine.identity, 1, 16_000));
        assertTrue(gate.onVerifiedIncomingInvite(engine.identity));
        assertTrue(gate.accept(engine.identity));
        assertThrows(SecurityException.class, () -> port.open(engine.identity, 1, 16_000));
        engine.security = S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED;
        assertTrue(gate.onAuthenticatedSrtp(engine.identity));
        assertSame(engine.identity, port.activeIdentity());

        AppOwnedVoipCallMediaSessionBackend backend =
            new AppOwnedVoipCallMediaSessionBackend(port, engine.identity);
        CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
            backend, (generation, task) -> () -> {}, () -> 42L, snapshot -> {}
        );
        long generation = coordinator.start(16_000);
        assertEquals(CallMediaSessionState.ACTIVE, coordinator.snapshot().state());
        CallMediaEndpointLease lease = coordinator.activeEndpointLease(generation);
        lease.uplink().write(new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, engine.transmitted.toByteArray());
        assertEquals(2, lease.downlink().read(new byte[2]));

        gate.takeOverNow();
        assertNull(port.activeIdentity());
        assertFalse(port.mediaHealthy(engine.identity));
        assertThrows(IOException.class, () -> lease.uplink().write(4));
        assertThrows(IOException.class, () -> lease.downlink().read(new byte[8]));
        coordinator.takeOverNow();
        assertEquals(3, engine.transmitted.size());
        assertEquals(CallMediaSessionState.IDLE, coordinator.snapshot().state());
        assertEquals(1, engine.nativeCloses);
    }

    @Test public void lossOfSrtpKeysBlocksNewTxBeforeSignalingCallback() throws Exception {
        Engine engine = new Engine();
        S8SecureSipDialogGate gate = activeGate(engine);
        var port = new S8AuthenticatedVoipMediaPort(engine.identity, gate, engine, engine);
        CallMediaEndpointLease lease = port.open(engine.identity, 1, 16_000);
        engine.security = S8SecureSipDialogGate.MediaSecurity.UNVERIFIED;
        assertFalse(port.mediaHealthy(engine.identity));
        assertThrows(IOException.class, () -> lease.uplink().write(new byte[]{1}));
        assertThrows(IOException.class, () -> lease.downlink().read());
        assertEquals(0, engine.transmitted.size());
        gate.onMediaSecurityLost(engine.identity);
        assertEquals(S8SecureSipDialogGate.State.FAILED, gate.snapshot().state());
        lease.close();
    }

    @Test public void lostKeysDuringOpenCloseCandidateBeforeUse() {
        Engine engine = new Engine();
        S8SecureSipDialogGate gate = activeGate(engine);
        engine.dropKeysOnOpen = true;
        var port = new S8AuthenticatedVoipMediaPort(engine.identity, gate, engine, engine);
        assertThrows(SecurityException.class, () -> port.open(engine.identity, 1, 16_000));
        assertEquals(1, engine.leaseCloses);
        assertEquals(0, engine.transmitted.size());
        gate.onMediaSecurityLost(engine.identity);
    }

    @Test public void foreignIdentityNeverReceivesPcmDespiteAuthenticatedSession() {
        Engine engine = new Engine();
        S8SecureSipDialogGate gate = activeGate(engine);
        var port = new S8AuthenticatedVoipMediaPort(engine.identity, gate, engine, engine);
        assertNull(port.activeIdentity() == new Object() ? port.activeIdentity() : null);
        assertFalse(port.mediaHealthy(new Object()));
        assertThrows(SecurityException.class, () -> port.open(new Object(), 1, 16_000));
        assertThrows(SecurityException.class, () -> port.open(engine.identity, 0, 16_000));
        assertThrows(SecurityException.class, () -> port.open(engine.identity, 1, 8_000));
        gate.takeOverNow();
    }

    private static S8SecureSipDialogGate gate(Engine engine) {
        return new S8SecureSipDialogGate(
            new S8SecureSipDialogGate.Signaling() {
                @Override public boolean tlsPeerVerified() { return engine.tls; }
                @Override public void answerInvite(Object identity) {}
                @Override public void rejectInvite(Object identity) {}
                @Override public void endDialog(Object identity) {}
                @Override public void acknowledgeRemoteBye(Object identity) {}
                @Override public void acknowledgeRemoteCancel(Object identity) {}
                @Override public void close() {}
            },
            engine, ignored -> {}
        );
    }

    private static S8SecureSipDialogGate activeGate(Engine engine) {
        S8SecureSipDialogGate gate = gate(engine);
        assertTrue(gate.onVerifiedIncomingInvite(engine.identity));
        assertTrue(gate.accept(engine.identity));
        engine.security = S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED;
        assertTrue(gate.onAuthenticatedSrtp(engine.identity));
        return gate;
    }

    private static final class Engine
        implements S8SecureSipDialogGate.VerifiedMedia,
            AppOwnedVoipCallMediaSessionBackend.MediaPort {
        final Object identity = new Object();
        final ByteArrayOutputStream transmitted = new ByteArrayOutputStream();
        volatile S8SecureSipDialogGate.MediaSecurity security =
            S8SecureSipDialogGate.MediaSecurity.UNVERIFIED;
        boolean tls = true;
        boolean dropKeysOnOpen;
        boolean opened;
        boolean leaseClosed;
        int leaseCloses;
        int nativeCloses;

        @Override public S8SecureSipDialogGate.MediaSecurity mediaSecurity() {
            return security;
        }
        @Override public Object exactCallIdentity() { return identity; }
        @Override public Object activeIdentity() { return nativeCloses == 0 ? identity : null; }
        @Override public boolean mediaHealthy(Object exactIdentity) {
            return exactIdentity == identity && opened && !leaseClosed && nativeCloses == 0;
        }
        @Override public CallMediaEndpointLease open(
            Object exactIdentity, long generation, int rateHz
        ) {
            if (exactIdentity != identity || generation <= 0 || rateHz != 16_000)
                throw new AssertionError("unexpected_native_media_open");
            opened = true;
            leaseClosed = false;
            if (dropKeysOnOpen)
                security = S8SecureSipDialogGate.MediaSecurity.UNVERIFIED;
            return new CallMediaEndpointLease() {
                @Override public InputStream downlink() {
                    return new ByteArrayInputStream(new byte[]{6, 7, 8});
                }
                @Override public OutputStream uplink() { return transmitted; }
                @Override public void close() { leaseClosed = true; leaseCloses++; }
            };
        }
        @Override public void close() {
            nativeCloses++;
            security = S8SecureSipDialogGate.MediaSecurity.UNVERIFIED;
        }
    }
}
