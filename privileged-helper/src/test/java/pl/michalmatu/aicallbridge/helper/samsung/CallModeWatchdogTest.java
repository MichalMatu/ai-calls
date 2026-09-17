package pl.michalmatu.aicallbridge.helper.samsung;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class CallModeWatchdogTest {
    @Test
    public void staysArmedWhileModeRemainsInCall() throws Exception {
        AtomicInteger mode = new AtomicInteger(AudioManager.MODE_IN_CALL);
        AtomicInteger fired = new AtomicInteger();
        try (CallModeWatchdog watchdog = new CallModeWatchdog(5L, mode::get, fired::incrementAndGet)) {
            watchdog.start();
            Thread.sleep(40L);
            assertEquals(0, fired.get());
            assertFalse(watchdog.hasFired());
        }
    }

    @Test
    public void firesOnceWhenModeLeavesInCall() throws Exception {
        AtomicInteger mode = new AtomicInteger(AudioManager.MODE_IN_CALL);
        AtomicInteger fired = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        try (CallModeWatchdog watchdog = new CallModeWatchdog(
            5L,
            mode::get,
            () -> {
                fired.incrementAndGet();
                latch.countDown();
            }
        )) {
            watchdog.start();
            mode.set(AudioManager.MODE_NORMAL);
            assertTrue(latch.await(250L, TimeUnit.MILLISECONDS));
            Thread.sleep(20L);
            assertEquals(1, fired.get());
            assertTrue(watchdog.hasFired());
        }
    }

    @Test
    public void closePreventsLaterFire() throws Exception {
        AtomicInteger mode = new AtomicInteger(AudioManager.MODE_IN_CALL);
        AtomicInteger fired = new AtomicInteger();
        CallModeWatchdog watchdog = new CallModeWatchdog(5L, mode::get, fired::incrementAndGet);
        watchdog.start();
        watchdog.close();
        mode.set(AudioManager.MODE_NORMAL);
        Thread.sleep(40L);
        assertEquals(0, fired.get());
    }
}
