package pl.michalmatu.aicallbridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ToneGeneratorTest {
    @Test
    public void generatesExpectedSampleCount() {
        short[] samples = ToneGenerator.sinePcm16(16_000, 400, 1_000, 0.05);
        assertEquals(6_400, samples.length);
    }

    @Test
    public void generatedSignalIsNonZeroAndBounded() {
        short[] samples = ToneGenerator.sinePcm16(16_000, 400, 1_000, 0.05);
        int peak = 0;
        boolean anyNonZero = false;
        for (short sample : samples) {
            int magnitude = Math.abs((int) sample);
            peak = Math.max(peak, magnitude);
            anyNonZero |= sample != 0;
        }
        assertTrue(anyNonZero);
        assertTrue(peak <= Math.floor(Short.MAX_VALUE * 0.05));
        assertTrue(peak >= Math.floor(Short.MAX_VALUE * 0.045));
    }

    @Test
    public void rejectsInvalidDuration() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ToneGenerator.sinePcm16(16_000, 0, 1_000, 0.05)
        );
    }

    @Test
    public void rejectsNyquistOrHigherFrequency() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ToneGenerator.sinePcm16(16_000, 400, 8_000, 0.05)
        );
    }

    @Test
    public void rejectsInvalidAmplitude() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ToneGenerator.sinePcm16(16_000, 400, 1_000, 0.0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ToneGenerator.sinePcm16(16_000, 400, 1_000, 1.01)
        );
    }
}
