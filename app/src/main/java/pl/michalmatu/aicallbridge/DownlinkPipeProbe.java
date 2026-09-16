package pl.michalmatu.aicallbridge;

import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import java.io.FileInputStream;
import java.lang.reflect.Method;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungDownlinkPipeSession;

/** Bounded regression harness for the production VOICE_DOWNLINK -> pipe path. */
public final class DownlinkPipeProbe {
    private static final int SAMPLE_RATE = 16_000;
    private static final int MAX_DURATION_MS = 5_000;

    private DownlinkPipeProbe() {}

    public static void main(String[] args) {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        int exitCode;
        try {
            if (args.length == 1 && "open-abort-offcall".equals(args[0])) {
                exitCode = runOpenAbortOffcall();
            } else if (args.length == 1 && "start-offcall".equals(args[0])) {
                exitCode = runStartOffcall();
            } else if (args.length == 2 && "capture-live".equals(args[0])) {
                int durationMs = parseDuration(args[1]);
                exitCode = runCaptureLive(durationMs);
            } else {
                System.out.println(
                    "usage=DownlinkPipeProbe open-abort-offcall | start-offcall | capture-live <durationMs>"
                );
                exitCode = 2;
            }
        } catch (Throwable error) {
            printError("downlink_pipe_probe", error);
            exitCode = 3;
        }

        System.out.flush();
        System.err.flush();
        System.exit(exitCode);
    }

    private static int runOpenAbortOffcall() throws Exception {
        Context context = shellContext();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        System.out.println("probe=downlink-pipe-v1");
        System.out.println("mode=open-abort-offcall");
        System.out.println("uid=" + Process.myUid());
        System.out.println("audio_mode=" + audioManager.getMode());

        SamsungDownlinkPipeSession session = SamsungDownlinkPipeSession.open(context, SAMPLE_RATE);
        ParcelFileDescriptor reader = null;
        try {
            reader = session.takeReadEnd();
            System.out.println("read_end_acquired=true");
            session.abortNow();
            System.out.println("terminated_after_abort=" + session.isTerminated());
            System.out.println("aborted_after_abort=" + session.wasAborted());
            return session.isTerminated() && session.wasAborted() ? 0 : 4;
        } finally {
            closeQuietly(reader);
            session.abortNow();
        }
    }

    private static int runStartOffcall() throws Exception {
        Context context = shellContext();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        System.out.println("probe=downlink-pipe-v1");
        System.out.println("mode=start-offcall");
        System.out.println("uid=" + Process.myUid());
        System.out.println("audio_mode=" + audioManager.getMode());

        SamsungDownlinkPipeSession session = SamsungDownlinkPipeSession.open(context, SAMPLE_RATE);
        ParcelFileDescriptor reader = null;
        try {
            reader = session.takeReadEnd();
            boolean rejected = false;
            try {
                session.start();
            } catch (IllegalStateException expected) {
                rejected = true;
                System.out.println("offcall_start_rejected=true");
                System.out.println("offcall_start_error=" + sanitize(expected.getMessage()));
            }
            System.out.println("terminated_after_start_failure=" + session.isTerminated());
            System.out.println("terminal_failure_present=" + (session.getTerminalFailure() != null));
            return rejected && session.isTerminated() ? 0 : 5;
        } finally {
            closeQuietly(reader);
            session.abortNow();
        }
    }

    private static int runCaptureLive(int durationMs) throws Exception {
        Context context = shellContext();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        System.out.println("probe=downlink-pipe-v1");
        System.out.println("mode=capture-live");
        System.out.println("uid=" + Process.myUid());
        System.out.println("audio_mode=" + audioManager.getMode());
        System.out.println("sample_rate=" + SAMPLE_RATE);
        System.out.println("duration_ms=" + durationMs);

        if (audioManager.getMode() != AudioManager.MODE_IN_CALL) {
            System.out.println("call_guard=blocked_not_in_call");
            return 6;
        }

        SamsungDownlinkPipeSession session = SamsungDownlinkPipeSession.open(context, SAMPLE_RATE);
        ParcelFileDescriptor reader = null;
        try {
            reader = session.takeReadEnd();
            session.start();
            System.out.println("session_started=" + session.isStarted());
            System.out.println("route_guard=telephony_confirmed_by_session_start");

            long targetBytes = (long) SAMPLE_RATE * durationMs / 1000L * 2L;
            PcmMetrics metrics = new PcmMetrics();
            try (FileInputStream input = new FileInputStream(reader.getFileDescriptor())) {
                reader = null; // FileInputStream owns the descriptor now.
                byte[] buffer = new byte[2048];
                int carry = -1;
                long bytesRead = 0L;
                while (bytesRead < targetBytes) {
                    int wanted = (int) Math.min(buffer.length, targetBytes - bytesRead);
                    int count = input.read(buffer, 0, wanted);
                    if (count < 0) {
                        break;
                    }
                    if (count == 0) {
                        continue;
                    }
                    bytesRead += count;
                    int index = 0;
                    if (carry >= 0) {
                        int high = buffer[0];
                        metrics.accept((short) ((high << 8) | carry));
                        carry = -1;
                        index = 1;
                    }
                    while (index + 1 < count) {
                        int low = buffer[index] & 0xff;
                        int high = buffer[index + 1];
                        metrics.accept((short) ((high << 8) | low));
                        index += 2;
                    }
                    if (index < count) {
                        carry = buffer[index] & 0xff;
                    }
                }
                System.out.println("bytes_read=" + bytesRead);
                System.out.println("samples_read=" + metrics.samples);
                System.out.println("non_zero_samples=" + metrics.nonZero);
                System.out.println("peak=" + metrics.peak);
                System.out.println("rms=" + metrics.rms());
                System.out.println("half_sample_carry=" + (carry >= 0));
                if (bytesRead != targetBytes || carry >= 0) {
                    return 7;
                }
            }

            boolean terminated = session.awaitTerminated(1_000L);
            System.out.println("terminated_after_reader_close=" + terminated);
            if (!terminated) {
                session.abortNow();
                return 8;
            }
            System.out.println("terminal_failure_present=" + (session.getTerminalFailure() != null));
            return session.getTerminalFailure() == null ? 0 : 9;
        } finally {
            closeQuietly(reader);
            session.abortNow();
        }
    }

    private static Context shellContext() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThreadClass.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object thread = systemMain.invoke(null);

        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        Context system = (Context) getSystemContext.invoke(thread);
        android.content.pm.ApplicationInfo shellInfo = system.getPackageManager()
            .getApplicationInfo("com.android.shell", 0);

        Method getPackageInfoNoCheck = activityThreadClass.getDeclaredMethod(
            "getPackageInfoNoCheck", android.content.pm.ApplicationInfo.class
        );
        getPackageInfoNoCheck.setAccessible(true);
        Object loadedApk = getPackageInfoNoCheck.invoke(thread, shellInfo);

        Class<?> loadedApkClass = Class.forName("android.app.LoadedApk");
        Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
        Method createAppContext = contextImplClass.getDeclaredMethod(
            "createAppContext", activityThreadClass, loadedApkClass, String.class
        );
        createAppContext.setAccessible(true);
        return (Context) createAppContext.invoke(null, thread, loadedApk, "com.android.shell");
    }

    private static int parseDuration(String raw) {
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("durationMs must be an integer", error);
        }
        if (value < 100 || value > MAX_DURATION_MS) {
            throw new IllegalArgumentException("durationMs must be between 100 and " + MAX_DURATION_MS);
        }
        return value;
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (Exception ignored) {
            // Diagnostic cleanup path.
        }
    }

    private static void printError(String label, Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        System.out.println(label + "=error:" + root.getClass().getSimpleName() + ":" + sanitize(root.getMessage()));
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static final class PcmMetrics {
        long samples;
        long nonZero;
        int peak;
        double sumSquares;

        void accept(short sample) {
            samples++;
            if (sample != 0) {
                nonZero++;
            }
            int abs = Math.abs((int) sample);
            if (abs > peak) {
                peak = abs;
            }
            sumSquares += (double) sample * sample;
        }

        double rms() {
            return samples == 0 ? 0.0 : Math.sqrt(sumSquares / samples);
        }
    }
}
