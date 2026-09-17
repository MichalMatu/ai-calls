package pl.michalmatu.aicallbridge.shizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class ShizukuCycleProbeTest {
    @Test
    public void defaultCycleCountRemainsTwenty() {
        assertEquals(20, ShizukuCycleProbe.defaultCycleCount());
    }

    @Test
    public void acceptsOneAndTwentyCycles() {
        assertEquals(1, ShizukuCycleProbe.validateCycleCount(1));
        assertEquals(20, ShizukuCycleProbe.validateCycleCount(20));
    }

    @Test
    public void rejectsCycleCountsOutsideDiagnosticBounds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ShizukuCycleProbe.validateCycleCount(0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ShizukuCycleProbe.validateCycleCount(21)
        );
    }
}
