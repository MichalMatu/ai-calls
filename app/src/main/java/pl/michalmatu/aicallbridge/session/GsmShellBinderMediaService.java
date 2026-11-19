package pl.michalmatu.aicallbridge.session;

import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungCallMediaSessionController;

/**
 * UID-checked S8 GSM Binder controller. One generation at a time owns the
 * existing Samsung PCM downlink and uplink; no Shizuku, SIP or TCP.
 *
 * STOP fences new work immediately. If prepare/start is still running, the
 * STOPPING state remains until that operation exits and its resources are
 * explicitly aborted. A stale completion cannot re-open a newer call.
 */
public final class GsmShellBinderMediaService extends IGsmShellMediaService.Stub {
    private static final long IDLE = -1L;
    private static final long STOPPING = -2L;

    private final int trustedAppUid;
    private final SamsungCallMediaSessionController controller =
        new SamsungCallMediaSessionController();

    private volatile long generation = IDLE;
    private volatile boolean shuttingDown;
    private int operationsInFlight;
    private long revokedThrough;
    private boolean cleanupInProgress;
    private ShellCallContexts.Pair contexts;
    private SamsungCallMediaSessionController.Endpoints endpoints;

    public GsmShellBinderMediaService(int trustedAppUid) {
        if (Process.myUid() != GsmShellMediaProtocol.SHELL_UID
            || trustedAppUid < 10_000 || trustedAppUid == Process.myUid())
            throw new SecurityException("gsm_shell_invalid_owner");
        this.trustedAppUid = trustedAppUid;
    }

    private void requireOwner() {
        if (shuttingDown || Process.myUid() != GsmShellMediaProtocol.SHELL_UID
            || Binder.getCallingUid() != trustedAppUid)
            throw new SecurityException("gsm_shell_owner_uid_required");
    }

    @Override public int getShellUid() {
        requireOwner();
        return Process.myUid();
    }

    @Override public void prepare(long requested, int sampleRate) {
        requireOwner();
        if (!GsmShellMediaProtocol.validGeneration(requested)
            || sampleRate != GsmShellMediaProtocol.SAMPLE_RATE_HZ)
            throw new IllegalArgumentException("gsm_shell_invalid_prepare");

        synchronized (this) {
            if (!GsmBinderCancellationPolicy.mayPrepare(requested, revokedThrough)
                || generation != IDLE
                || operationsInFlight != 0 || cleanupInProgress
                || contexts != null || endpoints != null || shuttingDown)
                throw new IllegalStateException("gsm_shell_already_owned");
            generation = requested;
            operationsInFlight++;
        }
        try {
            ShellCallContexts.Pair candidate = ShellCallContexts.create();
            controller.prepare(sampleRate, candidate.shell());
            synchronized (this) {
                if (generation != requested || shuttingDown)
                    throw new IllegalStateException("gsm_shell_prepare_revoked");
                contexts = candidate;
            }
        } catch (Throwable error) {
            stopGeneration(requested);
            // If STOP ran while prepare was blocked, cleanup can precede the
            // late prepare completion. Until this operation exits no new
            // generation is admitted, so aborting again is safe.
            controller.abortNow();
            throw new IllegalStateException("gsm_shell_prepare_failed", error);
        } finally {
            finishOperation();
        }
    }

    @Override public void startMedia(long requested) {
        requireOwner();
        ShellCallContexts.Pair selected;
        synchronized (this) {
            if (requested <= revokedThrough || generation != requested
                || contexts == null || endpoints != null
                || operationsInFlight != 0 || shuttingDown)
                throw new IllegalStateException("gsm_shell_start_without_prepare");
            selected = contexts;
            contexts = null;
            operationsInFlight++;
        }
        SamsungCallMediaSessionController.Endpoints result = null;
        try {
            result = controller.start(selected.system(), selected.shell());
            synchronized (this) {
                if (generation != requested || shuttingDown)
                    throw new IllegalStateException("gsm_shell_start_revoked");
                endpoints = result;
                result = null;
            }
        } catch (Throwable error) {
            if (result != null) result.close();
            stopGeneration(requested);
            controller.abortNow();
            throw new IllegalStateException("gsm_shell_start_failed", error);
        } finally {
            finishOperation();
        }
    }

    @Override public synchronized ParcelFileDescriptor takeDownlinkReadEnd(long requested) {
        requireOwner();
        if (requested <= revokedThrough || generation != requested
            || endpoints == null || cleanupInProgress)
            throw new IllegalStateException("gsm_shell_downlink_unavailable");
        return endpoints.takeDownlinkReadEnd();
    }

    @Override public synchronized ParcelFileDescriptor takeUplinkWriteEnd(long requested) {
        requireOwner();
        if (generation != requested || endpoints == null || cleanupInProgress)
            throw new IllegalStateException("gsm_shell_uplink_unavailable");
        return endpoints.takeUplinkWriteEnd();
    }

    @Override public boolean heartbeat(long requested) {
        requireOwner();
        if (requested <= revokedThrough || generation != requested || shuttingDown) return false;
        boolean healthy = controller.heartbeat();
        if (!healthy) stopGeneration(requested);
        return healthy && generation == requested;
    }

    @Override public void abortNow(long requested) {
        requireOwner();
        stopGeneration(requested);
    }

    private void stopGeneration(long requested) {
        SamsungCallMediaSessionController.Endpoints toClose;
        synchronized (this) {
            // STOP can race ahead of PREPARE across Binder threads. Remember
            // this wire token so a late PREPARE cannot re-open PCM afterward.
            revokedThrough = GsmBinderCancellationPolicy.afterStop(revokedThrough, requested);
            if (generation != requested) return;
            generation = STOPPING;
            cleanupInProgress = true;
            toClose = endpoints;
            endpoints = null;
            contexts = null;
        }
        try {
            if (toClose != null) toClose.close();
            controller.abortNow();
        } finally {
            synchronized (this) {
                cleanupInProgress = false;
                maybeFinishStopLocked();
            }
        }
    }

    private synchronized void finishOperation() {
        if (operationsInFlight <= 0)
            throw new IllegalStateException("gsm_shell_unbalanced_operation");
        operationsInFlight--;
        maybeFinishStopLocked();
    }

    private void maybeFinishStopLocked() {
        if (generation == STOPPING && operationsInFlight == 0
            && !cleanupInProgress && !shuttingDown)
            generation = IDLE;
    }

    /** Called by the shell process itself, never through the Binder interface. */
    public void shutdown() {
        shuttingDown = true;
        SamsungCallMediaSessionController.Endpoints toClose;
        synchronized (this) {
            generation = STOPPING;
            cleanupInProgress = true;
            toClose = endpoints;
            endpoints = null;
            contexts = null;
        }
        try {
            if (toClose != null) toClose.close();
            controller.abortNow();
        } finally {
            synchronized (this) {
                cleanupInProgress = false;
            }
        }
    }
}
