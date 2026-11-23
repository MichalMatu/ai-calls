package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.AudioManager;
import android.os.DeadObjectException;
import android.os.Looper;
import android.os.Process;
import android.telecom.TelecomManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * Explicit ADB shell entry point for the native S8 Binder transport.
 * It registers one UID-fenced media service with the S8 debug app through
 * Android's standard external ContentProvider Binder path, not Shizuku.
 * Neither the host nor registration initiates cellular calls or PCM.
 */
public final class GsmShellBinderMediaHost {
    private static final int RECOVERY_CHECKS = 60;
    private static final long RECOVERY_DELAY_MS = 500;

    private GsmShellBinderMediaHost() {}

    public static void main(String[] args) throws Exception {
        if (Process.myUid() != GsmShellMediaProtocol.SHELL_UID)
            throw new SecurityException("gsm_shell_requires_adb_owner");
        // The lab retains its original no-argument entrypoint; release is explicit.
        String packageName;
        if (args.length == 0) packageName = GsmShellMediaProtocol.S8_PACKAGE;
        else if (args.length == 1 && GsmShellMediaProtocol.APP_PACKAGE.equals(args[0]))
            packageName = GsmShellMediaProtocol.APP_PACKAGE;
        else throw new SecurityException("gsm_shell_untrusted_app_target");
        if (Looper.myLooper() == null) Looper.prepare();

        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThread.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        systemMain.invoke(null);

        ShellCallContexts.Pair contexts = ShellCallContexts.create();
        Context system = contexts.system();
        ApplicationInfo initial = installedApp(system, packageName);
        if (!GsmShellMediaProtocol.mayServeApp(initial.uid, initial.uid))
            throw new SecurityException("gsm_shell_invalid_app_uid");

        boolean recovering = false;
        while (!Thread.currentThread().isInterrupted()) {
            requireSameApp(system, initial, packageName);
            GsmShellBinderMediaService media = new GsmShellBinderMediaService(initial.uid);
            try (GsmShellExternalProviderClient provider = GsmShellExternalProviderClient.acquire(packageName)) {
                requireSameApp(system, initial, packageName);
                if (recovering && !phoneIdle(contexts.shell())) return;
                if (!provider.register(media.asBinder()))
                    throw new IllegalStateException("gsm_shell_binder_registration_failed");

                // The controller watchdog independently revokes unattended PCM.
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1_000L);
                    requireSameApp(system, initial, packageName);
                    if (!registrationAlive(provider)) break;
                }
            } finally {
                media.shutdown();
            }

            recovering = true;
            boolean ready = false;
            for (int check = 0; check < RECOVERY_CHECKS; check++) {
                if (Thread.currentThread().isInterrupted()) return;
                requireSameApp(system, initial, packageName);
                if (media.isQuiescent() && phoneIdle(contexts.shell())) {
                    ready = true;
                    break;
                }
                Thread.sleep(RECOVERY_DELAY_MS);
            }
            if (!ready) return;
        }
    }

    private static ApplicationInfo installedApp(Context system, String packageName) throws Exception {
        return system.getPackageManager().getApplicationInfo(packageName, 0);
    }

    private static void requireSameApp(Context system, ApplicationInfo initial,
                                       String packageName) throws Exception {
        ApplicationInfo current = installedApp(system, packageName);
        if (current.uid != initial.uid || !initial.sourceDir.equals(current.sourceDir))
            throw new SecurityException("gsm_shell_app_identity_changed");
    }

    private static boolean registrationAlive(GsmShellExternalProviderClient provider) throws Exception {
        try {
            return provider.stillRegistered();
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof DeadObjectException) return false;
            throw failure;
        }
    }

    private static boolean phoneIdle(Context shell) {
        try {
            TelecomManager telecom = shell.getSystemService(TelecomManager.class);
            AudioManager audio = shell.getSystemService(AudioManager.class);
            return telecom != null && !telecom.isInCall() && audio != null
                && audio.getMode() == AudioManager.MODE_NORMAL;
        } catch (SecurityException failure) {
            return false;
        }
    }
}
