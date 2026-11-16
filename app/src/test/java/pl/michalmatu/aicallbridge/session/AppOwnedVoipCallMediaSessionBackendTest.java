package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class AppOwnedVoipCallMediaSessionBackendTest {
    private static final class FakeLease implements CallMediaEndpointLease {
        int closes;
        @Override public InputStream downlink() { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream uplink() { return new ByteArrayOutputStream(); }
        @Override public void close() { closes++; }
    }

    private static final class FakePort implements AppOwnedVoipCallMediaSessionBackend.MediaPort {
        Object active;
        boolean healthy = true;
        int opens;
        int sampleRateHz;
        Runnable duringOpen;
        FakeLease next = new FakeLease();
        @Override public Object activeIdentity() { return active; }
        @Override public boolean mediaHealthy(Object exactIdentity) { return healthy && active == exactIdentity; }
        @Override public CallMediaEndpointLease open(Object exactIdentity, long generation, int rate) {
            assertSame(active, exactIdentity);
            opens++;
            sampleRateHz = rate;
            if (duringOpen != null) duringOpen.run();
            return next;
        }
    }

    private static final class Callback implements CallMediaSessionBackend.BindCallback {
        long bound = -1L;
        Throwable failure;
        @Override public void onBound(long generation) { bound = generation; }
        @Override public void onDisconnected(long generation) { fail("unexpected_disconnect"); }
        @Override public void onBindFailed(long generation, Throwable error) { failure = error; }
    }

    @Test public void requiresTheExactAppOwnedIdentityNotEqualLookingHandle() {
        Object expected = new String("opaque-call");
        FakePort port = new FakePort();
        port.active = new String("opaque-call");
        AppOwnedVoipCallMediaSessionBackend backend =
            new AppOwnedVoipCallMediaSessionBackend(port, expected);
        Callback callback = new Callback();
        backend.bind(1L, callback);
        assertEquals(-1L, callback.bound);
        assertNotNull(callback.failure);
        assertEquals(0, port.opens);
        backend.close();
    }

    @Test public void opensPcmForOneGenerationAndStopOnlyClosesOwnedLease() {
        Object identity = new Object();
        FakePort port = new FakePort();
        port.active = identity;
        AppOwnedVoipCallMediaSessionBackend backend =
            new AppOwnedVoipCallMediaSessionBackend(port, identity);
        Callback callback = new Callback();
        backend.bind(9L, callback);
        assertEquals(9L, callback.bound);
        backend.prepare(9L, 16000);
        assertNotNull(backend.start(9L));
        assertEquals(16000, port.sampleRateHz);
        assertTrue(backend.heartbeat(9L));
        backend.abort(8L); // A stale STOP cannot touch the active generation.
        assertEquals(0, port.next.closes);
        backend.abort(9L);
        assertEquals(1, port.next.closes);
        assertFalse(backend.heartbeat(9L));
        backend.unbind(9L);
        backend.close();
        assertEquals(1, port.next.closes);
    }

    @Test public void rejectsChangedCallAndClosesStaleOpenLease() {
        Object owner = new Object();
        FakePort port = new FakePort();
        port.active = owner;
        AppOwnedVoipCallMediaSessionBackend backend =
            new AppOwnedVoipCallMediaSessionBackend(port, owner);
        Callback callback = new Callback();
        backend.bind(15L, callback);
        backend.prepare(15L, 24000);
        port.duringOpen = () -> port.active = new Object();
        try {
            backend.start(15L);
            fail("must reject media after ownership changed");
        } catch (IllegalStateException expected) {
            assertEquals("voip_call_changed_during_open", expected.getMessage());
        }
        assertEquals(1, port.next.closes);
        assertFalse(backend.heartbeat(15L));
        backend.abort(15L);
        assertEquals(1, port.next.closes);
    }

    @Test public void stopDuringBlockingOpenCannotResurrectMedia() {
        Object owner = new Object();
        FakePort port = new FakePort();
        port.active = owner;
        AppOwnedVoipCallMediaSessionBackend backend =
            new AppOwnedVoipCallMediaSessionBackend(port, owner);
        backend.bind(21L, new Callback());
        backend.prepare(21L, 16000);
        port.duringOpen = () -> backend.abort(21L);
        assertThrows(IllegalStateException.class, () -> backend.start(21L));
        assertEquals(1, port.next.closes);
        assertFalse(backend.heartbeat(21L));
    }

    @Test public void coordinatorStopNeverDoubleClosesAnOwnedVoipLease() {
        Object identity = new Object();
        FakePort port = new FakePort();
        port.active = identity;
        AppOwnedVoipCallMediaSessionBackend backend =
            new AppOwnedVoipCallMediaSessionBackend(port, identity);
        CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
            backend, (generation, heartbeat) -> () -> {}, () -> 123L, ignored -> {}
        );
        assertEquals(1L, coordinator.start(16000));
        assertEquals(CallMediaSessionState.ACTIVE, coordinator.snapshot().state());
        coordinator.takeOverNow();
        assertEquals(CallMediaSessionState.IDLE, coordinator.snapshot().state());
        assertEquals(1, port.next.closes);
        coordinator.close();
        backend.close();
        assertEquals(1, port.next.closes);
    }

    @Test public void staleAbortDoesNotStopNewGeneration() {
        Object owner = new Object();
        FakePort port = new FakePort();
        port.active = owner;
        AppOwnedVoipCallMediaSessionBackend backend =
            new AppOwnedVoipCallMediaSessionBackend(port, owner);
        backend.bind(1L, new Callback());
        backend.prepare(1L, 16000);
        FakeLease first = port.next;
        backend.start(1L);
        backend.abort(1L);
        port.next = new FakeLease();
        backend.bind(2L, new Callback());
        backend.prepare(2L, 16000);
        backend.start(2L);
        backend.abort(1L);
        assertEquals(1, first.closes);
        assertEquals(0, port.next.closes);
        assertTrue(backend.heartbeat(2L));
        backend.close();
        assertEquals(1, port.next.closes);
        Callback rejected = new Callback();
        backend.bind(3L, rejected);
        assertNotNull(rejected.failure);
    }
}
