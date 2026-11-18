package pl.michalmatu.aicallbridge.session;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * S8-only app-side GSM transport over Android Binder and two PCM pipes.
 * The shell process is launched independently by the owner via ADB; this
 * backend never acquires shell identity or asks Shizuku for privileges.
 *
 * Bind is async. Local abort detaches immediately and sends a one-way STOP
 * without waiting for a blocked preparation, inference or remote heartbeat.
 */
public final class GsmShellBinderCallMediaSessionBackend implements CallMediaSessionBackend {
    private final Object lock = new Object();
    private final ExecutorService connector = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "aicalls-s8-binder-connect");
        thread.setDaemon(true);
        return thread;
    });

    private long expectedGeneration = -1;
    private Remote connected;
    private boolean closed;

    @Override public void bind(long generation, BindCallback callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (lock) {
            if (closed || expectedGeneration != -1 || !GsmShellMediaProtocol.validGeneration(generation)) {
                callback.onBindFailed(generation, new IllegalStateException("gsm_binder_unavailable"));
                return;
            }
            expectedGeneration = generation;
        }
        try {
            connector.execute(() -> connect(generation, callback));
        } catch (RuntimeException failure) {
            abort(generation);
            callback.onBindFailed(generation, failure);
        }
    }

    private void connect(long generation, BindCallback callback) {
        Remote candidate = null;
        boolean retained = false;
        try {
            IGsmShellMediaService service = GsmShellBinderRegistryProvider.availableService();
            if (service == null || service.getShellUid() != GsmShellMediaProtocol.SHELL_UID)
                throw new SecurityException("gsm_binder_host_not_verified");
            IBinder binder = service.asBinder();
            if (!binder.isBinderAlive())
                throw new IllegalStateException("gsm_binder_host_died");
            IBinder.DeathRecipient death = () -> disconnected(generation, callback);
            binder.linkToDeath(death, 0);
            candidate = new Remote(generation, service, binder, death);
            synchronized (lock) {
                if (!closed && expectedGeneration == generation && connected == null) {
                    connected = candidate;
                    retained = true;
                }
            }
            if (retained) callback.onBound(generation);
        } catch (Throwable failure) {
            synchronized (lock) {
                if (expectedGeneration == generation) expectedGeneration = -1;
            }
            callback.onBindFailed(generation, failure);
        } finally {
            if (!retained && candidate != null) candidate.detachDeath();
        }
    }

    private void disconnected(long generation, BindCallback callback) {
        Remote removed;
        synchronized (lock) {
            if (expectedGeneration != generation || connected == null) return;
            removed = connected;
            connected = null;
            expectedGeneration = -1;
        }
        removed.detachDeath();
        callback.onDisconnected(generation);
    }

    @Override public void prepare(long generation, int sampleRateHz) {
        if (sampleRateHz != GsmShellMediaProtocol.SAMPLE_RATE_HZ)
            throw new IllegalArgumentException("gsm_binder_sample_rate_unsupported");
        try {
            current(generation).service.prepare(generation, sampleRateHz);
        } catch (RemoteException error) {
            throw new IllegalStateException("gsm_binder_prepare_failed", error);
        }
    }

    @Override public CallMediaEndpointLease start(long generation) {
        IGsmShellMediaService service = current(generation).service;
        ParcelFileDescriptor downlink = null;
        ParcelFileDescriptor uplink = null;
        try {
            service.startMedia(generation);
            downlink = service.takeDownlinkReadEnd(generation);
            uplink = service.takeUplinkWriteEnd(generation);
            if (downlink == null || uplink == null)
                throw new IllegalStateException("gsm_binder_endpoints_missing");
            CallMediaEndpointLease lease = new ShizukuCallMediaEndpointLease(downlink, uplink);
            downlink = null;
            uplink = null;
            return lease;
        } catch (RemoteException error) {
            throw new IllegalStateException("gsm_binder_start_failed", error);
        } finally {
            closeQuietly(downlink);
            closeQuietly(uplink);
        }
    }

    @Override public boolean heartbeat(long generation) {
        try {
            return current(generation).service.heartbeat(generation);
        } catch (RemoteException | IllegalStateException failure) {
            return false;
        }
    }

    @Override public void abort(long generation) {
        Remote toAbort = detach(generation);
        if (toAbort == null) return;
        toAbort.detachDeath();
        // One-way Binder IPC: STOP cannot wait for a blocking media operation.
        try {
            toAbort.service.abortNow(generation);
        } catch (RemoteException | RuntimeException ignored) {
            // The local PCM lease is closed by the coordinator; the independent
            // Samsung watchdog tears down remote PCM after missed heartbeat.
        }
    }

    @Override public void unbind(long generation) { abort(generation); }

    @Override public void close() {
        Remote active;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            active = connected;
            connected = null;
            expectedGeneration = -1;
        }
        if (active != null) {
            active.detachDeath();
            try { active.service.abortNow(active.generation); }
            catch (RemoteException | RuntimeException ignored) {}
        }
        connector.shutdownNow();
    }

    private Remote current(long generation) {
        synchronized (lock) {
            if (closed || generation != expectedGeneration || connected == null)
                throw new IllegalStateException("gsm_binder_generation_not_bound");
            return connected;
        }
    }

    private Remote detach(long generation) {
        synchronized (lock) {
            if (expectedGeneration != generation) return null;
            expectedGeneration = -1;
            Remote result = connected;
            connected = null;
            return result;
        }
    }

    private static void closeQuietly(AutoCloseable item) {
        if (item != null) try { item.close(); } catch (Exception ignored) {}
    }

    private static final class Remote {
        final long generation;
        final IGsmShellMediaService service;
        final IBinder binder;
        final IBinder.DeathRecipient death;

        Remote(long generation, IGsmShellMediaService service, IBinder binder,
               IBinder.DeathRecipient death) {
            this.generation = generation;
            this.service = service;
            this.binder = binder;
            this.death = death;
        }

        void detachDeath() {
            try { binder.unlinkToDeath(death, 0); } catch (RuntimeException ignored) {}
        }
    }
}
