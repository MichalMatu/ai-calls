package pl.michalmatu.aicallbridge.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/** Live diagnostic for the helper heartbeat fail-safe. */
public final class ShizukuWatchdogProbe {
    private static final int SAMPLE_RATE = 16_000;
    private static final int PCM_CHUNK_BYTES = SAMPLE_RATE * 20 / 1000 * 2;
    private static final long HEARTBEAT_TIMEOUT_MS = 2_000L;
    private static final long OBSERVATION_TIMEOUT_MS = 5_000L;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

    private final Context appContext;
    private final ShizukuUserServiceProbe.Callback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs userServiceArgs;

    private final Runnable timeout = () -> finish(
        "probe=shizuku-user-service-watchdog-v1\nerror=connect_timeout"
    );

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            handler.removeCallbacks(timeout);
            IShizukuCallMediaService service = IShizukuCallMediaService.Stub.asInterface(binder);
            ShizukuProbeWorker.start("aicall-shizuku-watchdog-probe", () -> {
                String result;
                try {
                    result = runWatchdogProbe(service);
                } catch (Throwable error) {
                    result = "probe=shizuku-user-service-watchdog-v1\nerror=" + describe(error);
                } finally {
                    try {
                        service.abortNow();
                    } catch (Throwable ignored) {
                        // Fail-safe cleanup.
                    }
                }
                String completedResult = result;
                handler.post(() -> finish(completedResult));
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            finish("probe=shizuku-user-service-watchdog-v1\nerror=service_disconnected");
        }
    };

    private ShizukuWatchdogProbe(Context context, ShizukuUserServiceProbe.Callback callback) {
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

    public static void run(Context context, ShizukuUserServiceProbe.Callback callback) {
        new ShizukuWatchdogProbe(context, callback).start();
    }

    private void start() {
        try {
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable error) {
            handler.removeCallbacks(timeout);
            finish("probe=shizuku-user-service-watchdog-v1\nerror=" + describe(error));
        }
    }

    private String runWatchdogProbe(IShizukuCallMediaService service) throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("probe=shizuku-user-service-watchdog-v1\n");
        result.append("mode=live-heartbeat-expiry\n");
        result.append("binder_ping=").append(Shizuku.pingBinder()).append('\n');
        result.append("shizuku_uid=").append(Shizuku.getUid()).append('\n');
        result.append("service_uid=").append(service.getProcessUid()).append('\n');
        result.append("service_pid=").append(service.getProcessPid()).append('\n');
        result.append("sample_rate=").append(SAMPLE_RATE).append('\n');
        result.append("heartbeat_timeout_ms=").append(HEARTBEAT_TIMEOUT_MS).append('\n');

        service.prepare(SAMPLE_RATE);
        service.startMedia();
        boolean activeAfterStart = service.hasActiveSession();
        ParcelFileDescriptor downlink = null;
        ParcelFileDescriptor uplink = null;
        ShizukuBidirectionalProbeMedia media = null;
        try {
            downlink = service.takeDownlinkReadEnd();
            uplink = service.takeUplinkWriteEnd();
            media = ShizukuBidirectionalProbeMedia.start(
                downlink,
                uplink,
                PCM_CHUNK_BYTES,
                "aicall-watchdog"
            );
            downlink = null;
            uplink = null;

            boolean heartbeatAtBaseline = service.heartbeat();
            long baselineNs = SystemClock.elapsedRealtimeNanos();
            boolean becameInactive = false;
            long inactiveNs = -1L;
            long deadlineNs = baselineNs + OBSERVATION_TIMEOUT_MS * 1_000_000L;
            while (SystemClock.elapsedRealtimeNanos() < deadlineNs) {
                if (!service.hasActiveSession()) {
                    becameInactive = true;
                    inactiveNs = SystemClock.elapsedRealtimeNanos();
                    break;
                }
                Thread.sleep(10L);
            }

            long observedMs = becameInactive ? (inactiveNs - baselineNs) / 1_000_000L : -1L;
            long overrunMs = becameInactive ? observedMs - HEARTBEAT_TIMEOUT_MS : -1L;
            boolean heartbeatAfterTimeout = service.heartbeat();
            boolean preparedAfterTimeout = service.hasPreparedSession();
            boolean activeAfterTimeout = service.hasActiveSession();

            media.close();
            long downlinkBytes = media.downlinkBytes();
            long uplinkBytes = media.uplinkBytes();
            String downlinkTerminal = media.downlinkTerminal();
            String uplinkTerminal = media.uplinkTerminal();
            boolean threadsStopped = media.threadsStopped();

            boolean privilegedUid = service.getProcessUid() == 0 || service.getProcessUid() == 2000;
            boolean timingOk = observedMs >= 1_800L && observedMs <= 3_000L;
            boolean ok = Shizuku.pingBinder()
                && privilegedUid
                && activeAfterStart
                && heartbeatAtBaseline
                && becameInactive
                && timingOk
                && !heartbeatAfterTimeout
                && !preparedAfterTimeout
                && !activeAfterTimeout
                && downlinkBytes > 0L
                && uplinkBytes > 0L
                && threadsStopped;

            result.append("active_after_start=").append(activeAfterStart).append('\n');
            result.append("heartbeat_at_baseline=").append(heartbeatAtBaseline).append('\n');
            result.append("watchdog_became_inactive=").append(becameInactive).append('\n');
            result.append("watchdog_observed_ms=").append(observedMs).append('\n');
            result.append("watchdog_overrun_ms=").append(overrunMs).append('\n');
            result.append("watchdog_timing_ok=").append(timingOk).append('\n');
            result.append("downlink_bytes_read=").append(downlinkBytes).append('\n');
            result.append("uplink_bytes_written=").append(uplinkBytes).append('\n');
            result.append("downlink_terminal=").append(downlinkTerminal).append('\n');
            result.append("uplink_terminal=").append(uplinkTerminal).append('\n');
            result.append("threads_stopped=").append(threadsStopped).append('\n');
            result.append("prepared_after_timeout=").append(preparedAfterTimeout).append('\n');
            result.append("active_after_timeout=").append(activeAfterTimeout).append('\n');
            result.append("heartbeat_after_timeout=").append(heartbeatAfterTimeout).append('\n');
            result.append("privileged_uid=").append(privilegedUid).append('\n');
            result.append("watchdog_fail_safe_ok=").append(ok);
            return result.toString();
        } finally {
            if (media != null) {
                media.close();
            }
            closeQuietly(downlink);
            closeQuietly(uplink);
        }
    }

    private void finish(String result) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        handler.removeCallbacks(timeout);
        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true);
        } catch (Throwable ignored) {
            // Service may already be gone; preserve the diagnostic result.
        }
        callback.onResult(result);
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (Throwable ignored) {
            // Diagnostic cleanup path.
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
                result.append(':').append(message.replace('\n', ' ').replace('\r', ' '));
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
