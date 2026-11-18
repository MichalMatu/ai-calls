package pl.michalmatu.aicallbridge.session;

import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungCallMediaSessionController;
import pl.michalmatu.aicallbridge.shizuku.PrivilegedCallContexts;

/**
 * Privileged GSM media controller bound via the platform's Binder primitives.
 * Only the installed S8 app UID can call this ADB-launched shell instance.
 * No Shizuku runtime, SIP, dialer changes, or arbitrary command execution.
 */
public final class GsmShellBinderMediaService extends IGsmShellMediaService.Stub {
    private final int trustedAppUid;
    private final SamsungCallMediaSessionController controller =
        new SamsungCallMediaSessionController();
    private volatile boolean revoked;
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
        if (revoked || Process.myUid() != GsmShellMediaProtocol.SHELL_UID
            || Binder.getCallingUid() != trustedAppUid) {
            throw new SecurityException("gsm_shell_owner_uid_required");
        }
    }

    @Override public int getShellUid() {
        requireOwner();
        return Process.myUid();
    }

    @Override public synchronized void prepare(int sampleRate) {
        requireOwner();
        if (contexts != null || endpoints != null || controller.hasPreparedSession()) {
            throw new IllegalStateException("gsm_shell_session_already_prepared");
        }
        if (sampleRate != GsmShellMediaProtocol.SAMPLE_RATE_HZ) {
            throw new IllegalArgumentException("gsm_shell_sample_rate_unsupported");
        }
        try {
            PrivilegedCallContexts.Pair candidate = PrivilegedCallContexts.create();
            controller.prepare(sampleRate, candidate.shell());
            contexts = candidate;
        } catch (Throwable error) {
            controller.abortNow();
            contexts = null;
            throw new IllegalStateException("gsm_shell_media_prepare_failed", error);
        }
    }

    @Override public synchronized void startMedia() {
        requireOwner();
        if (contexts == null || endpoints != null) {
            throw new IllegalStateException("gsm_shell_start_without_prepare");
        }
        try {
            endpoints = controller.start(contexts.system(), contexts.shell());
            contexts = null;
        } catch (Throwable error) {
            controller.abortNow();
            contexts = null;
            throw new IllegalStateException("gsm_shell_media_start_failed", error);
        }
    }

    @Override public synchronized ParcelFileDescriptor takeDownlinkReadEnd() {
        requireOwner();
        if (endpoints == null) throw new IllegalStateException("gsm_shell_no_downlink");
        return endpoints.takeDownlinkReadEnd();
    }

    @Override public synchronized ParcelFileDescriptor takeUplinkWriteEnd() {
        requireOwner();
        if (endpoints == null) throw new IllegalStateException("gsm_shell_no_uplink");
        return endpoints.takeUplinkWriteEnd();
    }

    @Override public boolean heartbeat() {
        requireOwner();
        return controller.heartbeat();
    }

    @Override public void abortNow() {
        // Revocation is visible immediately, even while a media operation is
        // transitioning. Closing endpoints and the controller kills both PCM
        // directions, including after an app-side Binder transport failure.
        revoked = true;
        SamsungCallMediaSessionController.Endpoints toClose;
        synchronized (this) {
            toClose = endpoints;
            endpoints = null;
            contexts = null;
        }
        if (toClose != null) toClose.close();
        controller.abortNow();
    }

    /** Shell process shutdown path; cannot be called via app Binder. */
    public void shutdown() {
        abortNow();
    }
}
