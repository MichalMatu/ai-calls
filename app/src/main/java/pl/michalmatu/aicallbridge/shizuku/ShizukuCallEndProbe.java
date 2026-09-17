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
 * Live diagnostic that keeps the helper heartbeat alive while waiting for the cellular call media
 * paths to terminate naturally. The probe itself never requests telephony hangup.
 */
public final class ShizukuCallEndProbe {
    private static final int SAMPLE_RATE = 16_000;
    private static final int PCM_CHUNK_BYTES = SAMPLE_RATE * 20 / 1000 * 2;
    private static final long DEFAULT_WAIT_MS = 120_000L;
    private static final long MIN_WAIT_MS = 5_000L;
    private static final long MAX_WAIT_MS = 180_000L;
    private static final long POLL_INTERVAL_MS = 50L;
    private static final long MEDIA_WARMUP_TIMEOUT_MS = 1_000L;
    private static final long THREAD_STOP_TIMEOUT_MS = 1_000L;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

    private final Context appContext;
    private final long waitMs;
    private final ShizukuUserServiceProbe.Callback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs userServiceArgs;

    private final Runnable timeout = () -> finish(
        "probe=shizuku-user-service-call-end-v1\nerror=connect_timeout"
    );

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            handler.removeCallbacks(timeout);
            IShizukuCallMediaService service = IShizukuCallMediaService.Stub.asInterface(binder);
            ShizukuProbeWorker.start("aicall-shizuku-call-end-probe", () -> {
                String result;
                try {
                    result = runCallEndProbe(service);
                } catch (Throwable error) {
                    result = "probe=shizuku-user-service-call-end-v1\nerror=" + describe(error);
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
            finish("probe=shizuku-user-service-call-end-v1\nerror=service_disconnected");
        }
    };

    private ShizukuCallEndProbe(
        Context context,
        long waitMs,
        ShizukuUserServiceProbe.Callback callback
    ) {
        this.appContext = context.getApplicationContext();
        this.waitMs = validateWaitMs(waitMs);
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
        run(context, DEFAULT_WAIT_MS, callback);
    }

    public static void run(
        Context context,
        long waitMs,
        ShizukuUserServiceProbe.Callback callback
    ) {
        new ShizukuCallEndProbe(context, waitMs, callback).start();
    }

    static long defaultWaitMs() {
        return DEFAULT_WAIT_MS;
    }

    static long validateWaitMs(long waitMs) {
        if (waitMs < MIN_WAIT_MS || waitMs > MAX_WAIT_MS) {
            throw new IllegalArgumentException(
                "waitMs must be between " + MIN_WAIT_MS + " and " + MAX_WAIT_MS
            );
        }
        return waitMs;
    }

    private void start() {
        try {
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable error) {
            handler.removeCallbacks(timeout);
            finish("probe=shizuku-user-service-call-end-v1\nerror=" + describe(error));
        }
    }

    private String runCallEndProbe(IShizukuCallMediaService service) throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("probe=shizuku-user-service-call-end-v1\n");
        result.append("mode=live-natural-call-end\n");
        result.append("binder_ping=").append(Shizuku.pingBinder()).append('\n');
        result.append("shizuku_uid=").append(Shizuku.getUid()).append('\n');
        result.append("service_uid=").append(service.getProcessUid()).append('\n');
        result.append("service_pid=").append(service.getProcessPid()).append('\n');
        result.append("sample_rate=").append(SAMPLE_RATE).append('\n');
        result.append("wait_ms=").append(waitMs).append('\n');

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
                "aicall-call-end"
            );
            downlink = null;
            uplink = null;

            boolean heartbeatAtBaseline = service.heartbeat();
            boolean mediaWarm = waitForMediaWarmup(media);
            long downlinkBytesBefore = media.downlinkBytes();
            long uplinkBytesBefore = media.uplinkBytes();

            long waitStartNs = SystemClock.elapsedRealtimeNanos();
            long deadlineNs = waitStartNs + waitMs * 1_000_000L;
            boolean callMediaEnded = false;
            int heartbeatCount = 0;
            while (SystemClock.elapsedRealtimeNanos() < deadlineNs) {
                boolean heartbeatOk = service.heartbeat();
                heartbeatCount++;
                boolean active = service.hasActiveSession();
                if (!heartbeatOk || !active) {
                    callMediaEnded = !active;
                    break;
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
            long callMediaEndObservedMs = callMediaEnded
                ? (SystemClock.elapsedRealtimeNanos() - waitStartNs) / 1_000_000L
                : -1L;

            boolean workersStoppedWithoutOwnerClose = waitForWorkersStopped(media);
            boolean preparedAfterEnd = service.hasPreparedSession();
            boolean activeAfterEnd = service.hasActiveSession();
            boolean heartbeatAfterEnd = service.heartbeat();
            long downlinkBytesAfter = media.downlinkBytes();
            long uplinkBytesAfter = media.uplinkBytes();
            String downlinkTerminal = media.downlinkTerminal();
            String uplinkTerminal = media.uplinkTerminal();

            boolean privilegedUid = service.getProcessUid() == 0 || service.getProcessUid() == 2000;
            boolean ok = Shizuku.pingBinder()
                && privilegedUid
                && activeAfterStart
                && heartbeatAtBaseline
                && mediaWarm
                && downlinkBytesBefore > 0L
                && uplinkBytesBefore > 0L
                && callMediaEnded
                && callMediaEndObservedMs >= 0L
                && callMediaEndObservedMs <= waitMs
                && workersStoppedWithoutOwnerClose
                && !preparedAfterEnd
                && !activeAfterEnd
                && !heartbeatAfterEnd;

            result.append("active_after_start=").append(activeAfterStart).append('\n');
            result.append("heartbeat_at_baseline=").append(heartbeatAtBaseline).append('\n');
            result.append("media_warm=").append(mediaWarm).append('\n');
            result.append("downlink_bytes_before=").append(downlinkBytesBefore).append('\n');
            result.append("uplink_bytes_before=").append(uplinkBytesBefore).append('\n');
            result.append("heartbeat_count=").append(heartbeatCount).append('\n');
            result.append("call_media_ended=").append(callMediaEnded).append('\n');
            result.append("call_media_end_observed_ms=").append(callMediaEndObservedMs).append('\n');
            result.append("workers_stopped_without_owner_close=")
                .append(workersStoppedWithoutOwnerClose).append('\n');
            result.append("downlink_bytes_after=").append(downlinkBytesAfter).append('\n');
            result.append("uplink_bytes_after=").append(uplinkBytesAfter).append('\n');
            result.append("downlink_terminal=").append(downlinkTerminal).append('\n');
            result.append("uplink_terminal=").append(uplinkTerminal).append('\n');
            result.append("prepared_after_end=").append(preparedAfterEnd).append('\n');
            result.append("active_after_end=").append(activeAfterEnd).append('\n');
            result.append("heartbeat_after_end=").append(heartbeatAfterEnd).append('\n');
            result.append("privileged_uid=").append(privilegedUid).append('\n');
            result.append("call_end_fail_safe_ok=").append(ok);
            return result.toString();
        } finally {
            if (media != null) {
                media.close();
            }
            closeQuietly(downlink);
            closeQuietly(uplink);
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
