package pl.michalmatu.aicallbridge.shizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class ShizukuBidirectionalProbeMediaTest {
    @Test
    public void closeUnblocksAndJoinsBothWorkersIdempotently() throws Exception {
        BlockingInputStream input = new BlockingInputStream();
        BlockingOutputStream output = new BlockingOutputStream();
        ShizukuBidirectionalProbeMedia media = ShizukuBidirectionalProbeMedia.start(
            input,
            output,
            640,
            "aicall-test-media"
        );

        assertTrue(input.entered.await(1L, TimeUnit.SECONDS));
        assertTrue(output.entered.await(1L, TimeUnit.SECONDS));

        media.close();

        assertTrue(media.threadsStopped());
        assertEquals(1, input.closeCalls.get());
        assertEquals(1, output.closeCalls.get());

        media.close();
        assertEquals(1, input.closeCalls.get());
        assertEquals(1, output.closeCalls.get());
    }

    @Test
    public void closeDownlinkEndpointLeavesUplinkOwnedUntilFinalClose() throws Exception {
        BlockingInputStream input = new BlockingInputStream();
        BlockingOutputStream output = new BlockingOutputStream();
        ShizukuBidirectionalProbeMedia media = ShizukuBidirectionalProbeMedia.start(
            input,
            output,
            640,
            "aicall-test-rx-close"
        );

        assertTrue(input.entered.await(1L, TimeUnit.SECONDS));
        assertTrue(output.entered.await(1L, TimeUnit.SECONDS));

        media.closeDownlinkEndpoint();

        assertEquals(1, input.closeCalls.get());
        assertEquals(0, output.closeCalls.get());

        media.close();
        assertTrue(media.threadsStopped());
        assertEquals(1, input.closeCalls.get());
        assertEquals(1, output.closeCalls.get());
    }

    @Test
    public void closeUplinkEndpointLeavesDownlinkOwnedUntilFinalClose() throws Exception {
        BlockingInputStream input = new BlockingInputStream();
        BlockingOutputStream output = new BlockingOutputStream();
        ShizukuBidirectionalProbeMedia media = ShizukuBidirectionalProbeMedia.start(
            input,
            output,
            640,
            "aicall-test-tx-close"
        );

        assertTrue(input.entered.await(1L, TimeUnit.SECONDS));
        assertTrue(output.entered.await(1L, TimeUnit.SECONDS));

        media.closeUplinkEndpoint();

        assertEquals(0, input.closeCalls.get());
        assertEquals(1, output.closeCalls.get());

        media.close();
        assertTrue(media.threadsStopped());
        assertEquals(1, input.closeCalls.get());
        assertEquals(1, output.closeCalls.get());
    }

    private static final class BlockingInputStream extends InputStream {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicInteger closeCalls = new AtomicInteger();

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            entered.countDown();
            awaitClosed(closed);
            throw new IOException("closed");
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            closed.countDown();
        }
    }

    private static final class BlockingOutputStream extends OutputStream {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicInteger closeCalls = new AtomicInteger();

        @Override
        public void write(int value) throws IOException {
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            entered.countDown();
            awaitClosed(closed);
            throw new IOException("closed");
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            closed.countDown();
        }
    }

    private static void awaitClosed(CountDownLatch closed) throws IOException {
        try {
            if (!closed.await(2L, TimeUnit.SECONDS)) {
                throw new IOException("close timeout");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", error);
        }
    }
}
