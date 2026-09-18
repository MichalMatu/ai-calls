package pl.michalmatu.aicallbridge.session;

import java.io.InputStream;
import java.io.OutputStream;

/** App-owned lease for one transferred bidirectional media endpoint pair. */
public interface CallMediaEndpointLease extends AutoCloseable {
    /** Remote-party mono PCM16LE downlink owned by this lease. */
    InputStream downlink();

    /** Mono PCM16LE uplink toward the cellular call owned by this lease. */
    OutputStream uplink();

    @Override
    void close();
}
