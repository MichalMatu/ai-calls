package pl.michalmatu.aicallbridge;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.AudioManager;
import android.os.Looper;
import android.os.Process;

import java.lang.reflect.Method;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungCallMediaSessionController;

/** Bounded off-call regression harness for the bidirectional helper media controller. */
public final class BidirectionalMediaProbe {
    private static final int SAMPLE_RATE = 16_000;

    private BidirectionalMediaProbe() {}

    public static void main(String[] args) {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        int exitCode;
        try {
            if (args.length == 1 && "prepare-abort-offcall".equals(args[0])) {
                exitCode = runPrepareAbortOffcall();
            } else if (args.length == 1 && "start-offcall".equals(args[0])) {
                exitCode = runStartOffcall();
            } else {
                System.out.println(
                    "usage=BidirectionalMediaProbe prepare-abort-offcall | start-offcall"
                );
                exitCode = 2;
            }
        } catch (Throwable error) {
            printError("bidirectional_media_probe", error);
            exitCode = 3;
        }

        System.out.flush();
        System.err.flush();
        System.exit(exitCode);
    }

    private static int runPrepareAbortOffcall() {
        SamsungCallMediaSessionController controller = new SamsungCallMediaSessionController();
        try {
            System.out.println("probe=bidirectional-media-v1");
            System.out.println("mode=prepare-abort-offcall");
            System.out.println("uid=" + Process.myUid());
            System.out.println("sample_rate=" + SAMPLE_RATE);

            controller.prepare(SAMPLE_RATE);
            System.out.println("prepared_after_prepare=" + controller.hasPreparedSession());
            System.out.println("active_after_prepare=" + controller.hasActiveSession());
            System.out.println("heartbeat_after_prepare=" + controller.heartbeat());

            controller.abortNow();
            System.out.println("prepared_after_abort=" + controller.hasPreparedSession());
            System.out.println("active_after_abort=" + controller.hasActiveSession());
            System.out.println("heartbeat_after_abort=" + controller.heartbeat());

            return !controller.hasPreparedSession() && !controller.hasActiveSession() ? 0 : 4;
        } finally {
            controller.abortNow();
        }
    }

    private static int runStartOffcall() throws Exception {
        SamsungCallMediaSessionController controller = new SamsungCallMediaSessionController();
        SamsungCallMediaSessionController.Endpoints endpoints = null;
        try {
            System.out.println("probe=bidirectional-media-v1");
            System.out.println("mode=start-offcall");
            System.out.println("uid=" + Process.myUid());
            System.out.println("sample_rate=" + SAMPLE_RATE);

            // Keep the proven Samsung ordering: create VOICE_DOWNLINK before either Context.
            controller.prepare(SAMPLE_RATE);
            System.out.println("prepared_before_contexts=" + controller.hasPreparedSession());

            ContextPair contexts = contexts();
            AudioManager downlinkAudioManager =
                (AudioManager) contexts.system.getSystemService(Context.AUDIO_SERVICE);
            AudioManager uplinkAudioManager =
                (AudioManager) contexts.shell.getSystemService(Context.AUDIO_SERVICE);
            System.out.println("downlink_context_package=" + contexts.system.getPackageName());
            System.out.println("downlink_context_attribution="
                + contexts.system.getAttributionSource().getPackageName());
            System.out.println("uplink_context_package=" + contexts.shell.getPackageName());
            System.out.println("uplink_context_attribution="
                + contexts.shell.getAttributionSource().getPackageName());
            System.out.println("downlink_audio_mode=" + downlinkAudioManager.getMode());
            System.out.println("uplink_audio_mode=" + uplinkAudioManager.getMode());

            boolean rejected = false;
            try {
                endpoints = controller.start(contexts.system, contexts.shell);
            } catch (IllegalStateException expected) {
                rejected = true;
                System.out.println("offcall_start_rejected=true");
                System.out.println("offcall_start_error=" + sanitize(expected.getMessage()));
            }
            if (!rejected) {
                System.out.println("offcall_start_rejected=false");
                return 5;
            }

            System.out.println("prepared_after_rollback=" + controller.hasPreparedSession());
            System.out.println("active_after_rollback=" + controller.hasActiveSession());
            System.out.println("heartbeat_after_rollback=" + controller.heartbeat());

            // A failed transactional start must leave the controller reusable.
            controller.prepare(SAMPLE_RATE);
            System.out.println("reprepare_after_rollback=" + controller.hasPreparedSession());
            controller.abortNow();
            System.out.println("prepared_after_final_abort=" + controller.hasPreparedSession());
            System.out.println("active_after_final_abort=" + controller.hasActiveSession());

            return !controller.hasPreparedSession() && !controller.hasActiveSession() ? 0 : 6;
        } finally {
            if (endpoints != null) {
                endpoints.close();
            }
            controller.abortNow();
        }
    }

    private static ContextPair contexts() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThreadClass.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object thread = systemMain.invoke(null);

        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        Context system = (Context) getSystemContext.invoke(thread);

        ApplicationInfo shellInfo = system.getPackageManager()
            .getApplicationInfo("com.android.shell", 0);
        Method getPackageInfoNoCheck = activityThreadClass.getDeclaredMethod(
            "getPackageInfoNoCheck", ApplicationInfo.class
        );
        getPackageInfoNoCheck.setAccessible(true);
        Object loadedApk = getPackageInfoNoCheck.invoke(thread, shellInfo);

        Class<?> loadedApkClass = Class.forName("android.app.LoadedApk");
        Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
        Method createAppContext = contextImplClass.getDeclaredMethod(
            "createAppContext", activityThreadClass, loadedApkClass, String.class
        );
        createAppContext.setAccessible(true);
        Context shell = (Context) createAppContext.invoke(null, thread, loadedApk, "com.android.shell");
        return new ContextPair(system, shell);
    }

    private static void printError(String label, Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        System.out.println(
            label + "=error:" + root.getClass().getSimpleName() + ":" + sanitize(root.getMessage())
        );
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static final class ContextPair {
        final Context system;
        final Context shell;

        ContextPair(Context system, Context shell) {
            this.system = system;
            this.shell = shell;
        }
    }
}
