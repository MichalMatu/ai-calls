package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import android.os.Looper;
import android.os.Process;

import java.lang.reflect.Method;


/**
 * Explicit ADB shell entry point for the native S8 Binder transport.
 * It registers one UID-fenced media service with the S8 debug app through
 * Android's standard external ContentProvider Binder path, not Shizuku.
 * Neither the host nor registration initiates cellular calls or PCM.
 */
public final class GsmShellBinderMediaHost {
    private GsmShellBinderMediaHost() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0 || Process.myUid() != GsmShellMediaProtocol.SHELL_UID)
            throw new SecurityException("gsm_shell_requires_adb_owner");
        if (Looper.myLooper() == null) Looper.prepare();

        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThread.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        systemMain.invoke(null);

        Context system = ShellCallContexts.create().system();
        int appUid = system.getPackageManager().getPackageUid(
            GsmShellMediaProtocol.S8_PACKAGE, 0
        );
        if (!GsmShellMediaProtocol.mayServeApp(appUid, appUid))
            throw new SecurityException("gsm_shell_invalid_app_uid");

        GsmShellBinderMediaService media = new GsmShellBinderMediaService(appUid);
        try (GsmShellExternalProviderClient provider =
                 GsmShellExternalProviderClient.acquire()) {
            if (!provider.register(media.asBinder()))
                throw new IllegalStateException("gsm_shell_binder_registration_failed");

            // This is a foreground, explicitly bootstrapped shell process;
            // no daemon, scheduler, network listener, SIP or Shizuku service.
            // The controller watchdog independently revokes PCM without
            // heartbeat even if the app or host becomes unresponsive.
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1_000L);
                if (!provider.stillRegistered()) {
                    throw new IllegalStateException("gsm_shell_registration_lost");
                }
                int currentUid = system.getPackageManager().getPackageUid(
                    GsmShellMediaProtocol.S8_PACKAGE, 0
                );
                if (currentUid != appUid) {
                    throw new SecurityException("gsm_shell_app_identity_changed");
                }
            }
        } finally {
            media.shutdown();
        }
    }
}
