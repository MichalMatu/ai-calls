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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import rikka.shizuku.Shizuku;

/** Live diagnostic for explicit TAKE OVER / abort latency. */
public final class ShizukuAbortLatencyProbe {
    private static final int SAMPLE_RATE = 16_000;
    private static final int PCM_CHUNK_BYTES = SAMPLE_RATE * 20 / 1000 * 2;
    private static final long ABORT_MAX_MS = 500L;
    private static final long INACTIVE_OBSERVATION_TIMEOUT_MS = 1_000L;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;
    private static final long THREAD_JOIN_TIMEOUT_MS = 1_000L;

    private final Context appContext;
    private final ShizukuUserServiceProbe.Callback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs userServiceArgs;

    private final Runnable timeout = () -> finish(
        "probe=shizuku-user-service-abort-latency-v1\nerror=connect_timeout"
    );

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            handler.removeCallbacks(timeout);
            IShizukuCallMediaService service = IShizukuCallMediaService.Stub.asInterface(binder);
            String result;
            try {
                result = runAbortProbe(service);
            } catch (Throwable error) {
                result = "probe=shizuku-user-service-abort-latency-v1\nerror=" + describe(error);
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
            finish("probe=shizuku-user-service-abort-latency-v1\nerror=service_disconnected");
        }
    };

    private ShizukuAbortLatencyProbe(Context context, ShizukuUserServiceProbe.Callback callback) {
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
        new ShizukuAbortLatencyProbe(context, callback).start();
    }

    private void start() {
        try {
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable error) {
            handler.removeCallbacks(timeout);
            finish("probe=shizuku-user-service-abort-latency-v1\nerror=" + describe(error));
        }
    }

    private String runAbortProbe(IShizukuCallMediaService service) throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("probe=shizuku-user-service-abort-latency-v1\n");
        result.append("mode=live-explicit-abort\n");
        result.append("binder_ping=").append(Shizuku.pingBinder()).append('\n');
        result.append("shizuku_uid=").append(Shizuku.getUid()).append('\n');
        result.append("service_uid=").append(service.getProcessUid()).append('\n');
        result.append("service_pid=").append(service.getProcessPid()).append('\n');
        result.append("sample_rate=").append(SAMPLE_RATE).append('\n');
        result.append("abort_max_ms=").append(ABORT_MAX_MS).append('\n');

        service.prepare(SAMPLE_RATE);
        service.startMedia();
        boolean activeAfterStart = service.hasActiveSession();
        ParcelFileDescriptor downlink = service.takeDownlinkReadEnd();
        ParcelFileDescriptor uplink = service.takeUplinkWriteEnd();

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicLong downlinkBytes = new AtomicLong();
        AtomicLong uplinkBytes = new AtomicLong();
        AtomicReference<String> downlinkTerminal = new AtomicReference<>("none");
        AtomicReference<String> uplinkTerminal = new AtomicReference<>("none");

        Thread downlinkThread = new Thread(() -> {
            byte[] buffer = new byte[PCM_CHUNK_BYTES];
            try (ParcelFileDescriptor.AutoCloseInputStream input =
                     new ParcelFileDescriptor.AutoCloseInputStream(downlink)) {
                while (!stop.get()) {
                    int read = input.read(buffer);
                    if (read < 0) {
                        downlinkTerminal.set("eof");
                        break;
                    }
                    if (read > 0) {
                        downlinkBytes.addAndGet(read);
                    }
                }
            } catch (Throwable error) {
                downlinkTerminal.set(error.getClass().getSimpleName());
            }
        }, "aicall-abort-rx-drain");
        downlinkThread.setDaemon(true);

        Thread uplinkThread = new Thread(() -> {
            byte[] silence = new byte[PCM_CHUNK_BYTES];
            try (ParcelFileDescriptor.AutoCloseOutputStream output =
                     new ParcelFileDescriptor.AutoCloseOutputStream(uplink)) {
                while (!stop.get()) {
                    output.write(silence);
                    uplinkBytes.addAndGet(silence.length);
                }
                output.flush();
            } catch (Throwable error) {
                uplinkTerminal.set(error.getClass().getSimpleName());
            }
        }, "aicall-abort-tx-silence");
        uplinkThread.setDaemon(true);

        downlinkThread.start();
        uplinkThread.start();

        boolean heartbeatAtBaseline = service.heartbeat();
        Thread.sleep(200L);
        long abortStartNs = SystemClock.elapsedRealtimeNanos();
        service.abortNow();
        long abortReturnNs = SystemClock.elapsedRealtimeNanos();
        long abortRpcMs = (abortReturnNs - abortStartNs) / 1_000_000L;

        boolean becameInactive = !service.hasActiveSession();
        long inactiveNs = becameInactive ? SystemClock.elapsedRealtimeNanos() : -1L;
        long deadlineNs = abortStartNs + INACTIVE_OBSERVATION_TIMEOUT_MS * 1_000_000L;
        while (!becameInactive && SystemClock.elapsedRealtimeNanos() < deadlineNs) {
            Thread.sleep(5L);
            if (!service.hasActiveSession()) {
                becameInactive = true;
                inactiveNs = SystemClock.elapsedRealtimeNanos();
            }
        }
        long inactiveObservedMs = becameInactive ? (inactiveNs - abortStartNs) / 1_000_000L : -1L;

        boolean preparedAfterAbort = service.hasPreparedSession();
        boolean activeAfterAbort = service.hasActiveSession();
        boolean heartbeatAfterAbort = service.heartbeat();

        stop.set(true);
        closeQuietly(downlink);
        closeQuietly(uplink);
        downlinkThread.interrupt();
        uplinkThread.interrupt();
        downlinkThread.join(THREAD_JOIN_TIMEOUT_MS);
        uplinkThread.join(THREAD_JOIN_TIMEOUT_MS);

        boolean privilegedUid = service.getProcessUid() == 0 || service.getProcessUid() == 2000;
        boolean timingOk = abortRpcMs <= ABORT_MAX_MS
            && inactiveObservedMs >= 0L
            && inactiveObservedMs <= ABORT_MAX_MS;
        boolean threadsStopped = !downlinkThread.isAlive() && !uplinkThread.isAlive();
        boolean ok = Shizuku.pingBinder()
            && privilegedUid
            && activeAfterStart
            && heartbeatAtBaseline
            && downlinkBytes.get() > 0L
            && uplinkBytes.get() > 0L
            && becameInactive
            && timingOk
            && !preparedAfterAbort
            && !activeAfterAbort
            && !heartbeatAfterAbort
            && threadsStopped;

        result.append("active_after_start=").append(activeAfterStart).append('\n');
        result.append("heartbeat_at_baseline=").append(heartbeatAtBaseline).append('\n');
        result.append("downlink_bytes_read=").append(downlinkBytes.get()).append('\n');
        result.append("uplink_bytes_written=").append(uplinkBytes.get()).append('\n');
        result.append("abort_rpc_ms=").append(abortRpcMs).append('\n');
        result.append("abort_became_inactive=").append(becameInactive).append('\n');
        result.append("abort_inactive_observed_ms=").append(inactiveObservedMs).append('\n');
        result.append("abort_timing_ok=").append(timingOk).append('\n');
        result.append("downlink_terminal=").append(downlinkTerminal.get()).append('\n');
        result.append("uplink_terminal=").append(uplinkTerminal.get()).append('\n');
        result.append("threads_stopped=").append(threadsStopped).append('\n');
        result.append("prepared_after_abort=").append(preparedAfterAbort).append('\n');
        result.append("active_after_abort=").append(activeAfterAbort).append('\n');
        result.append("heartbeat_after_abort=").append(heartbeatAfterAbort).append('\n');
        result.append("privileged_uid=").append(privilegedUid).append('\n');
        result.append("explicit_abort_fail_safe_ok=").append(ok);
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
