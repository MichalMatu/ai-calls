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
 * Live diagnostic that repeatedly prepares, starts, transfers PFDs and explicitly aborts media
 * while keeping one Shizuku UserService binding alive for the whole run.
 */
public final class ShizukuCycleProbe {
    private static final int SAMPLE_RATE = 16_000;
    private static final int PCM_CHUNK_BYTES = SAMPLE_RATE * 20 / 1000 * 2;
    private static final int DEFAULT_CYCLE_COUNT = 20;
    private static final int MAX_CYCLE_COUNT = 20;
    private static final long MEDIA_WARMUP_TIMEOUT_MS = 1_000L;
    private static final long ABORT_MAX_MS = 500L;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

    private final Context appContext;
    private final int cycleCount;
    private final ShizukuUserServiceProbe.Callback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs userServiceArgs;

    private final Runnable timeout = () -> finish(
        "probe=shizuku-user-service-cycle-v1\nerror=connect_timeout"
    );

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            handler.removeCallbacks(timeout);
            IShizukuCallMediaService service = IShizukuCallMediaService.Stub.asInterface(binder);
            ShizukuProbeWorker.start("aicall-shizuku-cycle-probe", () -> {
                String result;
                try {
                    result = runCycles(service);
                } catch (Throwable error) {
                    result = "probe=shizuku-user-service-cycle-v1\nerror=" + describe(error);
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
            finish("probe=shizuku-user-service-cycle-v1\nerror=service_disconnected");
        }
    };

    private ShizukuCycleProbe(
        Context context,
        int cycleCount,
        ShizukuUserServiceProbe.Callback callback
    ) {
        this.appContext = context.getApplicationContext();
        this.cycleCount = validateCycleCount(cycleCount);
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
        run(context, DEFAULT_CYCLE_COUNT, callback);
    }

    public static void run(
        Context context,
        int cycleCount,
        ShizukuUserServiceProbe.Callback callback
    ) {
        new ShizukuCycleProbe(context, cycleCount, callback).start();
    }

    static int defaultCycleCount() {
        return DEFAULT_CYCLE_COUNT;
    }

    static int validateCycleCount(int cycleCount) {
        if (cycleCount < 1 || cycleCount > MAX_CYCLE_COUNT) {
            throw new IllegalArgumentException("cycleCount must be between 1 and " + MAX_CYCLE_COUNT);
        }
        return cycleCount;
    }

    private void start() {
        try {
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable error) {
            handler.removeCallbacks(timeout);
            finish("probe=shizuku-user-service-cycle-v1\nerror=" + describe(error));
        }
    }

    private String runCycles(IShizukuCallMediaService service) throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("probe=shizuku-user-service-cycle-v1\n");
        result.append("mode=live-repeated-start-abort\n");
        result.append("binder_ping=").append(Shizuku.pingBinder()).append('\n');
        result.append("shizuku_uid=").append(Shizuku.getUid()).append('\n');
        result.append("service_uid=").append(service.getProcessUid()).append('\n');
        int initialServicePid = service.getProcessPid();
        result.append("service_pid=").append(initialServicePid).append('\n');
        result.append("sample_rate=").append(SAMPLE_RATE).append('\n');
        result.append("requested_cycles=").append(cycleCount).append('\n');
        result.append("abort_max_ms=").append(ABORT_MAX_MS).append('\n');

        boolean privilegedUid = service.getProcessUid() == 0 || service.getProcessUid() == 2000;
        int completedCycles = 0;
        int firstFailedCycle = 0;
        long cumulativeDownlinkBytes = 0L;
        long cumulativeUplinkBytes = 0L;
        long maxAbortRpcMs = 0L;
        boolean servicePidStable = true;

        for (int cycle = 1; cycle <= cycleCount; cycle++) {
            CycleResult cycleResult = runCycle(service, cycle, initialServicePid, result);
            cumulativeDownlinkBytes += cycleResult.downlinkBytes;
            cumulativeUplinkBytes += cycleResult.uplinkBytes;
            maxAbortRpcMs = Math.max(maxAbortRpcMs, cycleResult.abortRpcMs);
            servicePidStable &= cycleResult.servicePidStable;
            if (!cycleResult.ok) {
                firstFailedCycle = cycle;
                break;
            }
            completedCycles++;
        }

        boolean finalPrepared = service.hasPreparedSession();
        boolean finalActive = service.hasActiveSession();
        boolean finalHeartbeat = service.heartbeat();
        boolean ok = Shizuku.pingBinder()
            && privilegedUid
            && servicePidStable
            && completedCycles == cycleCount
            && firstFailedCycle == 0
            && cumulativeDownlinkBytes > 0L
            && cumulativeUplinkBytes > 0L
            && maxAbortRpcMs <= ABORT_MAX_MS
            && !finalPrepared
            && !finalActive
            && !finalHeartbeat;

        result.append("completed_cycles=").append(completedCycles).append('\n');
        result.append("first_failed_cycle=").append(firstFailedCycle).append('\n');
        result.append("cumulative_downlink_bytes=").append(cumulativeDownlinkBytes).append('\n');
        result.append("cumulative_uplink_bytes=").append(cumulativeUplinkBytes).append('\n');
        result.append("max_abort_rpc_ms=").append(maxAbortRpcMs).append('\n');
        result.append("service_pid_stable=").append(servicePidStable).append('\n');
        result.append("final_prepared=").append(finalPrepared).append('\n');
        result.append("final_active=").append(finalActive).append('\n');
        result.append("final_heartbeat=").append(finalHeartbeat).append('\n');
        result.append("privileged_uid=").append(privilegedUid).append('\n');
        result.append("cycle_probe_ok=").append(ok);
        return result.toString();
    }

    private CycleResult runCycle(
        IShizukuCallMediaService service,
        int cycle,
        int initialServicePid,
        StringBuilder result
    ) throws Exception {
        ParcelFileDescriptor downlink = null;
        ParcelFileDescriptor uplink = null;
        ShizukuBidirectionalProbeMedia media = null;
        boolean activeAfterStart = false;
        boolean heartbeatAtBaseline = false;
        boolean mediaWarm = false;
        long downlinkBytes = 0L;
        long uplinkBytes = 0L;
        long abortRpcMs = Long.MAX_VALUE;
        boolean preparedAfterAbort = true;
        boolean activeAfterAbort = true;
        boolean heartbeatAfterAbort = true;
        boolean threadsStopped = false;
        boolean servicePidStable = service.getProcessPid() == initialServicePid;

        try {
            service.prepare(SAMPLE_RATE);
            service.startMedia();
            activeAfterStart = service.hasActiveSession();
            downlink = service.takeDownlinkReadEnd();
            uplink = service.takeUplinkWriteEnd();
            media = ShizukuBidirectionalProbeMedia.start(
                downlink,
                uplink,
                PCM_CHUNK_BYTES,
                "aicall-cycle-" + cycle
            );
            downlink = null;
            uplink = null;

            heartbeatAtBaseline = service.heartbeat();
            mediaWarm = waitForMediaWarmup(media);

            long abortStartNs = SystemClock.elapsedRealtimeNanos();
            service.abortNow();
            abortRpcMs = (SystemClock.elapsedRealtimeNanos() - abortStartNs) / 1_000_000L;
            preparedAfterAbort = service.hasPreparedSession();
            activeAfterAbort = service.hasActiveSession();
            heartbeatAfterAbort = service.heartbeat();

            media.close();
            downlinkBytes = media.downlinkBytes();
            uplinkBytes = media.uplinkBytes();
            threadsStopped = media.threadsStopped();

            boolean ok = servicePidStable
                && activeAfterStart
                && heartbeatAtBaseline
                && mediaWarm
                && downlinkBytes > 0L
                && uplinkBytes > 0L
                && abortRpcMs <= ABORT_MAX_MS
                && !preparedAfterAbort
                && !activeAfterAbort
                && !heartbeatAfterAbort
                && threadsStopped;

            appendCycleResult(
                result,
                cycle,
                servicePidStable,
                activeAfterStart,
                heartbeatAtBaseline,
                mediaWarm,
                downlinkBytes,
                uplinkBytes,
                abortRpcMs,
                preparedAfterAbort,
                activeAfterAbort,
                heartbeatAfterAbort,
                threadsStopped,
                ok
            );
            return new CycleResult(ok, servicePidStable, downlinkBytes, uplinkBytes, abortRpcMs);
        } finally {
            if (media != null) {
                media.close();
            }
            closeQuietly(downlink);
            closeQuietly(uplink);
            try {
                service.abortNow();
            } catch (Throwable ignored) {
                // Preserve the primary result/error and force an empty boundary before next cycle.
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

    private static void appendCycleResult(
        StringBuilder result,
        int cycle,
        boolean servicePidStable,
        boolean activeAfterStart,
        boolean heartbeatAtBaseline,
        boolean mediaWarm,
        long downlinkBytes,
        long uplinkBytes,
        long abortRpcMs,
        boolean preparedAfterAbort,
        boolean activeAfterAbort,
        boolean heartbeatAfterAbort,
        boolean threadsStopped,
        boolean ok
    ) {
        String prefix = "cycle_" + cycle + "_";
        result.append(prefix).append("service_pid_stable=").append(servicePidStable).append('\n');
        result.append(prefix).append("active_after_start=").append(activeAfterStart).append('\n');
        result.append(prefix).append("heartbeat_at_baseline=").append(heartbeatAtBaseline).append('\n');
        result.append(prefix).append("media_warm=").append(mediaWarm).append('\n');
        result.append(prefix).append("downlink_bytes=").append(downlinkBytes).append('\n');
        result.append(prefix).append("uplink_bytes=").append(uplinkBytes).append('\n');
        result.append(prefix).append("abort_rpc_ms=").append(abortRpcMs).append('\n');
        result.append(prefix).append("prepared_after_abort=").append(preparedAfterAbort).append('\n');
        result.append(prefix).append("active_after_abort=").append(activeAfterAbort).append('\n');
        result.append(prefix).append("heartbeat_after_abort=").append(heartbeatAfterAbort).append('\n');
        result.append(prefix).append("threads_stopped=").append(threadsStopped).append('\n');
        result.append(prefix).append("ok=").append(ok).append('\n');
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

    private static final class CycleResult {
        final boolean ok;
        final boolean servicePidStable;
        final long downlinkBytes;
        final long uplinkBytes;
        final long abortRpcMs;

        CycleResult(
            boolean ok,
            boolean servicePidStable,
            long downlinkBytes,
            long uplinkBytes,
            long abortRpcMs
        ) {
            this.ok = ok;
            this.servicePidStable = servicePidStable;
            this.downlinkBytes = downlinkBytes;
            this.uplinkBytes = uplinkBytes;
            this.abortRpcMs = abortRpcMs;
        }
    }
}
