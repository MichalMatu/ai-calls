package pl.michalmatu.aicallbridge;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Looper;
import android.os.Process;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Samsung Phase 1C probe for the system CALL_ASSISTANT -> Telephony Tx route.
 *
 * This is intentionally separate from ShellAudioProbe's generic USAGE_MEDIA experiment so
 * the generic Phase 1B failure remains reproducible and unmodified.
 */
public final class CallAssistantAudioProbe {
    private static final int CALL_ASSISTANT_USAGE = 17;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int CHANNEL_COUNT = 2;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
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
                exitCode = run(false, sampleRate, 0, 0, 0.0);
            } else if (args.length == 5 && "play-tone".equals(args[0])) {
                int sampleRate = parseSampleRate(args[1]);
                int durationMs = parseInt(args[2], "durationMs", 1, MAX_DURATION_MS);
                int frequencyHz = parseInt(args[3], "frequencyHz", 1, MAX_FREQUENCY_HZ);
                if (frequencyHz * 2 >= sampleRate) {
                    throw new IllegalArgumentException("frequencyHz must be below Nyquist");
                }
                double amplitude = parseAmplitude(args[4]);
                exitCode = run(true, sampleRate, durationMs, frequencyHz, amplitude);
            } else {
                System.out.println(
                    "usage=CallAssistantAudioProbe construct <16000|48000> | "
                        + "play-tone <16000|48000> <durationMs> <frequencyHz> <amplitude>"
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
        double amplitude
    ) throws Exception {
        AudioTrack track = null;
        int warmupFrames = sampleRate / 50; // 20 ms.
        System.out.println("probe=call-assistant-audio-v2");
        System.out.println("uid=" + Process.myUid());
        System.out.println("pid=" + Process.myPid());
        System.out.println("mode=" + (playTone ? "play-tone" : "construct"));
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

            AudioDeviceInfo telephonySink = findTelephonySink(audioManager);
            System.out.println("telephony_sink_present=" + (telephonySink != null));
            if (telephonySink != null) {
                System.out.println(
                    "telephony_sink=id:" + telephonySink.getId()
                        + ",type:" + telephonySink.getType()
                        + ",product:" + telephonySink.getProductName()
                );
            }

            AudioAttributes attributes = buildCallAssistantAttributes();
            System.out.println("attributes_public_usage=" + attributes.getUsage());
            int systemUsage = getSystemUsage(attributes);
            System.out.println("attributes_system_usage=" + systemUsage);
            System.out.println("attributes_content_type=" + attributes.getContentType());
            if (systemUsage != CALL_ASSISTANT_USAGE) {
                System.out.println("attributes_guard=unexpected_system_usage");
                return 5;
            }

            int minBuffer = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_OUT, ENCODING);
            int bufferSize = minBuffer > 0 ? Math.max(minBuffer * 2, 8192) : 8192;
            System.out.println("min_buffer_bytes=" + minBuffer);
            System.out.println("buffer_bytes=" + bufferSize);

            track = new AudioTrack.Builder()
                .setContext(context)
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    new AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(sampleRate)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

            System.out.println("track_state=" + track.getState());
            System.out.println("track_session_id=" + track.getAudioSessionId());
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                return 6;
            }

            if (!playTone) {
                System.out.println("construction=success");
                return 0;
            }

            track.play();
            System.out.println("play_state=" + track.getPlayState());
            if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                return 7;
            }

            short[] warmup = new short[warmupFrames * CHANNEL_COUNT];
            int warmupWritten = writeFully(track, warmup);
            System.out.println("warmup_samples_written=" + warmupWritten);
            System.out.println("warmup_frames=" + warmupFrames);
            if (warmupWritten != warmup.length) {
                return 8;
            }

            Thread.sleep(30L);
            AudioDeviceInfo routedDevice = track.getRoutedDevice();
            if (routedDevice == null) {
                System.out.println("routed_device=none");
                return 9;
            }
            System.out.println(
                "routed_device=id:" + routedDevice.getId()
                    + ",type:" + routedDevice.getType()
                    + ",product:" + routedDevice.getProductName()
            );
            if (routedDevice.getType() != AudioDeviceInfo.TYPE_TELEPHONY) {
                System.out.println("route_guard=blocked_non_telephony");
                return 10;
            }
            System.out.println("route_guard=telephony_confirmed");

            short[] mono = ToneGenerator.sinePcm16(sampleRate, durationMs, frequencyHz, amplitude);
            short[] stereo = interleaveStereo(mono);
            System.out.println("tone_duration_ms=" + durationMs);
            System.out.println("tone_frequency_hz=" + frequencyHz);
            System.out.println("tone_amplitude=" + amplitude);
            System.out.println("tone_frames=" + mono.length);

            int toneWritten = writeFully(track, stereo);
            System.out.println("tone_samples_written=" + toneWritten);
            if (toneWritten != stereo.length) {
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
                try {
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop();
                    }
                } catch (Throwable ignored) {
                    // Preserve the primary probe result.
                }
                try {
                    track.flush();
                } catch (Throwable ignored) {
                    // Preserve the primary probe result.
                }
                try {
                    track.release();
                } catch (Throwable ignored) {
                    // Preserve the primary probe result.
                }
            }
        }
    }

    private static int getSystemUsage(AudioAttributes attributes) throws Exception {
        Method getSystemUsage = AudioAttributes.class.getDeclaredMethod("getSystemUsage");
        getSystemUsage.setAccessible(true);
        return ((Integer) getSystemUsage.invoke(attributes)).intValue();
    }

    private static AudioAttributes buildCallAssistantAttributes() throws Exception {
        AudioAttributes.Builder builder = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);

        try {
            Method setSystemUsage = AudioAttributes.Builder.class.getDeclaredMethod("setSystemUsage", int.class);
            setSystemUsage.setAccessible(true);
            setSystemUsage.invoke(builder, CALL_ASSISTANT_USAGE);
            System.out.println("system_usage_builder=method:setSystemUsage");
        } catch (ReflectiveOperationException | RuntimeException methodError) {
            System.out.println("system_usage_builder_method_failed=" + sanitize(methodError.toString()));
            Field usageField = AudioAttributes.Builder.class.getDeclaredField("mUsage");
            usageField.setAccessible(true);
            usageField.setInt(builder, CALL_ASSISTANT_USAGE);
            System.out.println("system_usage_builder=field:mUsage");
        }

        return builder.build();
    }

    private static AudioDeviceInfo findTelephonySink(AudioManager audioManager) {
        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getType() == AudioDeviceInfo.TYPE_TELEPHONY && device.isSink()) {
                return device;
            }
        }
        return null;
    }

    private static int writeFully(AudioTrack track, short[] samples) {
        int total = 0;
        while (total < samples.length) {
            int written = track.write(samples, total, samples.length - total, AudioTrack.WRITE_BLOCKING);
            if (written <= 0) {
                return written < 0 ? written : total;
            }
            total += written;
        }
        return total;
    }

    private static short[] interleaveStereo(short[] mono) {
        short[] stereo = new short[mono.length * CHANNEL_COUNT];
        for (int i = 0; i < mono.length; i++) {
            stereo[i * 2] = mono[i];
            stereo[i * 2 + 1] = mono[i];
        }
        return stereo;
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
        Context system = systemContext();
        return system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
    }

    private static Context systemContext() throws Exception {
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThread.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object thread = systemMain.invoke(null);
        Method getSystemContext = activityThread.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        return (Context) getSystemContext.invoke(thread);
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
