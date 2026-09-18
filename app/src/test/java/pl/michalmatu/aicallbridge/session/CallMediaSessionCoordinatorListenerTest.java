package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class CallMediaSessionCoordinatorListenerTest {
    @Test
    public void addedListenerReceivesOnlyFutureSnapshotsAndCanDetach() throws Exception {
        FakeBackend backend = new FakeBackend();
        List<CallMediaSessionState> constructorStates = new ArrayList<>();
        List<CallMediaSessionState> addedStates = new ArrayList<>();
        CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
            backend,
            new NoopHeartbeatScheduler(),
            () -> 1L,
            snapshot -> constructorStates.add(snapshot.state())
        );

        AutoCloseable subscription = coordinator.addListener(
            snapshot -> addedStates.add(snapshot.state())
        );
        assertTrue("addListener must not replay current state", addedStates.isEmpty());

        coordinator.start(16_000);
        assertEquals(
            List.of(
                CallMediaSessionState.BINDING,
                CallMediaSessionState.PREPARING,
                CallMediaSessionState.ACTIVE
            ),
            addedStates
        );

        subscription.close();
        coordinator.takeOverNow();

        assertEquals(3, addedStates.size());
        assertTrue(constructorStates.contains(CallMediaSessionState.STOPPING));
        assertTrue(constructorStates.contains(CallMediaSessionState.IDLE));
    }

    @Test
    public void throwingListenerDoesNotBlockOtherListeners() {
        FakeBackend backend = new FakeBackend();
        AtomicInteger healthyEvents = new AtomicInteger();
        CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
            backend,
            new NoopHeartbeatScheduler(),
            () -> 1L,
            ignored -> {}
        );
        coordinator.addListener(snapshot -> {
            throw new IllegalStateException("observer boom");
        });
        coordinator.addListener(snapshot -> healthyEvents.incrementAndGet());

        coordinator.start(16_000);

        assertEquals(3, healthyEvents.get());
        assertEquals(CallMediaSessionState.ACTIVE, coordinator.snapshot().state());
    }

    @Test
    public void listenerCallbackCanReadSnapshotWithoutCoordinatorLockReentryDependency() {
        FakeBackend backend = new FakeBackend();
        AtomicInteger reads = new AtomicInteger();
        final CallMediaSessionCoordinator[] holder = new CallMediaSessionCoordinator[1];
        CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
            backend,
            new NoopHeartbeatScheduler(),
            () -> 1L,
            ignored -> {}
        );
        holder[0] = coordinator;
        coordinator.addListener(snapshot -> {
            assertEquals(snapshot.generation(), holder[0].snapshot().generation());
            reads.incrementAndGet();
        });

        coordinator.start(16_000);

        assertEquals(3, reads.get());
        assertFalse(coordinator.snapshot().state() == CallMediaSessionState.FAILED);
    }

    private static final class FakeBackend implements CallMediaSessionBackend {
        private final FakeLease lease = new FakeLease();

        @Override
        public void bind(long generation, BindCallback callback) {
            callback.onBound(generation);
        }

        @Override
        public void prepare(long generation, int sampleRateHz) {}

        @Override
        public CallMediaEndpointLease start(long generation) {
            return lease;
        }

        @Override
        public boolean heartbeat(long generation) {
            return true;
        }

        @Override
        public void abort(long generation) {}

        @Override
        public void unbind(long generation) {}
    }

    private static final class FakeLease implements CallMediaEndpointLease {
        @Override
        public InputStream downlink() {
            return InputStream.nullInputStream();
        }

        @Override
        public OutputStream uplink() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public void close() {}
    }

    private static final class NoopHeartbeatScheduler implements CallMediaHeartbeatScheduler {
        @Override
        public AutoCloseable start(long generation, Runnable heartbeat) {
            return () -> {};
        }
    }
}
