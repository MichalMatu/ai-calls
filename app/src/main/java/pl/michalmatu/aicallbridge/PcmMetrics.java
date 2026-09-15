package pl.michalmatu.aicallbridge;

/** Incremental metrics for signed PCM16 samples. */
public final class PcmMetrics {
    private long sampleCount;
    private long nonZeroSampleCount;
    private long sumSquares;
    private int peak;

    public void accept(short[] samples, int count) {
        if (samples == null) {
            throw new IllegalArgumentException("samples must not be null");
        }
        if (count < 0 || count > samples.length) {
            throw new IllegalArgumentException("count out of range");
        }

        for (int i = 0; i < count; i++) {
            int value = samples[i];
            int magnitude = value == Short.MIN_VALUE ? 32_768 : Math.abs(value);
            if (magnitude > peak) {
                peak = magnitude;
            }
            if (value != 0) {
                nonZeroSampleCount++;
            }
            sumSquares += (long) value * value;
        }
        sampleCount += count;
    }

    public long sampleCount() {
        return sampleCount;
    }

    public long nonZeroSampleCount() {
        return nonZeroSampleCount;
    }

    public int peak() {
        return peak;
    }

    public double rms() {
        if (sampleCount == 0) {
            return 0.0;
        }
        return Math.sqrt((double) sumSquares / sampleCount);
    }
}
