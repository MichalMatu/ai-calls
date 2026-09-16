package pl.michalmatu.aicallbridge.helper.samsung;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.util.Objects;

/**
 * Continuous remote-only cellular downlink media-plane session.
 *
 * <p>The privileged helper owns VOICE_DOWNLINK and the pipe write end. The controller receives the
 * read end once through {@link #takeReadEnd()}, then consumes raw mono PCM16LE without per-frame
 * Binder calls. Closing the controller read end stops capture locally.</p>
 *
 * <p>{@link #open(int)} remains intentionally context-free for the physically proven direct-shell
 * path. Privileged hosts that already execute inside an app-attributed process may use
 * {@link #open(Context, int)} to bind AudioRecord attribution explicitly.</p>
 */
public final class SamsungDownlinkPipeSession implements AutoCloseable {
    private final SamsungVoiceDownlinkCapture capture;
    private final ParcelFileDescriptor writeEnd;
    private final int readChunkFrames;
    private ParcelFileDescriptor readEnd;

    private Thread worker;
    private volatile Throwable terminalFailure;
    private volatile boolean started;
    private volatile boolean terminated;
    private volatile boolean aborted;

    private SamsungDownlinkPipeSession(
        SamsungVoiceDownlinkCapture capture,
        ParcelFileDescriptor readEnd,
        ParcelFileDescriptor writeEnd
    ) {
        this.capture = capture;
        this.readEnd = readEnd;
        this.writeEnd = writeEnd;
        this.readChunkFrames = capture.getSampleRate() / 50; // 20 ms.
    }

    public static SamsungDownlinkPipeSession open(int sampleRate) {
        return openWithCapture(SamsungVoiceDownlinkCapture.open(sampleRate));
    }

    public static SamsungDownlinkPipeSession open(Context attributionContext, int sampleRate) {
        Objects.requireNonNull(attributionContext, "attributionContext");
        return openWithCapture(
            SamsungVoiceDownlinkCapture.open(sampleRate, attributionContext)
        );
    }

    private static SamsungDownlinkPipeSession openWithCapture(
        SamsungVoiceDownlinkCapture capture
    ) {
        ParcelFileDescriptor[] pipe = null;
        try {
            pipe = ParcelFileDescriptor.createPipe();
            return new SamsungDownlinkPipeSession(capture, pipe[0], pipe[1]);
        } catch (Throwable error) {
            if (pipe != null) {
                closeQuietly(pipe[0]);
                closeQuietly(pipe[1]);
            }
            capture.abortNow();
            throw new IllegalStateException("failed to create downlink pipe", error);
        }
    }

    /** Transfers ownership of the app-facing read descriptor. May be called exactly once. */
    public synchronized ParcelFileDescriptor takeReadEnd() {
        ensureNotTerminated();
        if (readEnd == null) {
            throw new IllegalStateException("downlink pipe read end already taken");
        }
        ParcelFileDescriptor result = readEnd;
        readEnd = null;
        return result;
    }

    /** Starts telephony capture first, then starts the pipe writer. */
    public synchronized void start(Context context) throws InterruptedException {
        Objects.requireNonNull(context, "context");
        ensureNotTerminated();
        if (started) {
            throw new IllegalStateException("downlink pipe session already started");
        }

        try {
            capture.startAndConfirmTelephonyRoute(context);
        } catch (InterruptedException error) {
            terminateFromStartFailure(error);
            throw error;
        } catch (RuntimeException | Error error) {
            terminateFromStartFailure(error);
            throw error;
        }

        started = true;
        worker = new Thread(this::runWorker, "aicall-samsung-downlink");
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

    /** Immediate controller-death / takeover cleanup. */
    public void abortNow() {
        Thread thread;
        synchronized (this) {
            if (terminated) {
                return;
            }
            aborted = true;
            terminated = true;
            closeQuietly(writeEnd);
            closeQuietly(readEnd);
            readEnd = null;
            thread = worker;
        }
        capture.abortNow();
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean awaitTerminated(long timeoutMs) throws InterruptedException {
        if (timeoutMs < 0L) {
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
        short[] samples = new short[readChunkFrames];
        byte[] encoded = new byte[readChunkFrames * 2];
        try (
            ParcelFileDescriptor.AutoCloseOutputStream output =
                new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd)
        ) {
            while (!aborted) {
                int read = capture.read(samples, 0, samples.length);
                if (read < 0) {
                    throw new IllegalStateException("VOICE_DOWNLINK read failed: " + read);
                }
                if (read == 0) {
                    continue;
                }
                encodePcm16Le(samples, read, encoded);
                try {
                    output.write(encoded, 0, read * 2);
                } catch (IOException readerClosed) {
                    if (!aborted) {
                        finishGracefully();
                    }
                    return;
                }
            }
        } catch (Throwable error) {
            if (!aborted) {
                terminalFailure = error;
                failFromWorker();
            }
        }
    }

    private void terminateFromStartFailure(Throwable error) {
        terminalFailure = error;
        terminated = true;
        closeQuietly(writeEnd);
        closeQuietly(readEnd);
        readEnd = null;
        capture.abortNow();
    }

    private void finishGracefully() {
        synchronized (this) {
            if (terminated) {
                return;
            }
            terminated = true;
        }
        try {
            capture.stop();
        } catch (Throwable error) {
            terminalFailure = error;
            capture.abortNow();
        }
    }

    private void failFromWorker() {
        synchronized (this) {
            if (terminated) {
                return;
            }
            terminated = true;
        }
        capture.abortNow();
    }

    private synchronized void ensureNotTerminated() {
        if (terminated) {
            throw new IllegalStateException("downlink pipe session is terminated");
        }
    }

    private static void encodePcm16Le(short[] samples, int count, byte[] output) {
        for (int i = 0; i < count; i++) {
            int value = samples[i];
            output[i * 2] = (byte) (value & 0xff);
            output[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
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
