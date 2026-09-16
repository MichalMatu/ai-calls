package pl.michalmatu.aicallbridge.helper.samsung;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.Objects;

/**
 * Low-level remote-only cellular downlink capture primitive proven on the target S22+ build.
 *
 * <p>The caller must already execute with the required privileged/shell identity. This class does
 * not obtain privileges and does not place calls. It owns a VOICE_DOWNLINK AudioRecord, requires an
 * active cellular call before recording, confirms TYPE_TELEPHONY routing, and exposes mono PCM16
 * frames to the helper media plane.</p>
 */
public final class SamsungVoiceDownlinkCapture implements AutoCloseable {
    public static final int SAMPLE_RATE_16K = 16_000;

    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private final AudioManager audioManager;
    private final AudioRecord record;
    private final int sampleRate;

    private volatile boolean started;
    private volatile boolean closed;

    private SamsungVoiceDownlinkCapture(
        AudioManager audioManager,
        AudioRecord record,
        int sampleRate
    ) {
        this.audioManager = audioManager;
        this.record = record;
        this.sampleRate = sampleRate;
    }

    public static SamsungVoiceDownlinkCapture open(Context context, int sampleRate) {
        Objects.requireNonNull(context, "context");
        validateSampleRate(sampleRate);

        // Preserve the ordering of the physically proven ShellAudioProbe path on Samsung firmware:
        // create VOICE_DOWNLINK before touching AudioManager/context-backed audio services. Initializing
        // AudioManager first can change attribution state enough for AudioRecord.Builder.build() to fail
        // with UnsupportedOperationException("Cannot create AudioRecord") under shell UID 2000.
        int minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_MASK, ENCODING);
        int bufferSize = minBuffer > 0 ? Math.max(minBuffer * 2, 4096) : 4096;
        AudioRecord record = new AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_DOWNLINK)
            .setAudioFormat(
                new AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNEL_MASK)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build();

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            throw new IllegalStateException("VOICE_DOWNLINK AudioRecord failed to initialize");
        }

        final AudioManager audioManager;
        try {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                throw new IllegalStateException("AudioManager unavailable");
            }
        } catch (RuntimeException | Error error) {
            record.release();
            throw error;
        }

        return new SamsungVoiceDownlinkCapture(audioManager, record, sampleRate);
    }

    /** Starts recording and confirms that Android routed the capture from TYPE_TELEPHONY. */
    public synchronized AudioDeviceInfo startAndConfirmTelephonyRoute() throws InterruptedException {
        ensureOpen();
        if (started) {
            AudioDeviceInfo routed = record.getRoutedDevice();
            requireTelephonyRoute(routed);
            return routed;
        }
        if (audioManager.getMode() != AudioManager.MODE_IN_CALL) {
            throw new IllegalStateException("cellular call is not active (AudioManager not IN_CALL)");
        }

        try {
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("VOICE_DOWNLINK AudioRecord did not enter RECORDING");
            }

            Thread.sleep(30L);
            AudioDeviceInfo routed = record.getRoutedDevice();
            requireTelephonyRoute(routed);
            started = true;
            return routed;
        } catch (RuntimeException | InterruptedException error) {
            abortNow();
            throw error;
        }
    }

    /** Reads mono PCM16 frames. Only the media worker should call this method. */
    public int read(short[] buffer, int offset, int frames) {
        Objects.requireNonNull(buffer, "buffer");
        if (offset < 0 || frames < 0 || offset > buffer.length - frames) {
            throw new IndexOutOfBoundsException("invalid PCM slice");
        }
        if (closed || !started) {
            throw new IllegalStateException("VOICE_DOWNLINK capture has not been started");
        }
        return record.read(buffer, offset, frames, AudioRecord.READ_BLOCKING);
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getState() {
        return record.getState();
    }

    public int getAudioSessionId() {
        return record.getAudioSessionId();
    }

    public AudioDeviceInfo getRoutedDevice() {
        ensureOpen();
        return record.getRoutedDevice();
    }

    /** Graceful end of the capture session. */
    public synchronized void stop() {
        if (closed) {
            return;
        }
        try {
            if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop();
            }
        } finally {
            record.release();
            started = false;
            closed = true;
        }
    }

    /** Immediate fail-safe cleanup; safe to call from another thread to unblock a read. */
    public synchronized void abortNow() {
        if (closed) {
            return;
        }
        try {
            try {
                if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop();
                }
            } catch (RuntimeException ignored) {
                // Continue cleanup.
            }
        } finally {
            record.release();
            started = false;
            closed = true;
        }
    }

    @Override
    public void close() {
        abortNow();
    }

    private static void requireTelephonyRoute(AudioDeviceInfo device) {
        if (device == null || device.getType() != AudioDeviceInfo.TYPE_TELEPHONY) {
            throw new IllegalStateException("VOICE_DOWNLINK is not routed from TYPE_TELEPHONY");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("VOICE_DOWNLINK capture is closed");
        }
    }

    private static void validateSampleRate(int sampleRate) {
        if (sampleRate != SAMPLE_RATE_16K) {
            throw new IllegalArgumentException("sampleRate must be 16000");
        }
    }
}
