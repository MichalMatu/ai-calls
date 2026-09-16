package pl.michalmatu.aicallbridge.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/** Bounded parity probes for the Shizuku UserService transport. */
public final class ShizukuUserServiceProbe {
    public interface Callback {
        void onResult(String result);
    }

    private static final int SAMPLE_RATE = 16_000;
    private static final int LIVE_MIN_DURATION_MS = 500;
    private static final int LIVE_MAX_DURATION_MS = 5_000;
    private static final int DTMF_DURATION_MS = 300;
    private static final double DTMF_AMPLITUDE = 0.10;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

    private final Context appContext;
    private final Callback callback;
    private final boolean live;
    private final int liveDurationMs;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs userServiceArgs;

    private final Runnable timeout = () -> finish(
        probeName() + "
error=connect_timeout"
    );

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            handler.removeCallbacks(timeout);
            IShizukuCallMediaService service = IShizukuCallMediaService.Stub.asInterface(binder);
            String result;
            try {
                result = live ? runLiveProbe(service, liveDurationMs) : runOffCallProbe(service);
            } catch (Throwable error) {
                result = (live ? "probe=shizuku-user-service-live-v1" : "probe=shizuku-user-service-v1")
                    + "\nerror=" + describe(error);
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
            finish(
                (live ? "probe=shizuku-user-service-live-v1" : "probe=shizuku-user-service-v1")
                    + "\nerror=service_disconnected"
            );
        }
    };

    private ShizukuUserServiceProbe(
        Context context,
        Callback callback,
        boolean live,
        int liveDurationMs
    ) {
        this.appContext = context.getApplicationContext();
        this.callback = callback;
        this.live = live;
        this.liveDurationMs = liveDurationMs;
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
        new ShizukuUserServiceProbe(context, callback, false, 0).start();
    }

    public static void runLive(Context context, int durationMs, Callback callback) {
        if (durationMs < LIVE_MIN_DURATION_MS || durationMs > LIVE_MAX_DURATION_MS) {
            throw new IllegalArgumentException(
                "durationMs must be between " + LIVE_MIN_DURATION_MS + " and " + LIVE_MAX_DURATION_MS
            );
        }
        new ShizukuUserServiceProbe(context, callback, true, durationMs).start();
    }

    private String probeName() {
        return live ? "probe=shizuku-user-service-live-v1" : "probe=shizuku-user-service-v1";
    }

    private void start() {
        try {
            handler.postDelayed(timeout, CONNECT_TIMEOUT_MS);
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable error) {
            handler.removeCallbacks(timeout);
            finish(
                (live ? "probe=shizuku-user-service-live-v1" : "probe=shizuku-user-service-v1")
                    + "\nerror=" + describe(error)
            );
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

    private String runLiveProbe(IShizukuCallMediaService service, int durationMs) throws Exception {
        ParcelFileDescriptor downlinkReadEnd = null;
        ParcelFileDescriptor uplinkWriteEnd = null;
        boolean mediaOk = false;
        StringBuilder result = new StringBuilder();
        try {
            result.append("probe=shizuku-user-service-live-v1\n");
            result.append("mode=live-smoke\n");
            result.append("binder_ping=").append(Shizuku.pingBinder()).append('\n');
            result.append("shizuku_uid=").append(Shizuku.getUid()).append('\n');
            result.append("service_uid=").append(service.getProcessUid()).append('\n');
            result.append("service_pid=").append(service.getProcessPid()).append('\n');
            result.append("sample_rate=").append(SAMPLE_RATE).append('\n');
            result.append("duration_ms=").append(durationMs).append('\n');

            boolean preparedBefore = service.hasPreparedSession();
            boolean activeBefore = service.hasActiveSession();
            result.append("prepared_before=").append(preparedBefore).append('\n');
            result.append("active_before=").append(activeBefore).append('\n');

            service.prepare(SAMPLE_RATE);
            result.append("prepared_after_prepare=")
                .append(service.hasPreparedSession()).append('\n');
            result.append("active_after_prepare=")
                .append(service.hasActiveSession()).append('\n');

            service.startMedia();
            boolean activeAfterStart = service.hasActiveSession();
            boolean heartbeatAfterStart = service.heartbeat();
            result.append("active_after_start=").append(activeAfterStart).append('\n');
            result.append("heartbeat_after_start=").append(heartbeatAfterStart).append('\n');

            downlinkReadEnd = service.takeDownlinkReadEnd();
            uplinkWriteEnd = service.takeUplinkWriteEnd();

            int preDurationMs = durationMs / 2;
            int postDurationMs = durationMs - preDurationMs;
            int preTargetBytes = SAMPLE_RATE * preDurationMs / 1000 * 2;
            int postTargetBytes = SAMPLE_RATE * postDurationMs / 1000 * 2;
            BytePcmMetrics preMetrics = new BytePcmMetrics();
            BytePcmMetrics postMetrics = new BytePcmMetrics();

            try (
                ParcelFileDescriptor.AutoCloseInputStream input =
                    new ParcelFileDescriptor.AutoCloseInputStream(downlinkReadEnd);
                ParcelFileDescriptor.AutoCloseOutputStream output =
                    new ParcelFileDescriptor.AutoCloseOutputStream(uplinkWriteEnd)
            ) {
                downlinkReadEnd = null;
                uplinkWriteEnd = null;

                int preBytesRead = captureWindow(
                    service,
                    input,
                    preMetrics,
                    preTargetBytes,
                    "pre_dtmf"
                );
                appendMetrics(result, "pre_dtmf", preBytesRead, preMetrics);

                byte[] dtmf = dualTonePcm16Le(
                    SAMPLE_RATE,
                    DTMF_DURATION_MS,
                    697,
                    1209,
                    DTMF_AMPLITUDE
                );
                output.write(dtmf);
                output.flush();
                result.append("uplink_dtmf_digit=1\n");
                result.append("uplink_dtmf_duration_ms=").append(DTMF_DURATION_MS).append('\n');
                result.append("uplink_bytes_written=").append(dtmf.length).append('\n');

                if (!service.heartbeat()) {
                    result.append("heartbeat_after_uplink_write=false\n");
                    return result.append("live_parity_ok=false").toString();
                }

                int postBytesRead = captureWindow(
                    service,
                    input,
                    postMetrics,
                    postTargetBytes,
                    "post_dtmf"
                );
                appendMetrics(result, "post_dtmf", postBytesRead, postMetrics);

                boolean activeWithEndpointsOpen = service.hasActiveSession();
                boolean heartbeatWithEndpointsOpen = service.heartbeat();
                result.append("active_with_endpoints_open=")
                    .append(activeWithEndpointsOpen).append('\n');
                result.append("heartbeat_with_endpoints_open=")
                    .append(heartbeatWithEndpointsOpen).append('\n');

                mediaOk = preBytesRead == preTargetBytes
                    && postBytesRead == postTargetBytes
                    && preMetrics.sampleCount == preTargetBytes / 2
                    && postMetrics.sampleCount == postTargetBytes / 2
                    && !preMetrics.hasCarry
                    && !postMetrics.hasCarry
                    && (preMetrics.nonZeroSamples + postMetrics.nonZeroSamples) > 0
                    && activeAfterStart
                    && heartbeatAfterStart
                    && activeWithEndpointsOpen
                    && heartbeatWithEndpointsOpen;
            }

            result.append("media_ok_before_endpoint_close=").append(mediaOk).append('\n');
            service.abortNow();
            boolean preparedAfterAbort = service.hasPreparedSession();
            boolean activeAfterAbort = service.hasActiveSession();
            boolean heartbeatAfterAbort = service.heartbeat();
            result.append("prepared_after_abort=").append(preparedAfterAbort).append('\n');
            result.append("active_after_abort=").append(activeAfterAbort).append('\n');
            result.append("heartbeat_after_abort=").append(heartbeatAfterAbort).append('\n');

            boolean privilegedUid = service.getProcessUid() == 0 || service.getProcessUid() == 2000;
            boolean ok = Shizuku.pingBinder()
                && privilegedUid
                && !preparedBefore
                && !activeBefore
                && mediaOk
                && !preparedAfterAbort
                && !activeAfterAbort
                && !heartbeatAfterAbort;
            result.append("privileged_uid=").append(privilegedUid).append('\n');
            result.append("live_parity_ok=").append(ok);
            return result.toString();
        } finally {
            closeQuietly(downlinkReadEnd);
            closeQuietly(uplinkWriteEnd);
        }
    }

    private static int captureWindow(
        IShizukuCallMediaService service,
        ParcelFileDescriptor.AutoCloseInputStream input,
        BytePcmMetrics metrics,
        int targetBytes,
        String phase
    ) throws Exception {
        int bytesRead = 0;
        byte[] buffer = new byte[640];
        while (bytesRead < targetBytes) {
            int requested = Math.min(buffer.length, targetBytes - bytesRead);
            int read = input.read(buffer, 0, requested);
            if (read < 0) {
                break;
            }
            if (read == 0) {
                continue;
            }
            metrics.accept(buffer, read);
            bytesRead += read;
            if (!service.heartbeat()) {
                break;
            }
        }
        return bytesRead;
    }

    private static void appendMetrics(
        StringBuilder result,
        String prefix,
        int bytesRead,
        BytePcmMetrics metrics
    ) {
        result.append(prefix).append("_bytes_read=").append(bytesRead).append('\n');
        result.append(prefix).append("_samples_read=").append(metrics.sampleCount).append('\n');
        result.append(prefix).append("_non_zero_samples=").append(metrics.nonZeroSamples).append('\n');
        result.append(prefix).append("_peak=").append(metrics.peak).append('\n');
        result.append(prefix).append("_rms=").append(metrics.rms()).append('\n');
        result.append(prefix).append("_half_sample_carry=").append(metrics.hasCarry).append('\n');
    }

    private static byte[] dualTonePcm16Le(
        int sampleRate,
        int durationMs,
        int frequencyHz1,
        int frequencyHz2,
        double amplitude
    ) {
        int frames = sampleRate * durationMs / 1000;
        byte[] result = new byte[frames * 2];
        double scale = Short.MAX_VALUE * amplitude * 0.5;
        for (int i = 0; i < frames; i++) {
            double t = (double) i / sampleRate;
            int value = (int) Math.round(
                scale * (Math.sin(2.0 * Math.PI * frequencyHz1 * t)
                    + Math.sin(2.0 * Math.PI * frequencyHz2 * t))
            );
            result[i * 2] = (byte) (value & 0xff);
            result[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
        }
        return result;
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

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (Throwable ignored) {
            // Probe cleanup path.
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
                result.append(':').append(sanitize(message));
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return result.toString();
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static final class BytePcmMetrics {
        long sampleCount;
        long nonZeroSamples;
        int peak;
        double sumSquares;
        boolean hasCarry;
        int carryByte;

        void accept(byte[] data, int length) {
            int index = 0;
            if (hasCarry && length > 0) {
                acceptSample((short) ((carryByte & 0xff) | (data[0] << 8)));
                hasCarry = false;
                index = 1;
            }
            while (index + 1 < length) {
                acceptSample((short) ((data[index] & 0xff) | (data[index + 1] << 8)));
                index += 2;
            }
            if (index < length) {
                carryByte = data[index] & 0xff;
                hasCarry = true;
            }
        }

        void acceptSample(short sample) {
            int value = sample;
            int absolute = Math.abs(value);
            sampleCount++;
            if (value != 0) {
                nonZeroSamples++;
            }
            if (absolute > peak) {
                peak = absolute;
            }
            sumSquares += (double) value * value;
        }

        double rms() {
            return sampleCount == 0 ? 0.0 : Math.sqrt(sumSquares / sampleCount);
        }
    }
}
