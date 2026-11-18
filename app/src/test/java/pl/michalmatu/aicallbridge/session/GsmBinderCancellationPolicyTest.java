package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GsmBinderCancellationPolicyTest {
    @Test public void stopBeforePrepareRejectsThatTokenAndEveryOlderOne() {
        long stopped = GsmBinderCancellationPolicy.afterStop(0L, 41L);
        assertEquals(41L, stopped);
        assertFalse(GsmBinderCancellationPolicy.mayPrepare(41L, stopped));
        assertFalse(GsmBinderCancellationPolicy.mayPrepare(3L, stopped));
        assertTrue(GsmBinderCancellationPolicy.mayPrepare(42L, stopped));
    }

    @Test public void delayedOldStopCannotCancelNewerSession() {
        long watermark = GsmBinderCancellationPolicy.afterStop(0L, 81L);
        watermark = GsmBinderCancellationPolicy.afterStop(watermark, 10L);
        assertEquals(81L, watermark);
        assertTrue(GsmBinderCancellationPolicy.mayPrepare(82L, watermark));
    }

    @Test public void invalidStopNeverRevokesFutureTokens() {
        assertEquals(9L, GsmBinderCancellationPolicy.afterStop(9L, 0L));
        assertEquals(9L, GsmBinderCancellationPolicy.afterStop(9L, -1L));
    }
}
