package pl.michalmatu.aicallbridge.shizuku;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import java.lang.reflect.Method;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungCallMediaSessionController;

/**
 * Shizuku UserService owner for the proven Samsung bidirectional call media controller.
 *
 * <p>This class intentionally exposes only control RPCs through Binder. Continuous PCM remains on
 * the two ParcelFileDescriptor pipes returned by {@link #takeDownlinkReadEnd()} and
 * {@link #takeUplinkWriteEnd()}.</p>
 *
 * <p>There is deliberately no Context constructor. The S22 VOICE_DOWNLINK proof requires
 * {@code SamsungCallMediaSessionController.prepare()} to run before any explicit Context/
 * AudioManager initialization in this process. Contexts are therefore constructed lazily only in
 * {@link #startMedia()} after prepare has succeeded.</p>
 */
public final class ShizukuCallMediaUserService extends IShizukuCallMediaService.Stub {
    private final SamsungCallMediaSessionController controller =
        new SamsungCallMediaSessionController();

    private SamsungCallMediaSessionController.Endpoints endpoints;

    public ShizukuCallMediaUserService() {
        // Required default constructor for Shizuku UserService. Keep context-free.
    }

    @Override
    public int getProcessUid() {
        return Process.myUid();
    }

    @Override
    public int getProcessPid() {
        return Process.myPid();
    }

    @Override
    public synchronized void prepare(int sampleRate) {
        if (endpoints != null) {
            throw new IllegalStateException("media endpoints are already active");
        }
        controller.prepare(sampleRate);
    }

    @Override
    public synchronized void startMedia() {
        if (endpoints != null) {
            throw new IllegalStateException("media endpoints already created");
        }

        try {
            ContextPair contexts = createContextsAfterPrepare();
            endpoints = controller.start(contexts.system, contexts.shell);
        } catch (RuntimeException | Error error) {
            controller.abortNow();
            throw error;
        } catch (Exception error) {
            controller.abortNow();
            throw new IllegalStateException("failed to start Samsung call media", error);
        }
    }

    @Override
    public synchronized ParcelFileDescriptor takeDownlinkReadEnd() {
        ensureEndpoints();
        return endpoints.takeDownlinkReadEnd();
    }

    @Override
    public synchronized ParcelFileDescriptor takeUplinkWriteEnd() {
        ensureEndpoints();
        return endpoints.takeUplinkWriteEnd();
    }

    @Override
    public boolean heartbeat() {
        return controller.heartbeat();
    }

    @Override
    public boolean hasPreparedSession() {
        return controller.hasPreparedSession();
    }

    @Override
    public boolean hasActiveSession() {
        return controller.hasActiveSession();
    }

    @Override
    public synchronized void abortNow() {
        closeEndpoints();
        controller.abortNow();
    }

    @Override
    public void destroy() {
        abortNow();
        System.exit(0);
    }

    private void ensureEndpoints() {
        if (endpoints == null) {
            throw new IllegalStateException("startMedia() must succeed before taking endpoints");
        }
    }

    private void closeEndpoints() {
        if (endpoints != null) {
            endpoints.close();
            endpoints = null;
        }
    }

    private static ContextPair createContextsAfterPrepare() throws Exception {
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

        return new ContextPair(system, shell);
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
