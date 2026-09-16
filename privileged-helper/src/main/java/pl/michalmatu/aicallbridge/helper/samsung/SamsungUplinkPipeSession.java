package pl.michalmatu.aicallbridge.helper.samsung;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Continuous mono PCM16LE media-plane session for the proven Samsung CALL_ASSISTANT uplink.
 *
 * <p>The privileged helper owns the read side and the AudioTrack. The controller receives the
 * write side once through {@link #takeWriteEnd()}, then streams raw mono PCM16LE into the kernel
 * pipe. No per-frame Binder calls are required.</p>
 */
public final class SamsungUplinkPipeSession implements AutoCloseable {
    private static final int READ_BUFFER_BYTES = 8192;

    private final SamsungCallAssistantTrack track;
    private final ParcelFileDescriptor readEnd;
    private ParcelFileDescriptor writeEnd;

    private Thread worker;
    private volatile Throwable terminalFailure;
    private volatile boolean started;
    private volatile boolean terminated;
    private volatile boolean aborted;

    private SamsungUplinkPipeSession(
        SamsungCallAssistantTrack track,
        ParcelFileDescriptor readEnd,
        ParcelFileDescriptor writeEnd
    ) {
        this.track = track;
        this.readEnd = readEnd;
        this.writeEnd = writeEnd;
    }

    public static SamsungUplinkPipeSession open(Context context, int sampleRate) throws Exception {
        Objects.requireNonNull(context, "context");
        SamsungCallAssistantTrack track = SamsungCallAssistantTrack.open(context, sampleRate);
        ParcelFileDescriptor[] pipe = null;
        try {
            pipe = ParcelFileDescriptor.createPipe();
            return new SamsungUplinkPipeSession(track, pipe[0], pipe[1]);
        } catch (Throwable error) {
            if (pipe != null) {
                closeQuietly(pipe[0]);
                closeQuietly(pipe[1]);
            }
            track.abortNow();
            throw error;
        }
    }

    /**
     * Transfers ownership of the controller-facing write descriptor. May be called exactly once.
     * Closing the returned descriptor signals normal EOF to the helper worker.
     */
    public synchronized ParcelFileDescriptor takeWriteEnd() {
        ensureNotTerminated();
        if (writeEnd == null) {
            throw new IllegalStateException("uplink pipe write end already taken");
        }
        ParcelFileDescriptor result = writeEnd;
        writeEnd = null;
        return result;
    }

    /** Starts telephony routing first, then starts the pipe reader. */
    public synchronized void start() throws InterruptedException {
        ensureNotTerminated();
        if (started) {
            throw new IllegalStateException("uplink pipe session already started");
        }

        track.startAndConfirmTelephonyRoute();
        started = true;
        worker = new Thread(this::runWorker, "aicall-samsung-uplink");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public boolean wasAborted() {
        return aborted;
    }

    public Throwable getTerminalFailure() {
        return terminalFailure;
    }

    /**
     * Immediate takeover/fail-safe path. Closing the read descriptor unblocks a blocked read and
     * the AudioTrack is flushed/released without waiting for the controller or network.
     */
    public void abortNow() {
        Thread thread;
        synchronized (this) {
            if (terminated) {
                return;
            }
            aborted = true;
            terminated = true;
            closeQuietly(readEnd);
            closeQuietly(writeEnd);
            writeEnd = null;
            thread = worker;
        }
        track.abortNow();
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** Waits only for diagnostics/tests; normal control should not block on this path. */
    public boolean awaitTerminated(long timeoutMs) throws InterruptedException {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("timeoutMs must be >= 0");
        }
        Thread thread = worker;
        if (thread == null) {
            return terminated;
        }
        thread.join(timeoutMs);
        return terminated;
    }

    @Override
    public void close() {
        abortNow();
    }

    private void runWorker() {
        byte[] buffer = new byte[READ_BUFFER_BYTES + 1];
        boolean hasCarry = false;
        try (FileInputStream input = new FileInputStream(readEnd.getFileDescriptor())) {
            while (!aborted) {
                int offset = hasCarry ? 1 : 0;
                int count = input.read(buffer, offset, READ_BUFFER_BYTES);
                if (count < 0) {
                    if (hasCarry) {
                        throw new IllegalStateException("uplink PCM stream ended on half a PCM16 sample");
                    }
                    finishGracefully();
                    return;
                }
                if (count == 0) {
                    continue;
                }

                int available = count + offset;
                int evenBytes = available & ~1;
                if (evenBytes > 0) {
                    track.writeMonoPcm16Le(buffer, 0, evenBytes);
                }

                hasCarry = (available & 1) != 0;
                if (hasCarry) {
                    buffer[0] = buffer[available - 1];
                }
            }
        } catch (Throwable error) {
            if (!aborted) {
                terminalFailure = error;
                failFromWorker();
            }
        } finally {
            closeQuietly(readEnd);
        }
    }

    private void finishGracefully() {
        synchronized (this) {
            if (terminated) {
                return;
            }
            terminated = true;
        }
        try {
            track.stop();
        } catch (Throwable error) {
            terminalFailure = error;
            track.abortNow();
        }
    }

    private void failFromWorker() {
        synchronized (this) {
            if (terminated) {
                return;
            }
            terminated = true;
        }
        track.abortNow();
    }

    private synchronized void ensureNotTerminated() {
        if (terminated) {
            throw new IllegalStateException("uplink pipe session is terminated");
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (IOException ignored) {
            // Cleanup path.
        }
    }
}
