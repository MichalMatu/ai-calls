package pl.michalmatu.aicallbridge.shizuku;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Builds the two attribution contexts required by the proven Samsung cellular media path.
 *
 * <p>The direct-shell proof still constructs VOICE_DOWNLINK before creating these contexts. A
 * Shizuku UserService is different: Shizuku has already created an app-attributed process before
 * our service object exists, so its RX path must explicitly supply trusted shell attribution to
 * AudioRecord during prepare. The same pair is then reused for route guarding and CALL_ASSISTANT
 * TX.</p>
 */
public final class PrivilegedCallContexts {
    private PrivilegedCallContexts() {}

    public static Pair create() throws Exception {
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
