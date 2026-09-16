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
 *
 * <p>On the target Samsung firmware, the direct-shell proof path requires VOICE_DOWNLINK to be
 * constructed before creating/touching a process Context used for audio services. Therefore
 * {@link #open(int)} remains deliberately context-free. Privileged hosts that already run inside an
 * app-attributed process, such as a Shizuku UserService, may instead use
 * {@link #open(int, Context)} to set the trusted shell attribution explicitly on AudioRecord.</p>
 */
public final class SamsungVoiceDownlinkCapture implements AutoCloseable {
    public static final int SAMPLE_RATE_16K = 16_000;

    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private final AudioRecord record;
    private final int sampleRate;

    private volatile boolean started;
    private volatile boolean closed;

    private SamsungVoiceDownlinkCapture(AudioRecord record, int sampleRate) {
        this.record = record;
        this.sampleRate = sampleRate;
    }

    /**
     * Constructs VOICE_DOWNLINK before any Context/AudioManager work.
     * This ordering matches the physically proven ShellAudioProbe path on the target S22+.
     */
    public static SamsungVoiceDownlinkCapture open(int sampleRate) {
        return openInternal(sampleRate, null);
    }

    /**
     * Constructs VOICE_DOWNLINK with an explicit attribution Context.
     *
     * <p>This is for privileged hosts whose process package does not match their trusted UID. It is
     * not used by the frozen direct-shell proof path.</p>
     */
    public static SamsungVoiceDownlinkCapture open(int sampleRate, Context attributionContext) {
        Objects.requireNonNull(attributionContext, "attributionContext");
        return openInternal(sampleRate, attributionContext);
    }

    private static SamsungVoiceDownlinkCapture openInternal(
        int sampleRate,
        Context attributionContext
    ) {
        validateSampleRate(sampleRate);

        int minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_MASK, ENCODING);
        int bufferSize = minBuffer > 0 ? Math.max(minBuffer * 2, 4096) : 4096;
        AudioRecord.Builder builder = new AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_DOWNLINK)
            .setAudioFormat(
                new AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNEL_MASK)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize);
        if (attributionContext != null) {
            builder.setContext(attributionContext);
        }
        AudioRecord record = builder.build();

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            throw new IllegalStateException("VOICE_DOWNLINK AudioRecord failed to initialize");
        }
        return new SamsungVoiceDownlinkCapture(record, sampleRate);
    }

    /** Starts recording and confirms that Android routed the capture from TYPE_TELEPHONY. */
    public synchronized AudioDeviceInfo startAndConfirmTelephonyRoute(Context context)
        throws InterruptedException {
        Objects.requireNonNull(context, "context");
        ensureOpen();
        if (started) {
            AudioDeviceInfo routed = record.getRoutedDevice();
            requireTelephonyRoute(routed);
            return routed;
        }

        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                throw new IllegalStateException("AudioManager unavailable");
            }
            if (audioManager.getMode() != AudioManager.MODE_IN_CALL) {
                throw new IllegalStateException("cellular call is not active (AudioManager not IN_CALL)");
            }

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
