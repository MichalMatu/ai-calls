package pl.michalmatu.aicallbridge;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.AudioManager;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import java.lang.reflect.Method;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungCallMediaSessionController;

/** Bounded regression harness for the bidirectional helper media controller. */
public final class BidirectionalMediaProbe {
    private static final int SAMPLE_RATE = 16_000;
    private static final int LIVE_MIN_DURATION_MS = 500;
    private static final int LIVE_MAX_DURATION_MS = 5_000;
    private static final int DTMF_DURATION_MS = 300;
    private static final double DTMF_AMPLITUDE = 0.10;

    private BidirectionalMediaProbe() {}

    public static void main(String[] args) {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        int exitCode;
        try {
            if (args.length == 1 && "prepare-abort-offcall".equals(args[0])) {
                exitCode = runPrepareAbortOffcall();
            } else if (args.length == 1 && "start-offcall".equals(args[0])) {
                exitCode = runStartOffcall();
            } else if (args.length == 2 && "live-smoke".equals(args[0])) {
                int durationMs = parseDurationMs(args[1]);
                exitCode = runLiveSmoke(durationMs);
            } else {
                System.out.println(
                    "usage=BidirectionalMediaProbe prepare-abort-offcall | start-offcall | "
                        + "live-smoke <500..5000ms>"
                );
                exitCode = 2;
            }
        } catch (Throwable error) {
            printError("bidirectional_media_probe", error);
            exitCode = 3;
        }

        System.out.flush();
        System.err.flush();
        System.exit(exitCode);
    }

    private static int runPrepareAbortOffcall() {
        SamsungCallMediaSessionController controller = new SamsungCallMediaSessionController();
        try {
            System.out.println("probe=bidirectional-media-v3");
            System.out.println("mode=prepare-abort-offcall");
            System.out.println("uid=" + Process.myUid());
            System.out.println("sample_rate=" + SAMPLE_RATE);

            controller.prepare(SAMPLE_RATE);
            System.out.println("prepared_after_prepare=" + controller.hasPreparedSession());
            System.out.println("active_after_prepare=" + controller.hasActiveSession());
            System.out.println("heartbeat_after_prepare=" + controller.heartbeat());

            controller.abortNow();
            System.out.println("prepared_after_abort=" + controller.hasPreparedSession());
            System.out.println("active_after_abort=" + controller.hasActiveSession());
            System.out.println("heartbeat_after_abort=" + controller.heartbeat());

            return !controller.hasPreparedSession() && !controller.hasActiveSession() ? 0 : 4;
        } finally {
            controller.abortNow();
        }
    }

    private static int runStartOffcall() throws Exception {
        SamsungCallMediaSessionController controller = new SamsungCallMediaSessionController();
        SamsungCallMediaSessionController.Endpoints endpoints = null;
        try {
            System.out.println("probe=bidirectional-media-v3");
            System.out.println("mode=start-offcall");
            System.out.println("uid=" + Process.myUid());
            System.out.println("sample_rate=" + SAMPLE_RATE);

            // Keep the proven Samsung ordering: create VOICE_DOWNLINK before either Context.
            controller.prepare(SAMPLE_RATE);
            System.out.println("prepared_before_contexts=" + controller.hasPreparedSession());

            ContextPair contexts = contexts();
            AudioManager downlinkAudioManager =
                (AudioManager) contexts.system.getSystemService(Context.AUDIO_SERVICE);
            AudioManager uplinkAudioManager =
                (AudioManager) contexts.shell.getSystemService(Context.AUDIO_SERVICE);
            System.out.println("downlink_context_package=" + contexts.system.getPackageName());
            System.out.println("downlink_context_attribution="
                + contexts.system.getAttributionSource().getPackageName());
            System.out.println("uplink_context_package=" + contexts.shell.getPackageName());
            System.out.println("uplink_context_attribution="
                + contexts.shell.getAttributionSource().getPackageName());
            System.out.println("downlink_audio_mode=" + downlinkAudioManager.getMode());
            System.out.println("uplink_audio_mode=" + uplinkAudioManager.getMode());

            boolean rejected = false;
            try {
                endpoints = controller.start(contexts.system, contexts.shell);
            } catch (IllegalStateException expected) {
                rejected = true;
                System.out.println("offcall_start_rejected=true");
                System.out.println("offcall_start_error=" + sanitize(expected.getMessage()));
            }
            if (!rejected) {
                System.out.println("offcall_start_rejected=false");
                return 5;
            }

            System.out.println("prepared_after_rollback=" + controller.hasPreparedSession());
            System.out.println("active_after_rollback=" + controller.hasActiveSession());
            System.out.println("heartbeat_after_rollback=" + controller.heartbeat());

            // Do not re-prepare in this direct-shell process after Context/AudioManager creation.
            // On the target S22 firmware VOICE_DOWNLINK construction is proven to require the
            // opposite initialization order. Reuse through a persistent UserService is a separate
            // Milestone C parity question, not an off-call rollback invariant.
            controller.abortNow();
            System.out.println("prepared_after_final_abort=" + controller.hasPreparedSession());
            System.out.println("active_after_final_abort=" + controller.hasActiveSession());

            return !controller.hasPreparedSession()
                && !controller.hasActiveSession()
                && !controller.heartbeat()
                ? 0
                : 6;
        } finally {
            if (endpoints != null) {
                endpoints.close();
            }
            controller.abortNow();
        }
    }

    private static int runLiveSmoke(int durationMs) throws Exception {
        SamsungCallMediaSessionController controller = new SamsungCallMediaSessionController();
        SamsungCallMediaSessionController.Endpoints endpoints = null;
        ParcelFileDescriptor downlinkReadEnd = null;
        ParcelFileDescriptor uplinkWriteEnd = null;
        try {
            System.out.println("probe=bidirectional-media-v3");
            System.out.println("mode=live-smoke");
            System.out.println("uid=" + Process.myUid());
            System.out.println("sample_rate=" + SAMPLE_RATE);
            System.out.println("duration_ms=" + durationMs);

            // Samsung S22 proof invariant: VOICE_DOWNLINK must be created before Context work.
            controller.prepare(SAMPLE_RATE);
            System.out.println("prepared_before_contexts=" + controller.hasPreparedSession());

            ContextPair contexts = contexts();
            AudioManager downlinkAudioManager =
                (AudioManager) contexts.system.getSystemService(Context.AUDIO_SERVICE);
            AudioManager uplinkAudioManager =
                (AudioManager) contexts.shell.getSystemService(Context.AUDIO_SERVICE);
            System.out.println("downlink_context_package=" + contexts.system.getPackageName());
            System.out.println("downlink_context_attribution="
                + contexts.system.getAttributionSource().getPackageName());
            System.out.println("uplink_context_package=" + contexts.shell.getPackageName());
            System.out.println("uplink_context_attribution="
                + contexts.shell.getAttributionSource().getPackageName());
            System.out.println("downlink_audio_mode=" + downlinkAudioManager.getMode());
            System.out.println("uplink_audio_mode=" + uplinkAudioManager.getMode());
            if (
                downlinkAudioManager.getMode() != AudioManager.MODE_IN_CALL
                    || uplinkAudioManager.getMode() != AudioManager.MODE_IN_CALL
            ) {
                System.out.println("call_guard=blocked_not_in_call");
                return 7;
            }

            endpoints = controller.start(contexts.system, contexts.shell);
            System.out.println("active_after_start=" + controller.hasActiveSession());
            System.out.println("heartbeat_after_start=" + controller.heartbeat());

            downlinkReadEnd = endpoints.takeDownlinkReadEnd();
            uplinkWriteEnd = endpoints.takeUplinkWriteEnd();

            PcmMetrics metrics = new PcmMetrics();
            int targetBytes = SAMPLE_RATE * durationMs / 1000 * 2;
            int bytesRead = 0;
            int uplinkBytesWritten;
            try (
                ParcelFileDescriptor.AutoCloseInputStream input =
                    new ParcelFileDescriptor.AutoCloseInputStream(downlinkReadEnd);
                ParcelFileDescriptor.AutoCloseOutputStream output =
                    new ParcelFileDescriptor.AutoCloseOutputStream(uplinkWriteEnd)
            ) {
                downlinkReadEnd = null;
                uplinkWriteEnd = null;

                byte[] dtmf = dualTonePcm16Le(
                    SAMPLE_RATE,
                    DTMF_DURATION_MS,
                    697,
                    1209,
                    DTMF_AMPLITUDE
                );
                output.write(dtmf);
                output.flush();
                uplinkBytesWritten = dtmf.length;
                System.out.println("uplink_dtmf_digit=1");
                System.out.println("uplink_dtmf_duration_ms=" + DTMF_DURATION_MS);
                System.out.println("uplink_bytes_written=" + uplinkBytesWritten);

                byte[] buffer = new byte[640]; // 20 ms mono PCM16LE at 16 kHz.
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
                    if (!controller.heartbeat()) {
                        System.out.println("heartbeat_during_capture=false");
                        return 8;
                    }
                }
            }

            System.out.println("bytes_read=" + bytesRead);
            System.out.println("samples_read=" + metrics.sampleCount);
            System.out.println("non_zero_samples=" + metrics.nonZeroSamples);
            System.out.println("peak=" + metrics.peak);
            System.out.println("rms=" + metrics.rms());
            System.out.println("half_sample_carry=" + metrics.hasCarry);
            System.out.println("active_before_abort=" + controller.hasActiveSession());
            System.out.println("heartbeat_before_abort=" + controller.heartbeat());

            controller.abortNow();
            System.out.println("prepared_after_abort=" + controller.hasPreparedSession());
            System.out.println("active_after_abort=" + controller.hasActiveSession());
            System.out.println("heartbeat_after_abort=" + controller.heartbeat());

            return bytesRead == targetBytes
                && metrics.sampleCount == targetBytes / 2
                && !metrics.hasCarry
                && !controller.hasActiveSession()
                ? 0
                : 9;
        } finally {
            closeQuietly(downlinkReadEnd);
            closeQuietly(uplinkWriteEnd);
            if (endpoints != null) {
                endpoints.close();
            }
            controller.abortNow();
        }
    }

    private static ContextPair contexts() throws Exception {
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
        Context shell = (Context) createAppContext.invoke(null, thread, loadedApk, "com.android.shell");
        return new ContextPair(system, shell);
    }

    private static int parseDurationMs(String raw) {
        final int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("durationMs must be an integer", error);
        }
        if (value < LIVE_MIN_DURATION_MS || value > LIVE_MAX_DURATION_MS) {
            throw new IllegalArgumentException(
                "durationMs must be between " + LIVE_MIN_DURATION_MS + " and " + LIVE_MAX_DURATION_MS
            );
        }
        return value;
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

    private static void printError(String label, Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        System.out.println(
            label + "=error:" + root.getClass().getSimpleName() + ":" + sanitize(root.getMessage())
        );
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static final class PcmMetrics {
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

    private static final class ContextPair {
        final Context system;
        final Context shell;

        ContextPair(Context system, Context shell) {
            this.system = system;
            this.shell = shell;
        }
    }
}
