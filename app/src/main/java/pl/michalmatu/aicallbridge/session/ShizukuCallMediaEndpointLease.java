package pl.michalmatu.aicallbridge.session;

import android.os.ParcelFileDescriptor;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the two transferred PFD endpoints for one production media generation. */
public final class ShizukuCallMediaEndpointLease implements CallMediaEndpointLease {
    private final ParcelFileDescriptor.AutoCloseInputStream downlink;
    private final ParcelFileDescriptor.AutoCloseOutputStream uplink;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ShizukuCallMediaEndpointLease(
        ParcelFileDescriptor downlinkReadEnd,
        ParcelFileDescriptor uplinkWriteEnd
    ) {
        Objects.requireNonNull(downlinkReadEnd, "downlinkReadEnd");
        Objects.requireNonNull(uplinkWriteEnd, "uplinkWriteEnd");

        ParcelFileDescriptor.AutoCloseInputStream input =
            new ParcelFileDescriptor.AutoCloseInputStream(downlinkReadEnd);
        ParcelFileDescriptor.AutoCloseOutputStream output;
        try {
            output = new ParcelFileDescriptor.AutoCloseOutputStream(uplinkWriteEnd);
        } catch (Throwable error) {
            closeQuietly(input);
            closeQuietly(uplinkWriteEnd);
            throw error;
        }
        downlink = input;
        uplink = output;
    }

    /** Future Realtime pump reads remote-party mono PCM16LE here. The lease owns the stream. */
    public InputStream downlink() {
        return downlink;
    }

    /** Future Realtime pump writes mono PCM16LE here. The lease owns the stream. */
    public OutputStream uplink() {
        return uplink;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(downlink);
        closeQuietly(uplink);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Throwable ignored) {
            // Fail-safe cleanup must continue for the sibling endpoint.
        }
    }
}
