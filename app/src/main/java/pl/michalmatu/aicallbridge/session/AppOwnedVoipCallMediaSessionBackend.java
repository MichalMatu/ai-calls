package pl.michalmatu.aicallbridge.session;

import java.util.Objects;

/**
 * PCM bridge for one app-owned VoIP call. A SIP/RTP engine must supply the MediaPort;
 * this adapter does not create a SIP account or claim access to cellular call audio.
 *
 * The identity is an opaque object, compared by reference rather than by telephone
 * number or equality. MediaPort.open must also refuse to open any other call.
 */
public final class AppOwnedVoipCallMediaSessionBackend implements CallMediaSessionBackend {
    public interface MediaPort {
        /** Exact app-owned call handle; never a cellular Call or a phone number. */
        Object activeIdentity();
        /** Whether media for the exact handle is still usable. */
        boolean mediaHealthy(Object exactIdentity);
        /** Must open media for this handle only, or fail; never silently substitute a call. */
        CallMediaEndpointLease open(Object exactIdentity, long generation, int sampleRateHz);
    }

    private final Object lock = new Object();
    private final MediaPort port;
    private final Object exactIdentity;
    private long boundGeneration = -1L;
    private int preparedRateHz;
    private CallMediaEndpointLease activeLease;
    private boolean closed;

    public AppOwnedVoipCallMediaSessionBackend(MediaPort port, Object exactIdentity) {
        this.port = Objects.requireNonNull(port, "port");
        this.exactIdentity = Objects.requireNonNull(exactIdentity, "exactIdentity");
    }

    @Override
    public void bind(long generation, BindCallback callback) {
        Objects.requireNonNull(callback, "callback");
        final boolean accepted;
        synchronized (lock) {
            accepted = !closed && generation > 0 && boundGeneration == -1L
                && port.activeIdentity() == exactIdentity;
            if (accepted) {
                boundGeneration = generation;
                preparedRateHz = 0;
            }
        }
        if (accepted) callback.onBound(generation);
        else callback.onBindFailed(generation,
            new IllegalStateException("voip_exact_call_unavailable"));
    }

    @Override
    public void prepare(long generation, int sampleRateHz) {
        if (sampleRateHz <= 0) throw new IllegalArgumentException("invalid_sample_rate");
        synchronized (lock) {
            requireCurrent(generation);
            if (activeLease != null) throw new IllegalStateException("voip_media_already_started");
            preparedRateHz = sampleRateHz;
        }
    }

    @Override
    public CallMediaEndpointLease start(long generation) {
        final int sampleRateHz;
        synchronized (lock) {
            requireCurrent(generation);
            if (preparedRateHz <= 0 || activeLease != null)
                throw new IllegalStateException("voip_media_not_prepared");
            sampleRateHz = preparedRateHz;
        }
        // Opening may block. Never hold the STOP lock across the external media port.
        CallMediaEndpointLease candidate =
            Objects.requireNonNull(port.open(exactIdentity, generation, sampleRateHz),
                "voip_media_port_returned_null");
        synchronized (lock) {
            if (!closed && boundGeneration == generation && activeLease == null
                && port.activeIdentity() == exactIdentity) {
                activeLease = candidate;
                return candidate;
            }
        }
        candidate.close();
        throw new IllegalStateException("voip_call_changed_during_open");
    }

    @Override
    public boolean heartbeat(long generation) {
        synchronized (lock) {
            if (closed || boundGeneration != generation || activeLease == null) return false;
        }
        boolean healthy = port.activeIdentity() == exactIdentity
            && port.mediaHealthy(exactIdentity);
        synchronized (lock) {
            return healthy && !closed && boundGeneration == generation && activeLease != null
                && port.activeIdentity() == exactIdentity;
        }
    }

    @Override
    public void abort(long generation) {
        CallMediaEndpointLease lease;
        synchronized (lock) {
            if (boundGeneration != generation) return;
            boundGeneration = -1L;
            preparedRateHz = 0;
            lease = activeLease;
            activeLease = null;
        }
        if (lease != null) lease.close();
    }

    @Override
    public void unbind(long generation) {
        abort(generation);
    }

    @Override
    public void close() {
        CallMediaEndpointLease lease;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            boundGeneration = -1L;
            preparedRateHz = 0;
            lease = activeLease;
            activeLease = null;
        }
        if (lease != null) lease.close();
    }

    private void requireCurrent(long generation) {
        if (closed || boundGeneration != generation || port.activeIdentity() != exactIdentity)
            throw new IllegalStateException("voip_exact_call_unavailable");
    }
}
