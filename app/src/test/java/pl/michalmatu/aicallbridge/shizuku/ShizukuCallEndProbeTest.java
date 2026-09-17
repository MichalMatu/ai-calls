package pl.michalmatu.aicallbridge.shizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class ShizukuCallEndProbeTest {
    @Test
    public void defaultWaitRemainsTwoMinutes() {
        assertEquals(120_000L, ShizukuCallEndProbe.defaultWaitMs());
    }

    @Test
    public void acceptsFiveSecondsAndThreeMinutes() {
        assertEquals(5_000L, ShizukuCallEndProbe.validateWaitMs(5_000L));
        assertEquals(180_000L, ShizukuCallEndProbe.validateWaitMs(180_000L));
    }

    @Test
    public void rejectsWaitOutsideDiagnosticBounds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ShizukuCallEndProbe.validateWaitMs(4_999L)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ShizukuCallEndProbe.validateWaitMs(180_001L)
        );
    }
}
