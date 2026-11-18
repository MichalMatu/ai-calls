package pl.michalmatu.aicallbridge.session;

import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungCallMediaSessionController;
import pl.michalmatu.aicallbridge.shizuku.PrivilegedCallContexts;

/**
 * Native Binder GSM controller launched explicitly through ADB shell.
 * Only the installed S8 app UID may use it. PCM moves via owned pipes, not
 * Binder byte arrays, TCP, Shizuku or SIP.
 */
public final class GsmShellBinderMediaService extends IGsmShellMediaService.Stub {
    private static final long IDLE = -1L;
    private static final long STOPPING = -2L;

    private final int trustedAppUid;
    private final SamsungCallMediaSessionController controller =
        new SamsungCallMediaSessionController();

    private volatile long generation = IDLE;
    private volatile boolean shuttingDown;
    private PrivilegedCallContexts.Pair contexts;
    private SamsungCallMediaSessionController.Endpoints endpoints;

    public GsmShellBinderMediaService(int trustedAppUid) {
        if (Process.myUid() != GsmShellMediaProtocol.SHELL_UID
            || trustedAppUid < 10_000 || trustedAppUid == Process.myUid()) {
            throw new SecurityException("gsm_shell_invalid_owner");
        }
        this.trustedAppUid = trustedAppUid;
    }

    private void requireOwner() {
        if (shuttingDown || Process.myUid() != GsmShellMediaProtocol.SHELL_UID
            || Binder.getCallingUid() != trustedAppUid) {
            throw new SecurityException("gsm_shell_owner_uid_required");
        }
    }

    @Override public int getShellUid() {
        requireOwner();
        return Process.myUid();
    }

    @Override public void prepare(long requested, int sampleRate) {
        requireOwner();
        if (!GsmShellMediaProtocol.validGeneration(requested)
            || sampleRate != GsmShellMediaProtocol.SAMPLE_RATE_HZ) {
            throw new IllegalArgumentException("gsm_shell_invalid_prepare");
        }
        synchronized (this) {
            if (generation != IDLE || contexts != null || endpoints != null) {
                throw new IllegalStateException("gsm_shell_media_already_owned");
            }
            generation = requested;
        }
        try {
            PrivilegedCallContexts.Pair candidate = PrivilegedCallContexts.create();
            controller.prepare(sampleRate, candidate.shell());
            synchronized (this) {
                if (generation != requested || shuttingDown) {
                    throw new IllegalStateException("gsm_shell_prepare_revoked");
                }
                contexts = candidate;
            }
        } catch (Throwable error) {
            stopGeneration(requested);
            throw new IllegalStateException("gsm_shell_prepare_failed", error);
        }
    }

    @Override public void startMedia(long requested) {
        requireOwner();
        PrivilegedCallContexts.Pair selected;
        synchronized (this) {
            if (generation != requested || contexts == null || endpoints != null) {
                throw new IllegalStateException("gsm_shell_start_without_prepare");
            }
            selected = contexts;
            contexts = null;
        }
        SamsungCallMediaSessionController.Endpoints result = null;
        try {
            result = controller.start(selected.system(), selected.shell());
            synchronized (this) {
                if (generation != requested || shuttingDown) {
                    throw new IllegalStateException("gsm_shell_start_revoked");
                }
                endpoints = result;
                result = null;
            }
        } catch (Throwable error) {
            if (result != null) result.close();
            stopGeneration(requested);
            throw new IllegalStateException("gsm_shell_start_failed", error);
        }
    }

    @Override public synchronized ParcelFileDescriptor takeDownlinkReadEnd(long requested) {
        requireOwner();
        if (generation != requested || endpoints == null)
            throw new IllegalStateException("gsm_shell_downlink_unavailable");
        return endpoints.takeDownlinkReadEnd();
    }

    @Override public synchronized ParcelFileDescriptor takeUplinkWriteEnd(long requested) {
        requireOwner();
        if (generation != requested || endpoints == null)
            throw new IllegalStateException("gsm_shell_uplink_unavailable");
        return endpoints.takeUplinkWriteEnd();
    }

    @Override public boolean heartbeat(long requested) {
        requireOwner();
        if (generation != requested) return false;
        boolean alive = controller.heartbeat();
        if (!alive) stopGeneration(requested);
        return alive && generation == requested;
    }

    @Override public void abortNow(long requested) {
        requireOwner();
        stopGeneration(requested);
    }

    private void stopGeneration(long requested) {
        SamsungCallMediaSessionController.Endpoints toClose;
        synchronized (this) {
            if (generation != requested) return;
            generation = STOPPING;
            toClose = endpoints;
            endpoints = null;
            contexts = null;
        }
        try {
            if (toClose != null) toClose.close();
            controller.abortNow();
        } finally {
            synchronized (this) {
                if (generation == STOPPING) generation = IDLE;
            }
        }
    }

    /** Local shell-host teardown; no app RPC can call this method. */
    public void shutdown() {
        shuttingDown = true;
        SamsungCallMediaSessionController.Endpoints toClose;
        synchronized (this) {
            generation = STOPPING;
            toClose = endpoints;
            endpoints = null;
            contexts = null;
        }
        if (toClose != null) toClose.close();
        controller.abortNow();
    }
}
