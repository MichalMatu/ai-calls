package pl.michalmatu.aicallbridge;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Looper;
import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Minimal command-line probe intended to be launched through adb shell + app_process.
 *
 * It does not depend on the app process identity. The goal is to measure what UID 2000
 * (shell), the privilege level later represented by a Shizuku UserService, can actually
 * initialize on the target Samsung firmware.
 */
public final class ShellAudioProbe {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int ROUTE_WARMUP_SAMPLES = 160;

    private ShellAudioProbe() {}

    public static void main(String[] args) {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        final ProbeArguments parsed;
        try {
            parsed = ProbeArguments.parse(args);
        } catch (IllegalArgumentException error) {
            System.out.println("probe=shell-audio-v5");
            System.out.println("argument_error=" + sanitize(error.getMessage()));
            flushAndExit(2);
            return;
        }

        System.out.println("probe=shell-audio-v5");
        System.out.println("uid=" + Process.myUid());
        System.out.println("pid=" + Process.myPid());
        System.out.println("mode=" + parsed.mode());

        int exitCode = switch (parsed.mode()) {
            case INVENTORY -> {
                runInventory();
                yield 0;
            }
            case CAPTURE_DOWNLINK -> captureDownlink(parsed.durationMs(), parsed.outputPath());
            case INJECT_TONE -> injectTone(parsed.durationMs(), parsed.frequencyHz(), parsed.amplitude());
        };

        flushAndExit(exitCode);
    }

    private static void runInventory() {
        probeRecordSource("VOICE_CALL", MediaRecorder.AudioSource.VOICE_CALL);
        probeRecordSource("VOICE_DOWNLINK", MediaRecorder.AudioSource.VOICE_DOWNLINK);
        probeRecordSource("VOICE_UPLINK", MediaRecorder.AudioSource.VOICE_UPLINK);

        try {
            Context context = systemContext();
            printPermission(context, "CAPTURE_AUDIO_OUTPUT", "android.permission.CAPTURE_AUDIO_OUTPUT");
            printPermission(context, "MODIFY_AUDIO_ROUTING", "android.permission.MODIFY_AUDIO_ROUTING");
            printPermission(context, "MODIFY_PHONE_STATE", "android.permission.MODIFY_PHONE_STATE");

            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            System.out.println("audio_mode=" + audioManager.getMode());

            AudioDeviceInfo telephonySink = null;
            AudioDeviceInfo telephonySource = null;
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS | AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_TELEPHONY) {
                    String direction = device.isSink() ? "sink" : (device.isSource() ? "source" : "neither");
                    System.out.println(
                        "telephony_device=id=" + device.getId()
                            + ",direction=" + direction
                            + ",product=" + device.getProductName()
                    );
                    if (device.isSink()) {
                        telephonySink = device;
                    }
                    if (device.isSource()) {
                        telephonySource = device;
                    }
                }
            }
            System.out.println("telephony_sink_present=" + (telephonySink != null));
            System.out.println("telephony_source_present=" + (telephonySource != null));
            probeTelephonyTrack(
                "MEDIA_MUSIC",
                telephonySink,
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.CONTENT_TYPE_MUSIC
            );
            probeTelephonyTrack(
                "VOICE_COMMUNICATION",
                telephonySink,
                AudioAttributes.USAGE_VOICE_COMMUNICATION,
                AudioAttributes.CONTENT_TYPE_SPEECH
            );
        } catch (Throwable error) {
            printError("system_context_or_audio_manager", error);
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private static int captureDownlink(int durationMs, String outputPath) {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING);
        int bufferSize = minBuffer > 0 ? Math.max(minBuffer * 2, 4096) : 4096;
        int targetSamples = SAMPLE_RATE * durationMs / 1000;
        AudioRecord record = null;
        FileOutputStream output = null;
        File outputFile = outputPath == null ? null : new File(outputPath);
        boolean successful = false;
        PcmMetrics metrics = new PcmMetrics();
        int readErrors = 0;

        System.out.println("capture_source=VOICE_DOWNLINK");
        System.out.println("sample_rate=" + SAMPLE_RATE);
        System.out.println("duration_ms=" + durationMs);
        System.out.println("target_samples=" + targetSamples);
        System.out.println("buffer_bytes=" + bufferSize);
        System.out.println("output_path=" + (outputPath == null ? "none" : outputPath));

        try {
            record = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_DOWNLINK)
                .setAudioFormat(
                    new AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_IN)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build();

            System.out.println("record_state=" + record.getState());
            System.out.println("session_id=" + record.getAudioSessionId());
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                return 3;
            }

            record.startRecording();
            System.out.println("recording_state=" + record.getRecordingState());
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                return 4;
            }

            AudioDeviceInfo routedDevice = record.getRoutedDevice();
            if (routedDevice != null) {
                System.out.println(
                    "routed_device=id:" + routedDevice.getId()
                        + ",type:" + routedDevice.getType()
                        + ",product:" + routedDevice.getProductName()
                );
            } else {
                System.out.println("routed_device=none");
            }

            if (outputFile != null) {
                output = new FileOutputStream(outputFile, false);
            }

            short[] buffer = new short[1024];
            byte[] encoded = output == null ? null : new byte[buffer.length * 2];
            while (metrics.sampleCount() < targetSamples) {
                int remaining = (int) (targetSamples - metrics.sampleCount());
                int requested = Math.min(buffer.length, remaining);
                int read = record.read(buffer, 0, requested, AudioRecord.READ_BLOCKING);
                if (read < 0) {
                    readErrors++;
                    System.out.println("read_error=" + read);
                    break;
                }
                if (read == 0) {
                    continue;
                }
                metrics.accept(buffer, read);
                if (output != null) {
                    encodePcm16LittleEndian(buffer, read, encoded);
                    output.write(encoded, 0, read * 2);
                }
            }

            System.out.println("samples_read=" + metrics.sampleCount());
            System.out.println("bytes_read=" + (metrics.sampleCount() * 2));
            System.out.println("non_zero_samples=" + metrics.nonZeroSampleCount());
            System.out.println("peak=" + metrics.peak());
            System.out.println("rms=" + metrics.rms());
            System.out.println("read_errors=" + readErrors);

            successful = metrics.sampleCount() == targetSamples && readErrors == 0;
            return successful ? 0 : 6;
        } catch (Throwable error) {
            printError("capture_downlink", error);
            return 7;
        } finally {
            if (record != null) {
                try {
                    if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop();
                    }
                } catch (Throwable ignored) {
                    // Preserve the primary capture result.
                }
                try {
                    record.release();
                } catch (Throwable ignored) {
                    // Preserve the primary capture result.
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                    // Preserve the primary capture result.
                }
            }
            if (!successful && outputFile != null && outputFile.exists()) {
                try {
                    outputFile.delete();
                } catch (Throwable ignored) {
                    // Best-effort cleanup of an incomplete diagnostic capture.
                }
            }
        }
    }

    private static int injectTone(int durationMs, int frequencyHz, double amplitude) {
        AudioTrack track = null;
        System.out.println("inject_usage=USAGE_MEDIA");
        System.out.println("inject_content_type=CONTENT_TYPE_MUSIC");
        System.out.println("sample_rate=" + SAMPLE_RATE);
        System.out.println("duration_ms=" + durationMs);
        System.out.println("frequency_hz=" + frequencyHz);
        System.out.println("amplitude=" + amplitude);

        try {
            Context context = systemContext();
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            System.out.println("audio_mode=" + audioManager.getMode());

            AudioDeviceInfo telephonySink = null;
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_TELEPHONY && device.isSink()) {
                    telephonySink = device;
                    break;
                }
            }
            if (telephonySink == null) {
                System.out.println("telephony_sink_present=false");
                return 8;
            }
            System.out.println("telephony_sink_present=true");
            System.out.println(
                "telephony_sink=id:" + telephonySink.getId()
                    + ",type:" + telephonySink.getType()
                    + ",product:" + telephonySink.getProductName()
            );

            short[] tone = ToneGenerator.sinePcm16(SAMPLE_RATE, durationMs, frequencyHz, amplitude);
            int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING);
            int bufferSize = minBuffer > 0 ? Math.max(minBuffer * 2, 4096) : 4096;
            System.out.println("target_samples=" + tone.length);
            System.out.println("buffer_bytes=" + bufferSize);

            track = new AudioTrack.Builder()
                .setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    new AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

            System.out.println("track_state=" + track.getState());
            System.out.println("track_session_id=" + track.getAudioSessionId());
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                return 9;
            }

            boolean preferredSet = track.setPreferredDevice(telephonySink);
            System.out.println("preferred_set=" + preferredSet);
            System.out.println(
                "preferred_id=" + (track.getPreferredDevice() == null ? -1 : track.getPreferredDevice().getId())
            );
            if (!preferredSet) {
                return 10;
            }

            track.play();
            System.out.println("play_state=" + track.getPlayState());

            short[] warmupSilence = new short[ROUTE_WARMUP_SAMPLES];
            Arrays.fill(warmupSilence, (short) 0);
            int warmupWritten = track.write(
                warmupSilence,
                0,
                warmupSilence.length,
                AudioTrack.WRITE_BLOCKING
            );
            System.out.println("warmup_samples_written=" + warmupWritten);
            if (warmupWritten != warmupSilence.length) {
                return 11;
            }

            AudioDeviceInfo routedDevice = track.getRoutedDevice();
            if (routedDevice == null) {
                System.out.println("routed_device=none");
                return 12;
            }
            System.out.println(
                "routed_device=id:" + routedDevice.getId()
                    + ",type:" + routedDevice.getType()
                    + ",product:" + routedDevice.getProductName()
            );
            if (routedDevice.getType() != AudioDeviceInfo.TYPE_TELEPHONY) {
                System.out.println("route_guard=blocked_non_telephony");
                return 13;
            }
            System.out.println("route_guard=telephony_confirmed");

            int totalWritten = 0;
            while (totalWritten < tone.length) {
                int written = track.write(
                    tone,
                    totalWritten,
                    tone.length - totalWritten,
                    AudioTrack.WRITE_BLOCKING
                );
                if (written < 0) {
                    System.out.println("write_error=" + written);
                    return 14;
                }
                if (written == 0) {
                    continue;
                }
                totalWritten += written;
            }
            System.out.println("samples_written=" + totalWritten);

            long deadline = System.nanoTime() + (durationMs + 500L) * 1_000_000L;
            int playbackHead = track.getPlaybackHeadPosition();
            while (playbackHead < ROUTE_WARMUP_SAMPLES + tone.length && System.nanoTime() < deadline) {
                Thread.sleep(10L);
                playbackHead = track.getPlaybackHeadPosition();
            }
            System.out.println("playback_head=" + playbackHead);
            System.out.println("expected_playback_head=" + (ROUTE_WARMUP_SAMPLES + tone.length));
            return playbackHead > ROUTE_WARMUP_SAMPLES ? 0 : 15;
        } catch (Throwable error) {
            printError("inject_tone", error);
            return 16;
        } finally {
            if (track != null) {
                try {
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop();
                    }
                } catch (Throwable ignored) {
                    // Preserve the primary injection result.
                }
                try {
                    track.flush();
                } catch (Throwable ignored) {
                    // Preserve the primary injection result.
                }
                try {
                    track.release();
                } catch (Throwable ignored) {
                    // Preserve the primary injection result.
                }
            }
        }
    }

    private static void encodePcm16LittleEndian(short[] samples, int count, byte[] output) {
        for (int i = 0; i < count; i++) {
            int value = samples[i];
            output[i * 2] = (byte) (value & 0xff);
            output[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
        }
    }

    private static void printPermission(Context context, String label, String permission) {
        int result = context.checkPermission(permission, Process.myPid(), Process.myUid());
        System.out.println(
            "permission_" + label + "="
                + (result == PackageManager.PERMISSION_GRANTED ? "granted" : "denied")
        );
    }

    @android.annotation.SuppressLint("MissingPermission")
    private static void probeRecordSource(String label, int source) {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING);
        int bufferSize = minBuffer > 0 ? minBuffer * 2 : 4096;
        AudioRecord record = null;
        try {
            record = new AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    new AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_IN)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build();
            System.out.println(
                "record_" + label
                    + "=state:" + record.getState()
                    + ",session:" + record.getAudioSessionId()
                    + ",buffer:" + bufferSize
            );
        } catch (Throwable error) {
            printError("record_" + label, error);
        } finally {
            if (record != null) {
                try {
                    record.release();
                } catch (Throwable ignored) {
                    // Preserve the primary probe result.
                }
            }
        }
    }

    private static void probeTelephonyTrack(
        String label,
        AudioDeviceInfo telephonySink,
        int usage,
        int contentType
    ) {
        if (telephonySink == null) {
            System.out.println("track_" + label + "=skipped:no_sink");
            return;
        }

        int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING);
        int bufferSize = minBuffer > 0 ? minBuffer * 2 : 4096;
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                .setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(contentType)
                        .build()
                )
                .setAudioFormat(
                    new AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

            boolean preferred = track.setPreferredDevice(telephonySink);
            System.out.println(
                "track_" + label
                    + "=state:" + track.getState()
                    + ",preferred_set:" + preferred
                    + ",preferred_id:" + (track.getPreferredDevice() == null ? -1 : track.getPreferredDevice().getId())
                    + ",buffer:" + bufferSize
            );
        } catch (Throwable error) {
            printError("track_" + label, error);
        } finally {
            if (track != null) {
                try {
                    track.release();
                } catch (Throwable ignored) {
                    // Preserve the primary probe result.
                }
            }
        }
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

    private static void flushAndExit(int exitCode) {
        System.out.flush();
        System.err.flush();
        System.exit(exitCode);
    }
}
