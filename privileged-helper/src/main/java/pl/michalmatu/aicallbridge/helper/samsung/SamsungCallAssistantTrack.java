package pl.michalmatu.aicallbridge.helper.samsung;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Low-level Samsung cellular-uplink primitive proven on the target S22+ build.
 *
 * <p>The caller must already execute with the required privileged/shell identity. This class does
 * not obtain privileges and does not place calls. It only owns the CALL_ASSISTANT AudioTrack,
 * enforces the telephony route before accepting user PCM, converts mono PCM16LE to Samsung's
 * required stereo profile, and provides an immediate abort path.</p>
 */
public final class SamsungCallAssistantTrack implements AutoCloseable {
    public static final int CALL_ASSISTANT_USAGE = 17;
    public static final int SAMPLE_RATE_16K = 16_000;
    public static final int SAMPLE_RATE_48K = 48_000;

    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int CHANNEL_COUNT = 2;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private final AudioManager audioManager;
    private final AudioTrack track;
    private final int sampleRate;
    private final int warmupFrames;

    private short[] stereoScratch = new short[0];
    private boolean started;
    private boolean closed;

    private SamsungCallAssistantTrack(
        AudioManager audioManager,
        AudioTrack track,
        int sampleRate
    ) {
        this.audioManager = audioManager;
        this.track = track;
        this.sampleRate = sampleRate;
        this.warmupFrames = sampleRate / 50; // 20 ms.
    }

    public static SamsungCallAssistantTrack open(Context context, int sampleRate) throws Exception {
        Objects.requireNonNull(context, "context");
        validateSampleRate(sampleRate);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            throw new UnsupportedOperationException(
                "CALL_ASSISTANT attributed AudioTrack requires Android 14 / API 34+"
            );
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            throw new IllegalStateException("AudioManager unavailable");
        }

        AudioAttributes attributes = buildCallAssistantAttributes();
        if (getSystemUsage(attributes) != CALL_ASSISTANT_USAGE) {
            throw new IllegalStateException("CALL_ASSISTANT system usage was not retained");
        }

        int minBuffer = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_MASK, ENCODING);
        int bufferSize = minBuffer > 0 ? Math.max(minBuffer * 2, 8192) : 8192;
        AudioTrack track = new AudioTrack.Builder()
            .setContext(context)
            .setAudioAttributes(attributes)
            .setAudioFormat(
                new AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNEL_MASK)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build();

        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new IllegalStateException("CALL_ASSISTANT AudioTrack failed to initialize");
        }
        return new SamsungCallAssistantTrack(audioManager, track, sampleRate);
    }

    /**
     * Starts the track and proves that Android actually routed it to TYPE_TELEPHONY.
     * No caller PCM is accepted before this method succeeds.
     */
    public synchronized AudioDeviceInfo startAndConfirmTelephonyRoute() throws InterruptedException {
        ensureOpen();
        if (started) {
            AudioDeviceInfo routed = track.getRoutedDevice();
            requireTelephonyRoute(routed);
            return routed;
        }
        if (audioManager.getMode() != AudioManager.MODE_IN_CALL) {
            throw new IllegalStateException("cellular call is not active (AudioManager not IN_CALL)");
        }

        try {
            track.play();
            if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                throw new IllegalStateException("CALL_ASSISTANT AudioTrack did not enter PLAYING");
            }

            short[] warmup = new short[warmupFrames * CHANNEL_COUNT];
            int written = writeFully(track, warmup, 0, warmup.length);
            if (written != warmup.length) {
                throw new IllegalStateException("failed to write route warmup: " + written);
            }

            Thread.sleep(30L);
            AudioDeviceInfo routed = track.getRoutedDevice();
            requireTelephonyRoute(routed);
            started = true;
            return routed;
        } catch (RuntimeException | InterruptedException error) {
            abortNow();
            throw error;
        }
    }

    /**
     * Writes mono signed PCM16 little-endian and duplicates each sample into the stereo Samsung
     * INCALL_MUSIC profile. Returns the number of mono frames accepted.
     */
    public synchronized int writeMonoPcm16Le(byte[] data, int offset, int length) {
        Objects.requireNonNull(data, "data");
        ensureStartedAndRouted();
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new IndexOutOfBoundsException("invalid PCM slice");
        }
        if ((length & 1) != 0) {
            throw new IllegalArgumentException("PCM16 byte count must be even");
        }

        int monoFrames = length / 2;
        int stereoSamples = monoFrames * CHANNEL_COUNT;
        ensureScratch(stereoSamples);
        for (int frame = 0; frame < monoFrames; frame++) {
            int index = offset + frame * 2;
            int low = data[index] & 0xff;
            int high = data[index + 1];
            short sample = (short) ((high << 8) | low);
            int stereoIndex = frame * 2;
            stereoScratch[stereoIndex] = sample;
            stereoScratch[stereoIndex + 1] = sample;
        }

        int written = writeFully(track, stereoScratch, 0, stereoSamples);
        if (written != stereoSamples) {
            throw new IllegalStateException("short CALL_ASSISTANT write: " + written + "/" + stereoSamples);
        }
        return monoFrames;
    }

    public synchronized int getSampleRate() {
        return sampleRate;
    }

    public synchronized int getState() {
        ensureOpen();
        return track.getState();
    }

    public synchronized int getPlayState() {
        ensureOpen();
        return track.getPlayState();
    }

    public synchronized int getAudioSessionId() {
        ensureOpen();
        return track.getAudioSessionId();
    }

    public synchronized int getWarmupFrames() {
        return warmupFrames;
    }

    public synchronized int getPlaybackHeadPosition() {
        ensureOpen();
        return track.getPlaybackHeadPosition();
    }

    public synchronized AudioDeviceInfo getRoutedDevice() {
        ensureOpen();
        return track.getRoutedDevice();
    }

    /** Graceful end of the local injection session. */
    public synchronized void stop() {
        if (closed) {
            return;
        }
        try {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop();
            }
        } finally {
            try {
                track.flush();
            } finally {
                track.release();
                started = false;
                closed = true;
            }
        }
    }

    /**
     * Takeover/fail-safe path: discard queued output and release the track immediately.
     */
    public synchronized void abortNow() {
        if (closed) {
            return;
        }
        try {
            try {
                if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause();
                }
            } catch (RuntimeException ignored) {
                // Continue cleanup.
            }
            try {
                track.flush();
            } catch (RuntimeException ignored) {
                // Continue cleanup.
            }
            try {
                track.stop();
            } catch (RuntimeException ignored) {
                // Continue cleanup.
            }
        } finally {
            track.release();
            started = false;
            closed = true;
        }
    }

    @Override
    public void close() {
        abortNow();
    }

    /**
     * Builds the physically proven Samsung CALL_ASSISTANT attributes.
     *
     * <p>The helper runs under the trusted shell identity on the target API-36 S22+. Android lint
     * cannot model that hidden-API exemption, so the frozen reflective fallback is deliberately
     * retained here. Changing it requires a targeted device regression of the uplink path.</p>
     */
    @SuppressLint("SoonBlockedPrivateApi")
    public static AudioAttributes buildCallAssistantAttributes() throws Exception {
        AudioAttributes.Builder builder = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);

        try {
            Method setSystemUsage = AudioAttributes.Builder.class.getDeclaredMethod(
                "setSystemUsage", int.class
            );
            setSystemUsage.setAccessible(true);
            setSystemUsage.invoke(builder, CALL_ASSISTANT_USAGE);
        } catch (ReflectiveOperationException | RuntimeException methodError) {
            Field usageField = AudioAttributes.Builder.class.getDeclaredField("mUsage");
            usageField.setAccessible(true);
            usageField.setInt(builder, CALL_ASSISTANT_USAGE);
        }
        return builder.build();
    }

    public static int getSystemUsage(AudioAttributes attributes) throws Exception {
        Method method = AudioAttributes.class.getDeclaredMethod("getSystemUsage");
        method.setAccessible(true);
        return ((Integer) method.invoke(attributes)).intValue();
    }

    public static AudioDeviceInfo findTelephonySink(AudioManager audioManager) {
        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getType() == AudioDeviceInfo.TYPE_TELEPHONY && device.isSink()) {
                return device;
            }
        }
        return null;
    }

    private void ensureStartedAndRouted() {
        ensureOpen();
        if (!started || track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            throw new IllegalStateException("CALL_ASSISTANT track has not been started");
        }
        requireTelephonyRoute(track.getRoutedDevice());
    }

    private static void requireTelephonyRoute(AudioDeviceInfo device) {
        if (device == null || device.getType() != AudioDeviceInfo.TYPE_TELEPHONY) {
            throw new IllegalStateException("CALL_ASSISTANT is not routed to TYPE_TELEPHONY");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("CALL_ASSISTANT track is closed");
        }
    }

    private void ensureScratch(int samples) {
        if (stereoScratch.length < samples) {
            stereoScratch = new short[samples];
        }
    }

    private static int writeFully(AudioTrack track, short[] samples, int offset, int length) {
        int total = 0;
        while (total < length) {
            int written = track.write(
                samples,
                offset + total,
                length - total,
                AudioTrack.WRITE_BLOCKING
            );
            if (written <= 0) {
                return written < 0 ? written : total;
            }
            total += written;
        }
        return total;
    }

    private static void validateSampleRate(int sampleRate) {
        if (sampleRate != SAMPLE_RATE_16K && sampleRate != SAMPLE_RATE_48K) {
            throw new IllegalArgumentException("sampleRate must be 16000 or 48000");
        }
    }
}
