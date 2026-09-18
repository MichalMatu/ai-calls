package pl.michalmatu.aicallbridge.session;

/** App-owned lease for one transferred bidirectional media endpoint pair. */
public interface CallMediaEndpointLease extends AutoCloseable {
    @Override
    void close();
}
