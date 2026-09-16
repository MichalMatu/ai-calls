package pl.michalmatu.aicallbridge.shizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public final class ShizukuProbeWorkerTest {
    @Test
    public void runsTaskOnDedicatedDaemonThread() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> executedOn = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        Thread worker = ShizukuProbeWorker.start("aicall-probe-test", () -> {
            executedOn.set(Thread.currentThread());
            completed.countDown();
        });

        assertTrue(completed.await(1L, TimeUnit.SECONDS));
        worker.join(1_000L);

        assertNotSame(caller, executedOn.get());
        assertEquals(worker, executedOn.get());
        assertEquals("aicall-probe-test", worker.getName());
        assertTrue(worker.isDaemon());
    }
}
