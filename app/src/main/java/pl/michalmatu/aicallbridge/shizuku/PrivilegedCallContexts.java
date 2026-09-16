package pl.michalmatu.aicallbridge.shizuku;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Builds the two attribution contexts required by the proven Samsung cellular media path.
 *
 * <p>This helper must only be invoked after VOICE_DOWNLINK has already been constructed through
 * {@code SamsungCallMediaSessionController.prepare()}. The target S22 firmware is sensitive to
 * that initialization order, so this class deliberately has no static Context initialization.</p>
 */
public final class PrivilegedCallContexts {
    private PrivilegedCallContexts() {}

    public static Pair createAfterMediaPrepare() throws Exception {
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
        Context shell = (Context) createAppContext.invoke(
            null,
            thread,
            loadedApk,
            "com.android.shell"
        );

        return new Pair(system, shell);
    }

    public static final class Pair {
        private final Context system;
        private final Context shell;

        private Pair(Context system, Context shell) {
            this.system = Objects.requireNonNull(system, "system");
            this.shell = Objects.requireNonNull(shell, "shell");
        }

        public Context system() {
            return system;
        }

        public Context shell() {
            return shell;
        }
    }
}
