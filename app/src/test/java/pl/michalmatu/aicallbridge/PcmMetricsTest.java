package pl.michalmatu.aicallbridge;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PcmMetricsTest {
    @Test
    public void silenceHasZeroMetrics() {
        PcmMetrics metrics = new PcmMetrics();
        metrics.accept(new short[] {0, 0, 0, 0}, 4);

        assertEquals(4, metrics.sampleCount());
        assertEquals(0, metrics.nonZeroSampleCount());
        assertEquals(0, metrics.peak());
        assertEquals(0.0, metrics.rms(), 0.0);
    }

    @Test
    public void computesPeakNonZeroAndRms() {
        PcmMetrics metrics = new PcmMetrics();
        metrics.accept(new short[] {1000, -1000, 0, 2000}, 4);

        assertEquals(4, metrics.sampleCount());
        assertEquals(3, metrics.nonZeroSampleCount());
        assertEquals(2000, metrics.peak());
        assertEquals(Math.sqrt(1_500_000.0), metrics.rms(), 0.000001);
    }

    @Test
    public void acceptsOnlyRequestedPrefix() {
        PcmMetrics metrics = new PcmMetrics();
        metrics.accept(new short[] {100, 200, 300, 30_000}, 3);

        assertEquals(3, metrics.sampleCount());
        assertEquals(3, metrics.nonZeroSampleCount());
        assertEquals(300, metrics.peak());
    }
}
