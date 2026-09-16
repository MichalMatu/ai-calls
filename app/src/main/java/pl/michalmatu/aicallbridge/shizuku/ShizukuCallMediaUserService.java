package pl.michalmatu.aicallbridge.shizuku;

import android.os.ParcelFileDescriptor;
import android.os.Process;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungCallMediaSessionController;

/**
 * Shizuku UserService owner for the proven Samsung bidirectional call media controller.
 *
 * <p>This class intentionally exposes only control RPCs through Binder. Continuous PCM remains on
 * the two ParcelFileDescriptor pipes returned by {@link #takeDownlinkReadEnd()} and
 * {@link #takeUplinkWriteEnd()}.</p>
 *
 * <p>Shizuku starts this UserService under shell UID 2000 but from our APK, so the process package
 * attribution is still {@code pl.michalmatu.aicallbridge}. The RX prepare path therefore creates
 * the trusted system/shell Context pair first and passes the shell Context explicitly to
 * AudioRecord construction. The frozen direct-shell path remains context-free.</p>
 */
public final class ShizukuCallMediaUserService extends IShizukuCallMediaService.Stub {
    private final SamsungCallMediaSessionController controller =
        new SamsungCallMediaSessionController();

    private SamsungCallMediaSessionController.Endpoints endpoints;
    private PrivilegedCallContexts.Pair contexts;

    public ShizukuCallMediaUserService() {
        // Required default constructor for Shizuku UserService.
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
        if (contexts != null || controller.hasPreparedSession()) {
            throw new IllegalStateException("media session is already prepared");
        }

        try {
            PrivilegedCallContexts.Pair candidate = PrivilegedCallContexts.create();
            controller.prepare(sampleRate, candidate.shell());
            contexts = candidate;
        } catch (RuntimeException | Error error) {
            controller.abortNow();
            contexts = null;
            throw error;
        } catch (Exception error) {
            controller.abortNow();
            contexts = null;
            throw new IllegalStateException(
                "failed to prepare Samsung call media: " + describe(error),
                error
            );
        }
    }

    @Override
    public synchronized void startMedia() {
        if (endpoints != null) {
            throw new IllegalStateException("media endpoints already created");
        }
        if (contexts == null) {
            throw new IllegalStateException("prepare(sampleRate) must succeed before startMedia()");
        }

        try {
            endpoints = controller.start(contexts.system(), contexts.shell());
            contexts = null;
        } catch (RuntimeException | Error error) {
            controller.abortNow();
            contexts = null;
            throw error;
        } catch (Exception error) {
            controller.abortNow();
            contexts = null;
            throw new IllegalStateException(
                "failed to start Samsung call media: " + describe(error),
                error
            );
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
        contexts = null;
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

    private static String describe(Throwable error) {
        StringBuilder result = new StringBuilder();
        Throwable current = error;
        for (int depth = 0; current != null && depth < 6; depth++) {
            if (depth > 0) {
                result.append(" -> ");
            }
            result.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                result.append(':')
                    .append(message.replace('\n', ' ').replace('\r', ' '));
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return result.toString();
    }
}
