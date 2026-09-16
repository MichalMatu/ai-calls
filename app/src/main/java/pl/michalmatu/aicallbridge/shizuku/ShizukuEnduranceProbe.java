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

/** Short live endurance diagnostic for sustained bidirectional call media. */
public final class ShizukuEnduranceProbe {
    private static final int SAMPLE_RATE = 16_000;
    private static final int PCM_CHUNK_BYTES = SAMPLE_RATE * 20 / 1000 * 2;
    private static final long DURATION_MS = 30_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 250L;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

    private final Context appContext;
    private final ShizukuUserServiceProbe.Callback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs userServiceArgs;

    private final Runnable timeout = () -> finish(
        "probe=shizuku-user-service-endurance-v1\nerror=connect_timeout"
    );

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            handler.removeCallbacks(timeout);
            IShizukuCallMediaService service = IShizukuCallMediaService.Stub.asInterface(binder);
            ShizukuProbeWorker.start("aicall-shizuku-endurance-probe", () -> {
                String result;
                try {
                    result = runEnduranceProbe(service);
                } catch (Throwable error) {
                    result = "probe=shizuku-user-service-endurance-v1\nerror=" + describe(error);
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
            finish("probe=shizuku-user-service-endurance-v1\nerror=service_disconnected");
        }
    };

    private ShizukuEnduranceProbe(Context context, ShizukuUserServiceProbe.Callback callback) {
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
        new ShizukuEnduranceProbe(context, callback).start();
    }

    private void start() {
        try {
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable error) {
            handler.removeCallbacks(timeout);
            finish("probe=shizuku-user-service-endurance-v1\nerror=" + describe(error));
        }
    }

    private String runEnduranceProbe(IShizukuCallMediaService service) throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("probe=shizuku-user-service-endurance-v1\n");
        result.append("mode=live-bidirectional-endurance\n");
        result.append("binder_ping=").append(Shizuku.pingBinder()).append('\n');
        result.append("shizuku_uid=").append(Shizuku.getUid()).append('\n');
        result.append("service_uid=").append(service.getProcessUid()).append('\n');
        result.append("service_pid=").append(service.getProcessPid()).append('\n');
        result.append("sample_rate=").append(SAMPLE_RATE).append('\n');
        result.append("duration_ms=").append(DURATION_MS).append('\n');
        result.append("heartbeat_interval_ms=").append(HEARTBEAT_INTERVAL_MS).append('\n');

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
                "aicall-endurance"
            );
            downlink = null;
            uplink = null;

            long startedNs = SystemClock.elapsedRealtimeNanos();
            long deadlineNs = startedNs + DURATION_MS * 1_000_000L;
            int heartbeatCount = 0;
            int activeCheckCount = 0;
            boolean allHeartbeatsOk = true;
            boolean activeThroughout = activeAfterStart;
            while (SystemClock.elapsedRealtimeNanos() < deadlineNs) {
                if (!service.heartbeat()) {
                    allHeartbeatsOk = false;
                    break;
                }
                heartbeatCount++;
                if (!service.hasActiveSession()) {
                    activeThroughout = false;
                    break;
                }
                activeCheckCount++;
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
            }
            long observedDurationMs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L;
            boolean activeBeforeAbort = service.hasActiveSession();
            boolean heartbeatBeforeAbort = service.heartbeat();

            long abortStartNs = SystemClock.elapsedRealtimeNanos();
            service.abortNow();
            long abortRpcMs = (SystemClock.elapsedRealtimeNanos() - abortStartNs) / 1_000_000L;
            boolean preparedAfterAbort = service.hasPreparedSession();
            boolean activeAfterAbort = service.hasActiveSession();
            boolean heartbeatAfterAbort = service.heartbeat();

            media.close();
            long downlinkBytes = media.downlinkBytes();
            long uplinkBytes = media.uplinkBytes();
            String downlinkTerminal = media.downlinkTerminal();
            String uplinkTerminal = media.uplinkTerminal();
            boolean threadsStopped = media.threadsStopped();

            boolean privilegedUid = service.getProcessUid() == 0 || service.getProcessUid() == 2000;
            boolean durationOk = observedDurationMs >= DURATION_MS - HEARTBEAT_INTERVAL_MS;
            boolean ok = Shizuku.pingBinder()
                && privilegedUid
                && activeAfterStart
                && allHeartbeatsOk
                && activeThroughout
                && durationOk
                && activeBeforeAbort
                && heartbeatBeforeAbort
                && downlinkBytes > 0L
                && uplinkBytes > 0L
                && !preparedAfterAbort
                && !activeAfterAbort
                && !heartbeatAfterAbort
                && threadsStopped;

            result.append("active_after_start=").append(activeAfterStart).append('\n');
            result.append("heartbeat_count=").append(heartbeatCount).append('\n');
            result.append("active_check_count=").append(activeCheckCount).append('\n');
            result.append("all_heartbeats_ok=").append(allHeartbeatsOk).append('\n');
            result.append("active_throughout=").append(activeThroughout).append('\n');
            result.append("observed_duration_ms=").append(observedDurationMs).append('\n');
            result.append("duration_ok=").append(durationOk).append('\n');
            result.append("downlink_bytes_read=").append(downlinkBytes).append('\n');
            result.append("uplink_bytes_written=").append(uplinkBytes).append('\n');
            result.append("active_before_abort=").append(activeBeforeAbort).append('\n');
            result.append("heartbeat_before_abort=").append(heartbeatBeforeAbort).append('\n');
            result.append("abort_rpc_ms=").append(abortRpcMs).append('\n');
            result.append("downlink_terminal=").append(downlinkTerminal).append('\n');
            result.append("uplink_terminal=").append(uplinkTerminal).append('\n');
            result.append("threads_stopped=").append(threadsStopped).append('\n');
            result.append("prepared_after_abort=").append(preparedAfterAbort).append('\n');
            result.append("active_after_abort=").append(activeAfterAbort).append('\n');
            result.append("heartbeat_after_abort=").append(heartbeatAfterAbort).append('\n');
            result.append("privileged_uid=").append(privilegedUid).append('\n');
            result.append("endurance_ok=").append(ok);
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
