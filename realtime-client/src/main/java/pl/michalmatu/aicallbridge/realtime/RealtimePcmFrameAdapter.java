package pl.michalmatu.aicallbridge.realtime;

import java.util.Objects;

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

/**
 * Explicit PCM boundary between the frozen Samsung telephony bridge and raw-audio Realtime paths.
 *
 * <p>The Samsung bridge stays mono PCM16LE at 16 kHz. Realtime raw PCM uses mono PCM16LE at
 * 24 kHz. This adapter performs frame-local linear resampling without mutating the source frame.
 * It is intentionally isolated so a higher-quality streaming resampler can replace it later
 * without changing the privileged telephony path or the transport contract.</p>
 */
public final class RealtimePcmFrameAdapter {
    public static final int TELEPHONY_SAMPLE_RATE_HZ = 16_000;
    public static final int REALTIME_SAMPLE_RATE_HZ = 24_000;

    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;
    private static final PcmFormat TELEPHONY_FORMAT =
        new PcmFormat(TELEPHONY_SAMPLE_RATE_HZ, CHANNELS, BITS_PER_SAMPLE);
    private static final PcmFormat REALTIME_FORMAT =
        new PcmFormat(REALTIME_SAMPLE_RATE_HZ, CHANNELS, BITS_PER_SAMPLE);

    public PcmFrame toRealtime(PcmFrame source) {
        return convert(source, TELEPHONY_FORMAT, REALTIME_FORMAT);
    }

    public PcmFrame toTelephony(PcmFrame source) {
        return convert(source, REALTIME_FORMAT, TELEPHONY_FORMAT);
    }

    private static PcmFrame convert(
        PcmFrame source,
        PcmFormat expectedSourceFormat,
        PcmFormat targetFormat
    ) {
        Objects.requireNonNull(source, "source");
        requireFormat(source.getFormat(), expectedSourceFormat);

        byte[] inputBytes = Objects.requireNonNull(source.getData(), "source.data");
        if ((inputBytes.length & 1) != 0) {
            throw new IllegalArgumentException("PCM16LE payload must contain whole 16-bit samples");
        }
        if (inputBytes.length == 0) {
            return new PcmFrame(targetFormat, new byte[0], source.getMonotonicTimestampNs());
        }

        short[] input = decodePcm16Le(inputBytes);
        int outputSampleCount = resampledSampleCount(
            input.length,
            expectedSourceFormat.getSampleRateHz(),
            targetFormat.getSampleRateHz()
        );
        short[] output = resampleLinear(input, outputSampleCount);
        return new PcmFrame(
            targetFormat,
            encodePcm16Le(output),
            source.getMonotonicTimestampNs()
        );
    }

    private static void requireFormat(PcmFormat actual, PcmFormat expected) {
        Objects.requireNonNull(actual, "source.format");
        if (actual.getSampleRateHz() != expected.getSampleRateHz()
            || actual.getChannels() != expected.getChannels()
            || actual.getBitsPerSample() != expected.getBitsPerSample()) {
            throw new IllegalArgumentException(
                "unexpected PCM format: "
                    + actual.getSampleRateHz() + " Hz, "
                    + actual.getChannels() + " ch, "
                    + actual.getBitsPerSample() + " bit"
            );
        }
    }

    private static int resampledSampleCount(int inputSamples, int sourceRate, int targetRate) {
        long scaled = (long) inputSamples * targetRate;
        long rounded = (scaled + sourceRate / 2L) / sourceRate;
        if (rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("PCM frame is too large to resample");
        }
        return (int) rounded;
    }

    /** Endpoint-preserving frame-local linear interpolation. */
    private static short[] resampleLinear(short[] input, int outputCount) {
        if (outputCount == 0) {
            return new short[0];
        }
        if (input.length == 1 || outputCount == 1) {
            short[] output = new short[outputCount];
            java.util.Arrays.fill(output, input[0]);
            return output;
        }

        short[] output = new short[outputCount];
        double positionScale = (double) (input.length - 1) / (double) (outputCount - 1);
        for (int outputIndex = 0; outputIndex < outputCount; outputIndex++) {
            double sourcePosition = outputIndex * positionScale;
            int leftIndex = (int) sourcePosition;
            int rightIndex = Math.min(leftIndex + 1, input.length - 1);
            double fraction = sourcePosition - leftIndex;
            double interpolated = input[leftIndex]
                + (input[rightIndex] - input[leftIndex]) * fraction;
            long rounded = Math.round(interpolated);
            if (rounded > Short.MAX_VALUE) {
                rounded = Short.MAX_VALUE;
            } else if (rounded < Short.MIN_VALUE) {
                rounded = Short.MIN_VALUE;
            }
            output[outputIndex] = (short) rounded;
        }
        return output;
    }

    private static short[] decodePcm16Le(byte[] pcm) {
        short[] samples = new short[pcm.length / 2];
        for (int i = 0; i < samples.length; i++) {
            int offset = i * 2;
            int low = pcm[offset] & 0xff;
            int high = pcm[offset + 1] << 8;
            samples[i] = (short) (high | low);
        }
        return samples;
    }

    private static byte[] encodePcm16Le(short[] samples) {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int value = samples[i] & 0xffff;
            pcm[i * 2] = (byte) value;
            pcm[i * 2 + 1] = (byte) (value >>> 8);
        }
        return pcm;
    }
}
