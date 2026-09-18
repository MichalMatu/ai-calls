package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Test;

public final class CallMediaEndpointLeaseContractTest {
    @Test
    public void endpointLeaseExposesBidirectionalStreamSurfaces() {
        FakeEndpointLease lease = new FakeEndpointLease();
        CallMediaEndpointLease contract = lease;

        assertSame(lease.downlink, contract.downlink());
        assertSame(lease.uplink, contract.uplink());
    }

    @Test
    public void coordinatorExposesOnlyTheCurrentActiveGenerationLease() {
        FakeBackend backend = new FakeBackend();
        CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
            backend,
            (generation, heartbeat) -> () -> {},
            () -> 1L,
            ignored -> {}
        );

        long generation = coordinator.start(16_000);

        assertSame(backend.endpoint, coordinator.activeEndpointLease(generation));
        assertThrows(
            IllegalStateException.class,
            () -> coordinator.activeEndpointLease(generation + 1)
        );

        coordinator.takeOverNow();
        assertThrows(
            IllegalStateException.class,
            () -> coordinator.activeEndpointLease(generation)
        );
    }

    private static final class FakeBackend implements CallMediaSessionBackend {
        final FakeEndpointLease endpoint = new FakeEndpointLease();

        @Override
        public void bind(long generation, BindCallback callback) {
            callback.onBound(generation);
        }

        @Override
        public void prepare(long generation, int sampleRateHz) {}

        @Override
        public CallMediaEndpointLease start(long generation) {
            return endpoint;
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

    private static final class FakeEndpointLease implements CallMediaEndpointLease {
        final InputStream downlink = new ByteArrayInputStream(new byte[0]);
        final OutputStream uplink = new ByteArrayOutputStream();

        @Override
        public InputStream downlink() {
            return downlink;
        }

        @Override
        public OutputStream uplink() {
            return uplink;
        }

        @Override
        public void close() {}
    }
}
