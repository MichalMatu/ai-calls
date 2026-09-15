package pl.michalmatu.aicallbridge;

/** Pure deterministic PCM16 tone generation for bounded telephony TX probes. */
public final class ToneGenerator {
    private ToneGenerator() {}

    public static short[] sinePcm16(
        int sampleRate,
        int durationMs,
        int frequencyHz,
        double amplitude
    ) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be > 0");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0");
        }
        if (frequencyHz <= 0 || frequencyHz * 2 >= sampleRate) {
            throw new IllegalArgumentException("frequencyHz must be > 0 and below Nyquist");
        }
        if (!Double.isFinite(amplitude) || amplitude <= 0.0 || amplitude > 1.0) {
            throw new IllegalArgumentException("amplitude must be finite, > 0 and <= 1");
        }

        long sampleCountLong = (long) sampleRate * durationMs / 1000L;
        if (sampleCountLong <= 0 || sampleCountLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("duration produces invalid sample count");
        }

        int sampleCount = (int) sampleCountLong;
        short[] samples = new short[sampleCount];
        double peak = Math.floor(Short.MAX_VALUE * amplitude);
        double phaseStep = 2.0 * Math.PI * frequencyHz / sampleRate;
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = (short) Math.round(Math.sin(phaseStep * i) * peak);
        }
        return samples;
    }
}
