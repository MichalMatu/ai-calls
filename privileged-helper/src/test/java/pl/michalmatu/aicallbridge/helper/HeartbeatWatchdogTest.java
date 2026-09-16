package pl.michalmatu.aicallbridge.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class HeartbeatWatchdogTest {
    @Test
    public void timeoutFiresExactlyOnce() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        HeartbeatWatchdog watchdog = new HeartbeatWatchdog(60L, () -> {
            calls.incrementAndGet();
            fired.countDown();
        });

        watchdog.start();
        assertTrue(fired.await(500L, TimeUnit.MILLISECONDS));
        Thread.sleep(80L);

        assertTrue(watchdog.hasFired());
        assertEquals(1, calls.get());
        assertFalse(watchdog.heartbeat());
    }

    @Test
    public void heartbeatMovesDeadlineForward() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        HeartbeatWatchdog watchdog = new HeartbeatWatchdog(140L, fired::countDown);

        watchdog.start();
        Thread.sleep(80L);
        assertTrue(watchdog.heartbeat());
        Thread.sleep(90L);
        assertEquals(1L, fired.getCount());
        assertTrue(fired.await(300L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void closePreventsTimeout() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        HeartbeatWatchdog watchdog = new HeartbeatWatchdog(60L, fired::countDown);

        watchdog.start();
        watchdog.close();

        assertFalse(fired.await(160L, TimeUnit.MILLISECONDS));
        assertFalse(watchdog.hasFired());
        assertFalse(watchdog.heartbeat());
    }
}
