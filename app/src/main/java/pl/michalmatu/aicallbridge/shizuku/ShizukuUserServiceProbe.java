package pl.michalmatu.aicallbridge.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/** Bounded off-call parity probe for the Shizuku UserService transport. */
public final class ShizukuUserServiceProbe {
    public interface Callback {
        void onResult(String result);
    }

    private static final int SAMPLE_RATE = 16_000;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

    private final Context appContext;
    private final Callback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs userServiceArgs;

    private final Runnable timeout = () -> finish("probe=shizuku-user-service-v1\nerror=connect_timeout");

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            handler.removeCallbacks(timeout);
            IShizukuCallMediaService service = IShizukuCallMediaService.Stub.asInterface(binder);
            String result;
            try {
                result = runOffCallProbe(service);
            } catch (Throwable error) {
                result = "probe=shizuku-user-service-v1\nerror="
                    + error.getClass().getSimpleName() + ":" + sanitize(error.getMessage());
            } finally {
                try {
                    service.abortNow();
                } catch (Throwable ignored) {
                    // Fail-safe cleanup.
                }
            }
            finish(result);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            finish("probe=shizuku-user-service-v1\nerror=service_disconnected");
        }
    };

    private ShizukuUserServiceProbe(Context context, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.callback = callback;
        this.userServiceArgs = new Shizuku.UserServiceArgs(
            new ComponentName(appContext, ShizukuCallMediaUserService.class)
        )
            .daemon(false)
            .processNameSuffix("call_media")
            .tag("call-media-v1")
            .version(1)
            .debuggable(false);
    }

    public static void run(Context context, Callback callback) {
        new ShizukuUserServiceProbe(context, callback).start();
    }

    private void start() {
        try {
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable error) {
            handler.removeCallbacks(timeout);
            finish("probe=shizuku-user-service-v1\nerror="
                + error.getClass().getSimpleName() + ":" + sanitize(error.getMessage()));
        }
    }

    private String runOffCallProbe(IShizukuCallMediaService service) throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("probe=shizuku-user-service-v1\n");
        result.append("mode=offcall-prepare-abort\n");
        result.append("binder_ping=").append(Shizuku.pingBinder()).append('\n');
        result.append("shizuku_uid=").append(Shizuku.getUid()).append('\n');
        result.append("service_uid=").append(service.getProcessUid()).append('\n');
        result.append("service_pid=").append(service.getProcessPid()).append('\n');

        boolean preparedBefore = service.hasPreparedSession();
        boolean activeBefore = service.hasActiveSession();
        result.append("prepared_before=").append(preparedBefore).append('\n');
        result.append("active_before=").append(activeBefore).append('\n');

        service.prepare(SAMPLE_RATE);
        boolean preparedAfterPrepare = service.hasPreparedSession();
        boolean activeAfterPrepare = service.hasActiveSession();
        boolean heartbeatAfterPrepare = service.heartbeat();
        result.append("prepared_after_prepare=").append(preparedAfterPrepare).append('\n');
        result.append("active_after_prepare=").append(activeAfterPrepare).append('\n');
        result.append("heartbeat_after_prepare=").append(heartbeatAfterPrepare).append('\n');

        service.abortNow();
        boolean preparedAfterAbort = service.hasPreparedSession();
        boolean activeAfterAbort = service.hasActiveSession();
        boolean heartbeatAfterAbort = service.heartbeat();
        result.append("prepared_after_abort=").append(preparedAfterAbort).append('\n');
        result.append("active_after_abort=").append(activeAfterAbort).append('\n');
        result.append("heartbeat_after_abort=").append(heartbeatAfterAbort).append('\n');

        int uid = service.getProcessUid();
        boolean privilegedUid = uid == 0 || uid == 2000;
        boolean ok = Shizuku.pingBinder()
            && privilegedUid
            && !preparedBefore
            && !activeBefore
            && preparedAfterPrepare
            && !activeAfterPrepare
            && !heartbeatAfterPrepare
            && !preparedAfterAbort
            && !activeAfterAbort
            && !heartbeatAfterAbort;
        result.append("privileged_uid=").append(privilegedUid).append('\n');
        result.append("offcall_parity_ok=").append(ok);
        return result.toString();
    }

    private void finish(String result) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        handler.removeCallbacks(timeout);
        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true);
        } catch (Throwable ignored) {
            // Service may already be gone; do not hide the probe result.
        }
        callback.onResult(result);
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
