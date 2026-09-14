package pl.michalmatu.aicallbridge;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Process;

import java.lang.reflect.Method;

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

    private ShellAudioProbe() {}

    public static void main(String[] args) {
        System.out.println("probe=shell-audio-v1");
        System.out.println("uid=" + Process.myUid());
        System.out.println("pid=" + Process.myPid());

        probeRecordSource("VOICE_CALL", MediaRecorder.AudioSource.VOICE_CALL);
        probeRecordSource("VOICE_DOWNLINK", MediaRecorder.AudioSource.VOICE_DOWNLINK);
        probeRecordSource("VOICE_UPLINK", MediaRecorder.AudioSource.VOICE_UPLINK);

        try {
            Context context = systemContext();
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            System.out.println("audio_mode=" + audioManager.getMode());

            AudioDeviceInfo telephonySink = null;
            AudioDeviceInfo telephonySource = null;
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_ALL)) {
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
            probeTelephonyTrack(telephonySink);
        } catch (Throwable error) {
            printError("system_context_or_audio_manager", error);
        }
    }

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

    private static void probeTelephonyTrack(AudioDeviceInfo telephonySink) {
        if (telephonySink == null) {
            System.out.println("track_telephony=skipped:no_sink");
            return;
        }

        int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING);
        int bufferSize = minBuffer > 0 ? minBuffer * 2 : 4096;
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                .setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
                "track_telephony=state:" + track.getState()
                    + ",preferred_set:" + preferred
                    + ",preferred_id:" + (track.getPreferredDevice() == null ? -1 : track.getPreferredDevice().getId())
                    + ",buffer:" + bufferSize
            );
        } catch (Throwable error) {
            printError("track_telephony", error);
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
        String message = root.getMessage();
        if (message == null) {
            message = "";
        }
        message = message.replace('\n', ' ').replace('\r', ' ');
        System.out.println(label + "=error:" + root.getClass().getSimpleName() + ":" + message);
    }
}
