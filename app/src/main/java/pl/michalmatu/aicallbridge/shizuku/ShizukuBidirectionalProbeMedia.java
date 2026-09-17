package pl.michalmatu.aicallbridge.shizuku;

import android.os.ParcelFileDescriptor;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Diagnostic-only owner for app-side RX drain and TX digital-silence workers.
 *
 * <p>The owner has exclusive responsibility for closing both streams and joining both workers.
 * Probe code must close it from a finally path after ownership has transferred.</p>
 */
final class ShizukuBidirectionalProbeMedia implements AutoCloseable {
    private static final long THREAD_JOIN_TIMEOUT_MS = 1_000L;

    private final InputStream downlinkInput;
    private final OutputStream uplinkOutput;
    private final int chunkBytes;
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean downlinkClosed = new AtomicBoolean(false);
    private final AtomicBoolean uplinkClosed = new AtomicBoolean(false);
    private final AtomicLong downlinkBytes = new AtomicLong();
    private final AtomicLong uplinkBytes = new AtomicLong();
    private final AtomicReference<String> downlinkTerminal = new AtomicReference<>("none");
    private final AtomicReference<String> uplinkTerminal = new AtomicReference<>("none");
    private final Thread downlinkThread;
    private final Thread uplinkThread;

    private ShizukuBidirectionalProbeMedia(
        InputStream downlinkInput,
        OutputStream uplinkOutput,
        int chunkBytes,
        String threadPrefix
    ) {
        this.downlinkInput = Objects.requireNonNull(downlinkInput, "downlinkInput");
        this.uplinkOutput = Objects.requireNonNull(uplinkOutput, "uplinkOutput");
        if (chunkBytes <= 0) {
            throw new IllegalArgumentException("chunkBytes must be > 0");
        }
        this.chunkBytes = chunkBytes;
        Objects.requireNonNull(threadPrefix, "threadPrefix");

        downlinkThread = new Thread(this::runDownlink, threadPrefix + "-rx-drain");
        downlinkThread.setDaemon(true);
        uplinkThread = new Thread(this::runUplink, threadPrefix + "-tx-silence");
        uplinkThread.setDaemon(true);
    }

    static ShizukuBidirectionalProbeMedia start(
        ParcelFileDescriptor downlink,
        ParcelFileDescriptor uplink,
        int chunkBytes,
        String threadPrefix
    ) {
        Objects.requireNonNull(downlink, "downlink");
        Objects.requireNonNull(uplink, "uplink");
        ParcelFileDescriptor.AutoCloseInputStream input =
            new ParcelFileDescriptor.AutoCloseInputStream(downlink);
        ParcelFileDescriptor.AutoCloseOutputStream output;
        try {
            output = new ParcelFileDescriptor.AutoCloseOutputStream(uplink);
        } catch (Throwable error) {
            closeQuietly(input);
            throw error;
        }
        return start(input, output, chunkBytes, threadPrefix);
    }

    static ShizukuBidirectionalProbeMedia start(
        InputStream downlinkInput,
        OutputStream uplinkOutput,
        int chunkBytes,
        String threadPrefix
    ) {
        ShizukuBidirectionalProbeMedia media = new ShizukuBidirectionalProbeMedia(
            downlinkInput,
            uplinkOutput,
            chunkBytes,
            threadPrefix
        );
        try {
            media.downlinkThread.start();
            media.uplinkThread.start();
            return media;
        } catch (Throwable error) {
            media.close();
            throw error;
        }
    }

    long downlinkBytes() {
        return downlinkBytes.get();
    }

    long uplinkBytes() {
        return uplinkBytes.get();
    }

    String downlinkTerminal() {
        return downlinkTerminal.get();
    }

    String uplinkTerminal() {
        return uplinkTerminal.get();
    }

    boolean threadsStopped() {
        return !downlinkThread.isAlive() && !uplinkThread.isAlive();
    }

    void closeDownlinkEndpoint() {
        if (downlinkClosed.compareAndSet(false, true)) {
            closeQuietly(downlinkInput);
        }
    }

    void closeUplinkEndpoint() {
        if (uplinkClosed.compareAndSet(false, true)) {
            closeQuietly(uplinkOutput);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        stop.set(true);
        closeDownlinkEndpoint();
        closeUplinkEndpoint();
        downlinkThread.interrupt();
        uplinkThread.interrupt();

        boolean interrupted = false;
        interrupted |= !joinQuietly(downlinkThread);
        interrupted |= !joinQuietly(uplinkThread);
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void runDownlink() {
        byte[] buffer = new byte[chunkBytes];
        try {
            while (!stop.get()) {
                int read = downlinkInput.read(buffer);
                if (read < 0) {
                    downlinkTerminal.set("eof");
                    return;
                }
                if (read > 0) {
                    downlinkBytes.addAndGet(read);
                }
            }
        } catch (Throwable error) {
            downlinkTerminal.set(error.getClass().getSimpleName());
        }
    }

    private void runUplink() {
        byte[] silence = new byte[chunkBytes];
        try {
            while (!stop.get()) {
                uplinkOutput.write(silence);
                uplinkBytes.addAndGet(silence.length);
            }
            uplinkOutput.flush();
        } catch (Throwable error) {
            uplinkTerminal.set(error.getClass().getSimpleName());
        }
    }

    private static boolean joinQuietly(Thread thread) {
        try {
            thread.join(THREAD_JOIN_TIMEOUT_MS);
            return true;
        } catch (InterruptedException ignored) {
            return false;
        }
    }

    private static void closeQuietly(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Throwable ignored) {
            // Diagnostic cleanup must continue for the sibling resource/thread.
        }
    }
}
