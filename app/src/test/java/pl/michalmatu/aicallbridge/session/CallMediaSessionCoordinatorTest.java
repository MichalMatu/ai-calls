package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class CallMediaSessionCoordinatorTest {
    @Test
    public void startTransitionsBindingPreparingActive() {
        FakeBackend backend = new FakeBackend();
        ManualHeartbeatScheduler scheduler = new ManualHeartbeatScheduler();
        List<CallMediaSessionSnapshot> snapshots = new ArrayList<>();
        CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
            backend,
            scheduler,
            () -> 123L,
            snapshots::add
        );

        long generation = coordinator.start(16_000);

        assertEquals(1L, generation);
        assertEquals(CallMediaSessionState.ACTIVE, coordinator.snapshot().state());
        assertEquals(
            List.of(
                CallMediaSessionState.IDLE,
                CallMediaSessionState.BINDING,
                CallMediaSessionState.PREPARING,
                CallMediaSessionState.ACTIVE
            ),
            states(snapshots)
        );
        assertEquals(1, backend.bindCalls);
        assertEquals(1, backend.prepareCalls);
        assertEquals(1, backend.startCalls);
        assertEquals(1, scheduler.startCalls);
    }

    @Test
    public void startWhileActiveIsRejected() {
        FakeBackend backend = new FakeBackend();
        CallMediaSessionCoordinator coordinator = newCoordinator(backend, new ManualHeartbeatScheduler());
        coordinator.start(16_000);

        try {
            coordinator.start(16_000);
            fail("second start should fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("ACTIVE"));
        }
    }

    @Test
    public void prepareFailureFailsClosed() {
        FakeBackend backend = new FakeBackend();
        backend.prepareFailure = new IllegalStateException("prepare boom");
        CallMediaSessionCoordinator coordinator = newCoordinator(backend, new ManualHeartbeatScheduler());

        coordinator.start(16_000);

        CallMediaSessionSnapshot snapshot = coordinator.snapshot();
        assertEquals(CallMediaSessionState.FAILED, snapshot.state());
        assertEquals(CallMediaSessionFailure.PREPARE_FAILED, snapshot.failure());
        assertEquals(1, backend.abortCalls);
        assertEquals(1, backend.unbindCalls);
        assertFalse(backend.endpoint.open);
    }

    @Test
    public void helperDisconnectFailsGenerationAndClosesEndpoints() {
        FakeBackend backend = new FakeBackend();
        CallMediaSessionCoordinator coordinator = newCoordinator(backend, new ManualHeartbeatScheduler());
        long generation = coordinator.start(16_000);
        assertTrue(backend.endpoint.open);

        backend.disconnect(generation);

        assertEquals(CallMediaSessionState.FAILED, coordinator.snapshot().state());
        assertEquals(CallMediaSessionFailure.HELPER_DISCONNECTED, coordinator.snapshot().failure());
        assertFalse(backend.endpoint.open);
        assertEquals(1, backend.abortCalls);
        assertEquals(1, backend.unbindCalls);
    }

    @Test
    public void heartbeatFailureFailsClosed() {
        FakeBackend backend = new FakeBackend();
        ManualHeartbeatScheduler scheduler = new ManualHeartbeatScheduler();
        CallMediaSessionCoordinator coordinator = newCoordinator(backend, scheduler);
        coordinator.start(16_000);
        backend.heartbeatOk = false;

        scheduler.fire();

        assertEquals(CallMediaSessionState.FAILED, coordinator.snapshot().state());
        assertEquals(CallMediaSessionFailure.HEARTBEAT_FAILED, coordinator.snapshot().failure());
        assertFalse(backend.endpoint.open);
        assertEquals(1, backend.abortCalls);
    }

    @Test
    public void takeOverAbortsLocallyAndReturnsIdle() {
        FakeBackend backend = new FakeBackend();
        ManualHeartbeatScheduler scheduler = new ManualHeartbeatScheduler();
        List<CallMediaSessionSnapshot> snapshots = new ArrayList<>();
        CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
            backend,
            scheduler,
            () -> 7L,
            snapshots::add
        );
        coordinator.start(16_000);

        coordinator.takeOverNow();

        assertEquals(CallMediaSessionState.IDLE, coordinator.snapshot().state());
        assertFalse(backend.endpoint.open);
        assertEquals(1, backend.abortCalls);
        assertEquals(1, backend.unbindCalls);
        assertTrue(states(snapshots).contains(CallMediaSessionState.STOPPING));
        assertFalse(scheduler.active);
    }

    @Test
    public void staleDisconnectCannotFailNewGeneration() {
        FakeBackend backend = new FakeBackend();
        ManualHeartbeatScheduler scheduler = new ManualHeartbeatScheduler();
        CallMediaSessionCoordinator coordinator = newCoordinator(backend, scheduler);

        long first = coordinator.start(16_000);
        coordinator.takeOverNow();
        long second = coordinator.start(16_000);
        assertNotEquals(first, second);

        backend.disconnect(first);

        assertEquals(second, coordinator.snapshot().generation());
        assertEquals(CallMediaSessionState.ACTIVE, coordinator.snapshot().state());
    }

    @Test
    public void takeOverDoesNotWaitForBlockedPrepare() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.blockPrepare = true;
        CallMediaSessionCoordinator coordinator = newCoordinator(backend, new ManualHeartbeatScheduler());
        Thread starter = new Thread(() -> coordinator.start(16_000), "coordinator-test-start");
        Thread takeover = new Thread(coordinator::takeOverNow, "coordinator-test-takeover");

        starter.start();
        assertTrue("prepare did not start", backend.prepareEntered.await(1, TimeUnit.SECONDS));
        takeover.start();

        try {
            takeover.join(250L);
            assertFalse("TAKE OVER waited for blocked prepare", takeover.isAlive());
            assertEquals(1, backend.abortCalls);
        } finally {
            backend.releasePrepare.countDown();
            starter.join(1_000L);
            takeover.join(1_000L);
        }

        assertFalse("starter did not finish", starter.isAlive());
        assertFalse("takeover did not finish", takeover.isAlive());
        assertEquals(0, backend.startCalls);
        assertEquals(CallMediaSessionState.IDLE, coordinator.snapshot().state());
    }

    @Test
    public void closeIsIdempotent() {
        FakeBackend backend = new FakeBackend();
        CallMediaSessionCoordinator coordinator = newCoordinator(backend, new ManualHeartbeatScheduler());
        coordinator.start(16_000);

        coordinator.close();
        coordinator.close();

        assertEquals(CallMediaSessionState.IDLE, coordinator.snapshot().state());
        assertEquals(1, backend.abortCalls);
        assertEquals(1, backend.unbindCalls);
    }

    private static CallMediaSessionCoordinator newCoordinator(
        FakeBackend backend,
        ManualHeartbeatScheduler scheduler
    ) {
        return new CallMediaSessionCoordinator(backend, scheduler, () -> 1L, ignored -> {});
    }

    private static List<CallMediaSessionState> states(List<CallMediaSessionSnapshot> snapshots) {
        List<CallMediaSessionState> result = new ArrayList<>();
        for (CallMediaSessionSnapshot snapshot : snapshots) {
            result.add(snapshot.state());
        }
        return result;
    }

    private static final class FakeBackend implements CallMediaSessionBackend {
        int bindCalls;
        int prepareCalls;
        int startCalls;
        int abortCalls;
        int unbindCalls;
        boolean heartbeatOk = true;
        boolean blockPrepare;
        RuntimeException prepareFailure;
        final CountDownLatch prepareEntered = new CountDownLatch(1);
        final CountDownLatch releasePrepare = new CountDownLatch(1);
        final FakeEndpointLease endpoint = new FakeEndpointLease();
        BindCallback callback;

        @Override
        public void bind(long generation, BindCallback callback) {
            bindCalls++;
            this.callback = callback;
            callback.onBound(generation);
        }

        @Override
        public void prepare(long generation, int sampleRateHz) {
            prepareCalls++;
            if (prepareFailure != null) {
                throw prepareFailure;
            }
            if (blockPrepare) {
                prepareEntered.countDown();
                try {
                    if (!releasePrepare.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("prepare release timeout");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("prepare interrupted", error);
                }
            }
        }

        @Override
        public CallMediaEndpointLease start(long generation) {
            startCalls++;
            endpoint.open = true;
            return endpoint;
        }

        @Override
        public boolean heartbeat(long generation) {
            return heartbeatOk;
        }

        @Override
        public void abort(long generation) {
            abortCalls++;
        }

        @Override
        public void unbind(long generation) {
            unbindCalls++;
        }

        void disconnect(long generation) {
            callback.onDisconnected(generation);
        }
    }

    private static final class FakeEndpointLease implements CallMediaEndpointLease {
        boolean open;

        @Override
        public void close() {
            open = false;
        }
    }

    private static final class ManualHeartbeatScheduler implements CallMediaHeartbeatScheduler {
        int startCalls;
        boolean active;
        Runnable heartbeat;

        @Override
        public AutoCloseable start(long generation, Runnable heartbeat) {
            startCalls++;
            active = true;
            this.heartbeat = heartbeat;
            return () -> active = false;
        }

        void fire() {
            if (active && heartbeat != null) {
                heartbeat.run();
            }
        }
    }
}
