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

/**
 * Live diagnostic proving that loss of either transferred media endpoint tears down the whole
 * helper generation before the independent heartbeat watchdog could expire.
 */
public final class ShizukuEndpointCloseProbe {
    private static final int SAMPLE_RATE = 16_000;
    private static final int PCM_CHUNK_BYTES = SAMPLE_RATE * 20 / 1000 * 2;
    private static final long MEDIA_WARMUP_TIMEOUT_MS = 1_000L;
    private static final long ENDPOINT_ABORT_MAX_MS = 1_000L;
    private static final long THREAD_STOP_TIMEOUT_MS = 1_000L;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

    private enum Direction {
        DOWNLINK("downlink"),
        UPLINK("uplink");

        final String key;

        Direction(String key) {
            this.key = key;
        }
    }

    private final Context appContext;
    private final ShizukuUserServiceProbe.Callback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs userServiceArgs;

    private final Runnable timeout = () -> finish(
        "probe=shizuku-user-service-endpoint-close-v1\nerror=connect_timeout"
    );

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            handler.removeCallbacks(timeout);
            IShizukuCallMediaService service = IShizukuCallMediaService.Stub.asInterface(binder);
            ShizukuProbeWorker.start("aicall-shizuku-endpoint-close-probe", () -> {
                String result;
                try {
                    result = runProbe(service);
                } catch (Throwable error) {
                    result = "probe=shizuku-user-service-endpoint-close-v1\nerror=" + describe(error);
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
            finish("probe=shizuku-user-service-endpoint-close-v1\nerror=service_disconnected");
        }
    };

    private ShizukuEndpointCloseProbe(Context context, ShizukuUserServiceProbe.Callback callback) {
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
        new ShizukuEndpointCloseProbe(context, callback).start();
    }

    private void start() {
        try {
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable error) {
            handler.removeCallbacks(timeout);
            finish("probe=shizuku-user-service-endpoint-close-v1\nerror=" + describe(error));
        }
    }

    private String runProbe(IShizukuCallMediaService service) throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("probe=shizuku-user-service-endpoint-close-v1\n");
        result.append("mode=live-selective-endpoint-close\n");
        result.append("binder_ping=").append(Shizuku.pingBinder()).append('\n');
        result.append("shizuku_uid=").append(Shizuku.getUid()).append('\n');
        result.append("service_uid=").append(service.getProcessUid()).append('\n');
        result.append("service_pid=").append(service.getProcessPid()).append('\n');
        result.append("sample_rate=").append(SAMPLE_RATE).append('\n');
        result.append("endpoint_abort_max_ms=").append(ENDPOINT_ABORT_MAX_MS).append('\n');

        boolean privilegedUid = service.getProcessUid() == 0 || service.getProcessUid() == 2000;
        boolean downlinkOk = runDirection(service, Direction.DOWNLINK, result);
        boolean uplinkOk = runDirection(service, Direction.UPLINK, result);
        boolean ok = Shizuku.pingBinder() && privilegedUid && downlinkOk && uplinkOk;

        result.append("privileged_uid=").append(privilegedUid).append('\n');
        result.append("endpoint_close_fail_safe_ok=").append(ok);
        return result.toString();
    }

    private boolean runDirection(
        IShizukuCallMediaService service,
        Direction direction,
        StringBuilder result
    ) throws Exception {
        String prefix = direction.key + "_close_";
        ParcelFileDescriptor downlink = null;
        ParcelFileDescriptor uplink = null;
        ShizukuBidirectionalProbeMedia media = null;
        try {
            service.prepare(SAMPLE_RATE);
            service.startMedia();
            boolean activeAfterStart = service.hasActiveSession();
            downlink = service.takeDownlinkReadEnd();
            uplink = service.takeUplinkWriteEnd();
            media = ShizukuBidirectionalProbeMedia.start(
                downlink,
                uplink,
                PCM_CHUNK_BYTES,
                "aicall-endpoint-" + direction.key
            );
            downlink = null;
            uplink = null;

            boolean heartbeatAtBaseline = service.heartbeat();
            boolean mediaWarm = waitForMediaWarmup(media);
            long downlinkBytesBeforeClose = media.downlinkBytes();
            long uplinkBytesBeforeClose = media.uplinkBytes();

            long closeStartNs = SystemClock.elapsedRealtimeNanos();
            if (direction == Direction.DOWNLINK) {
                media.closeDownlinkEndpoint();
            } else {
                media.closeUplinkEndpoint();
            }

            boolean becameInactive = waitForInactive(service, closeStartNs);
            long inactiveNs = becameInactive ? SystemClock.elapsedRealtimeNanos() : -1L;
            long inactiveObservedMs = becameInactive
                ? (inactiveNs - closeStartNs) / 1_000_000L
                : -1L;
            boolean workersStoppedWithoutOwnerClose = waitForWorkersStopped(media);

            boolean preparedAfterClose = service.hasPreparedSession();
            boolean activeAfterClose = service.hasActiveSession();
            boolean heartbeatAfterClose = service.heartbeat();
            String downlinkTerminal = media.downlinkTerminal();
            String uplinkTerminal = media.uplinkTerminal();

            boolean timingOk = becameInactive
                && inactiveObservedMs >= 0L
                && inactiveObservedMs <= ENDPOINT_ABORT_MAX_MS;
            boolean ok = activeAfterStart
                && heartbeatAtBaseline
                && mediaWarm
                && downlinkBytesBeforeClose > 0L
                && uplinkBytesBeforeClose > 0L
                && timingOk
                && workersStoppedWithoutOwnerClose
                && !preparedAfterClose
                && !activeAfterClose
                && !heartbeatAfterClose;

            result.append(prefix).append("active_after_start=").append(activeAfterStart).append('\n');
            result.append(prefix).append("heartbeat_at_baseline=").append(heartbeatAtBaseline).append('\n');
            result.append(prefix).append("media_warm=").append(mediaWarm).append('\n');
            result.append(prefix).append("downlink_bytes_before=").append(downlinkBytesBeforeClose).append('\n');
            result.append(prefix).append("uplink_bytes_before=").append(uplinkBytesBeforeClose).append('\n');
            result.append(prefix).append("became_inactive=").append(becameInactive).append('\n');
            result.append(prefix).append("inactive_observed_ms=").append(inactiveObservedMs).append('\n');
            result.append(prefix).append("timing_ok=").append(timingOk).append('\n');
            result.append(prefix).append("workers_stopped_without_owner_close=")
                .append(workersStoppedWithoutOwnerClose).append('\n');
            result.append(prefix).append("downlink_terminal=").append(downlinkTerminal).append('\n');
            result.append(prefix).append("uplink_terminal=").append(uplinkTerminal).append('\n');
            result.append(prefix).append("prepared_after=").append(preparedAfterClose).append('\n');
            result.append(prefix).append("active_after=").append(activeAfterClose).append('\n');
            result.append(prefix).append("heartbeat_after=").append(heartbeatAfterClose).append('\n');
            result.append(prefix).append("ok=").append(ok).append('\n');
            return ok;
        } finally {
            if (media != null) {
                media.close();
            }
            closeQuietly(downlink);
            closeQuietly(uplink);
            try {
                service.abortNow();
            } catch (Throwable ignored) {
                // Preserve the primary result/error while forcing a clean boundary before next run.
            }
        }
    }

    private static boolean waitForMediaWarmup(ShizukuBidirectionalProbeMedia media)
        throws InterruptedException {
        long deadlineNs = SystemClock.elapsedRealtimeNanos() + MEDIA_WARMUP_TIMEOUT_MS * 1_000_000L;
        while (SystemClock.elapsedRealtimeNanos() < deadlineNs) {
            if (media.downlinkBytes() > 0L && media.uplinkBytes() > 0L) {
                return true;
            }
            Thread.sleep(5L);
        }
        return media.downlinkBytes() > 0L && media.uplinkBytes() > 0L;
    }

    private static boolean waitForInactive(IShizukuCallMediaService service, long startNs)
        throws Exception {
        long deadlineNs = startNs + ENDPOINT_ABORT_MAX_MS * 1_000_000L;
        while (SystemClock.elapsedRealtimeNanos() < deadlineNs) {
            if (!service.hasActiveSession()) {
                return true;
            }
            Thread.sleep(5L);
        }
        return !service.hasActiveSession();
    }

    private static boolean waitForWorkersStopped(ShizukuBidirectionalProbeMedia media)
        throws InterruptedException {
        long deadlineNs = SystemClock.elapsedRealtimeNanos() + THREAD_STOP_TIMEOUT_MS * 1_000_000L;
        while (SystemClock.elapsedRealtimeNanos() < deadlineNs) {
            if (media.threadsStopped()) {
                return true;
            }
            Thread.sleep(5L);
        }
        return media.threadsStopped();
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
