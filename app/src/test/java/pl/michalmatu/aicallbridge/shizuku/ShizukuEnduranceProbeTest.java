package pl.michalmatu.aicallbridge.shizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class ShizukuEnduranceProbeTest {
    @Test
    public void defaultDurationRemainsThirtySeconds() {
        assertEquals(30_000L, ShizukuEnduranceProbe.defaultDurationMs());
    }

    @Test
    public void acceptsFiveSecondsAndTenMinutes() {
        assertEquals(5_000L, ShizukuEnduranceProbe.validateDurationMs(5_000L));
        assertEquals(600_000L, ShizukuEnduranceProbe.validateDurationMs(600_000L));
    }

    @Test
    public void rejectsDurationsOutsideDiagnosticBounds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ShizukuEnduranceProbe.validateDurationMs(4_999L)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ShizukuEnduranceProbe.validateDurationMs(600_001L)
        );
    }
}
