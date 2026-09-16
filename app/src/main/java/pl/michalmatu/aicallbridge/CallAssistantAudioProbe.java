package pl.michalmatu.aicallbridge;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Looper;
import android.os.Process;

import java.lang.reflect.Method;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungCallAssistantTrack;

/**
 * Samsung Phase 1C regression probe for the proven CALL_ASSISTANT -> Telephony Tx route.
 *
 * <p>The actual AudioTrack implementation lives in privileged-helper. This class remains only a
 * bounded command-line harness so the physical proof can be rerun without duplicating the
 * production uplink primitive.</p>
 */
public final class CallAssistantAudioProbe {
    private static final int MAX_DURATION_MS = 1_000;
    private static final int MAX_FREQUENCY_HZ = 8_000;
    private static final double MAX_AMPLITUDE = 0.10;

    private CallAssistantAudioProbe() {}

    public static void main(String[] args) {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        int exitCode;
        try {
            if (args.length == 2 && "construct".equals(args[0])) {
                int sampleRate = parseSampleRate(args[1]);
                exitCode = run(false, sampleRate, 0, 0, 0, 0.0);
            } else if (args.length == 5 && "play-tone".equals(args[0])) {
                int sampleRate = parseSampleRate(args[1]);
                int durationMs = parseInt(args[2], "durationMs", 1, MAX_DURATION_MS);
                int frequencyHz = parseInt(args[3], "frequencyHz", 1, MAX_FREQUENCY_HZ);
                if (frequencyHz * 2 >= sampleRate) {
                    throw new IllegalArgumentException("frequencyHz must be below Nyquist");
                }
                double amplitude = parseAmplitude(args[4]);
                exitCode = run(true, sampleRate, durationMs, frequencyHz, 0, amplitude);
            } else if (args.length == 6 && "play-dual-tone".equals(args[0])) {
                int sampleRate = parseSampleRate(args[1]);
                int durationMs = parseInt(args[2], "durationMs", 1, MAX_DURATION_MS);
                int frequencyHz1 = parseInt(args[3], "frequencyHz1", 1, MAX_FREQUENCY_HZ);
                int frequencyHz2 = parseInt(args[4], "frequencyHz2", 1, MAX_FREQUENCY_HZ);
                if (frequencyHz1 * 2 >= sampleRate || frequencyHz2 * 2 >= sampleRate) {
                    throw new IllegalArgumentException("frequencies must be below Nyquist");
                }
                double amplitude = parseAmplitude(args[5]);
                exitCode = run(true, sampleRate, durationMs, frequencyHz1, frequencyHz2, amplitude);
            } else {
                System.out.println(
                    "usage=CallAssistantAudioProbe construct <16000|48000> | "
                        + "play-tone <16000|48000> <durationMs> <frequencyHz> <amplitude> | "
                        + "play-dual-tone <16000|48000> <durationMs> <frequencyHz1> <frequencyHz2> <amplitude>"
                );
                exitCode = 2;
            }
        } catch (Throwable error) {
            printError("call_assistant_probe", error);
            exitCode = 3;
        }

        System.out.flush();
        System.err.flush();
        System.exit(exitCode);
    }

    private static int run(
        boolean playTone,
        int sampleRate,
        int durationMs,
        int frequencyHz,
        int secondFrequencyHz,
        double amplitude
    ) throws Exception {
        SamsungCallAssistantTrack track = null;
        System.out.println("probe=call-assistant-audio-v3");
        System.out.println("uid=" + Process.myUid());
        System.out.println("pid=" + Process.myPid());
        System.out.println("mode=" + (playTone ? "play-tone" : "construct"));
        System.out.println("backend=SamsungCallAssistantTrack");
        System.out.println("system_usage=USAGE_CALL_ASSISTANT(17)");
        System.out.println("sample_rate=" + sampleRate);
        System.out.println("channels=stereo");

        try {
            Context context = shellContext();
            System.out.println("context_package=" + context.getPackageName());
            System.out.println("context_op_package=" + context.getOpPackageName());
            System.out.println("context_attribution_package=" + context.getAttributionSource().getPackageName());
            printPermission(context, "MODIFY_AUDIO_ROUTING", "android.permission.MODIFY_AUDIO_ROUTING");
            printPermission(context, "MODIFY_PHONE_STATE", "android.permission.MODIFY_PHONE_STATE");

            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int audioMode = audioManager.getMode();
            System.out.println("audio_mode=" + audioMode);
            if (playTone && audioMode != AudioManager.MODE_IN_CALL) {
                System.out.println("call_guard=blocked_not_in_call");
                return 4;
            }

            AudioDeviceInfo telephonySink = SamsungCallAssistantTrack.findTelephonySink(audioManager);
            System.out.println("telephony_sink_present=" + (telephonySink != null));
            if (telephonySink != null) {
                System.out.println(
                    "telephony_sink=id:" + telephonySink.getId()
                        + ",type:" + telephonySink.getType()
                        + ",product:" + telephonySink.getProductName()
                );
            }

            AudioAttributes attributes = SamsungCallAssistantTrack.buildCallAssistantAttributes();
            System.out.println("attributes_public_usage=" + attributes.getUsage());
            int systemUsage = SamsungCallAssistantTrack.getSystemUsage(attributes);
            System.out.println("attributes_system_usage=" + systemUsage);
            System.out.println("attributes_content_type=" + attributes.getContentType());
            if (systemUsage != SamsungCallAssistantTrack.CALL_ASSISTANT_USAGE) {
                System.out.println("attributes_guard=unexpected_system_usage");
                return 5;
            }

            track = SamsungCallAssistantTrack.open(context, sampleRate);
            System.out.println("track_state=" + track.getState());
            System.out.println("track_session_id=" + track.getAudioSessionId());

            if (!playTone) {
                System.out.println("construction=success");
                return 0;
            }

            AudioDeviceInfo routedDevice = track.startAndConfirmTelephonyRoute();
            System.out.println("play_state=" + track.getPlayState());
            int warmupFrames = track.getWarmupFrames();
            System.out.println("warmup_samples_written=" + (warmupFrames * 2));
            System.out.println("warmup_frames=" + warmupFrames);
            System.out.println(
                "routed_device=id:" + routedDevice.getId()
                    + ",type:" + routedDevice.getType()
                    + ",product:" + routedDevice.getProductName()
            );
            System.out.println("route_guard=telephony_confirmed");

            short[] mono = secondFrequencyHz > 0
                ? ToneGenerator.dualSinePcm16(
                    sampleRate, durationMs, frequencyHz, secondFrequencyHz, amplitude
                )
                : ToneGenerator.sinePcm16(sampleRate, durationMs, frequencyHz, amplitude);
            byte[] pcm = pcm16Le(mono);
            System.out.println("tone_duration_ms=" + durationMs);
            System.out.println("tone_frequency_hz_1=" + frequencyHz);
            if (secondFrequencyHz > 0) {
                System.out.println("tone_frequency_hz_2=" + secondFrequencyHz);
            }
            System.out.println("tone_amplitude=" + amplitude);
            System.out.println("tone_frames=" + mono.length);

            int framesWritten = track.writeMonoPcm16Le(pcm, 0, pcm.length);
            System.out.println("tone_samples_written=" + (framesWritten * 2));
            if (framesWritten != mono.length) {
                return 11;
            }

            int expectedFrames = warmupFrames + mono.length;
            long deadline = System.nanoTime() + (durationMs + 750L) * 1_000_000L;
            int playbackHead = track.getPlaybackHeadPosition();
            while (playbackHead < expectedFrames && System.nanoTime() < deadline) {
                Thread.sleep(10L);
                playbackHead = track.getPlaybackHeadPosition();
            }
            System.out.println("playback_head_frames=" + playbackHead);
            System.out.println("expected_frames=" + expectedFrames);
            return playbackHead > warmupFrames ? 0 : 12;
        } finally {
            if (track != null) {
                track.abortNow();
            }
        }
    }

    private static byte[] pcm16Le(short[] mono) {
        byte[] result = new byte[mono.length * 2];
        for (int i = 0; i < mono.length; i++) {
            int value = mono[i];
            result[i * 2] = (byte) (value & 0xff);
            result[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
        }
        return result;
    }

    private static int parseSampleRate(String raw) {
        int value = parseInt(raw, "sampleRate", 1, 48_000);
        if (value != 16_000 && value != 48_000) {
            throw new IllegalArgumentException("sampleRate must be 16000 or 48000");
        }
        return value;
    }

    private static int parseInt(String raw, String label, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " must be an integer", error);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static double parseAmplitude(String raw) {
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("amplitude must be numeric", error);
        }
        if (!Double.isFinite(value) || value <= 0.0 || value > MAX_AMPLITUDE) {
            throw new IllegalArgumentException("amplitude must be > 0 and <= " + MAX_AMPLITUDE);
        }
        return value;
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

    private static void printPermission(Context context, String label, String permission) {
        int result = context.checkPermission(permission, Process.myPid(), Process.myUid());
        System.out.println(
            "permission_" + label + "="
                + (result == PackageManager.PERMISSION_GRANTED ? "granted" : "denied")
        );
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
}
