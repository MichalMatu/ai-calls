package pl.michalmatu.aicallbridge.session;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit 8 kHz G.711/RTP <-> 16 kHz PCM16LE adapter for app-owned VoIP.
 *
 * The frozen CallRealtimePcmBridge expects 16 kHz 20 ms frames; never make it
 * infer the actual RTP codec rate or silently change the Shizuku backend.
 * This is a simple voice-band resampler, not a high fidelity audio enhancer.
 */
public final class RtpPcm16kAdapterMediaPort
    implements AppOwnedVoipCallMediaSessionBackend.MediaPort, AutoCloseable {
    public static final int APP_PCM_RATE_HZ = TelephonyPcmStreamFramer.SAMPLE_RATE_HZ;
    private static final int RTP_FRAME_BYTES = RtpPcmuCodec.FRAME_PCM_BYTES;
    private static final int APP_FRAME_BYTES = TelephonyPcmStreamFramer.FRAME_BYTES_20_MS;

    private final AppOwnedVoipCallMediaSessionBackend.MediaPort delegate;

    public RtpPcm16kAdapterMediaPort(AppOwnedVoipCallMediaSessionBackend.MediaPort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override public Object activeIdentity() { return delegate.activeIdentity(); }

    @Override public boolean mediaHealthy(Object exactIdentity) {
        return delegate.mediaHealthy(exactIdentity);
    }

    @Override public CallMediaEndpointLease open(
        Object exactIdentity, long generation, int sampleRateHz
    ) {
        if (sampleRateHz != APP_PCM_RATE_HZ) {
            throw new IllegalArgumentException("voip_app_pcm_must_be_16000_hz");
        }
        CallMediaEndpointLease owned = Objects.requireNonNull(
            delegate.open(exactIdentity, generation, RtpPcmuCodec.SAMPLE_RATE_HZ),
            "voip_rtp_port_returned_null"
        );
        return new ResampledLease(owned);
    }

    @Override public void close() {
        if (delegate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception error) {
                throw new IllegalStateException("voip_rtp_port_close_failed", error);
            }
        }
    }

    private static final class ResampledLease implements CallMediaEndpointLease {
        private final CallMediaEndpointLease delegate;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final InputStream downlink = new InputStream() {
            private final byte[] narrow = new byte[RTP_FRAME_BYTES];
            private final byte[] wide = new byte[APP_FRAME_BYTES];
            private int wideOffset = APP_FRAME_BYTES;

            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                int count = read(one, 0, 1);
                return count < 0 ? -1 : one[0] & 0xff;
            }

            @Override public synchronized int read(byte[] dst, int offset, int length)
                throws IOException {
                Objects.checkFromIndexSize(offset, length, dst.length);
                if (length == 0) return 0;
                if (closed.get()) return -1;
                if (wideOffset == APP_FRAME_BYTES) {
                    int got = 0;
                    while (got < narrow.length) {
                        if (closed.get()) return -1;
                        int n = delegate.downlink().read(narrow, got, narrow.length - got);
                        if (n == -1) {
                            if (closed.get() || got == 0) return -1;
                            throw new EOFException("rtp_pcm_frame_truncated");
                        }
                        if (n == 0) continue;
                        got += n;
                    }
                    expand8kTo16k(narrow, wide);
                    wideOffset = 0;
                }
                if (closed.get()) return -1;
                int n = Math.min(length, APP_FRAME_BYTES - wideOffset);
                System.arraycopy(wide, wideOffset, dst, offset, n);
                wideOffset += n;
                if (wideOffset == APP_FRAME_BYTES) {
                    Arrays.fill(narrow, (byte) 0);
                    Arrays.fill(wide, (byte) 0);
                }
                return n;
            }

            @Override public void close() { ResampledLease.this.close(); }
        };

        private final OutputStream uplink = new OutputStream() {
            private final byte[] wide = new byte[APP_FRAME_BYTES];
            private final byte[] narrow = new byte[RTP_FRAME_BYTES];
            private int count;

            @Override public void write(int value) throws IOException {
                byte[] one = {(byte) value};
                write(one, 0, 1);
            }

            @Override public synchronized void write(byte[] bytes, int offset, int length)
                throws IOException {
                Objects.checkFromIndexSize(offset, length, bytes.length);
                int cursor = offset;
                int end = offset + length;
                while (cursor < end) {
                    if (closed.get()) throw new IOException("voip_media_stopped");
                    int n = Math.min(end - cursor, wide.length - count);
                    System.arraycopy(bytes, cursor, wide, count, n);
                    count += n;
                    cursor += n;
                    if (count == wide.length) {
                        reduce16kTo8k(wide, narrow);
                        if (closed.get()) throw new IOException("voip_media_stopped");
                        delegate.uplink().write(narrow);
                        count = 0;
                        Arrays.fill(wide, (byte) 0);
                        Arrays.fill(narrow, (byte) 0);
                    }
                }
            }

            @Override public void flush() { /* Never emit partial 20 ms frames. */ }
            @Override public void close() { ResampledLease.this.close(); }
        };

        ResampledLease(CallMediaEndpointLease delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override public InputStream downlink() { return downlink; }
        @Override public OutputStream uplink() { return uplink; }

        @Override public void close() {
            if (closed.compareAndSet(false, true)) {
                delegate.close(); // Unblocks the narrow-band RTP read immediately.
            }
        }
    }

    /** Exact twofold interpolation; preserve full 20 ms frame alignment. */
    public static void expand8kTo16k(byte[] narrow, byte[] wide) {
        if (narrow == null || wide == null
            || narrow.length != RTP_FRAME_BYTES || wide.length != APP_FRAME_BYTES) {
            throw new IllegalArgumentException("invalid_rtp_resample_frame");
        }
        for (int i = 0; i < RtpPcmuCodec.FRAME_SAMPLES; i++) {
            int current = readLe16(narrow, i);
            int next = i + 1 < RtpPcmuCodec.FRAME_SAMPLES
                ? readLe16(narrow, i + 1) : current;
            writeLe16(wide, i * 2, current);
            writeLe16(wide, i * 2 + 1, (current + next) / 2);
        }
    }

    /** Two-tap low-pass decimation; does not claim wideband speech quality. */
    public static void reduce16kTo8k(byte[] wide, byte[] narrow) {
        if (wide == null || narrow == null
            || narrow.length != RTP_FRAME_BYTES || wide.length != APP_FRAME_BYTES) {
            throw new IllegalArgumentException("invalid_rtp_resample_frame");
        }
        for (int i = 0; i < RtpPcmuCodec.FRAME_SAMPLES; i++) {
            int x = readLe16(wide, i * 2);
            int y = readLe16(wide, i * 2 + 1);
            writeLe16(narrow, i, (x + y) / 2);
        }
    }

    private static int readLe16(byte[] bytes, int sampleIndex) {
        int offset = sampleIndex * 2;
        return (short) (((bytes[offset + 1] & 0xff) << 8) | (bytes[offset] & 0xff));
    }

    private static void writeLe16(byte[] bytes, int sampleIndex, int value) {
        int offset = sampleIndex * 2;
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }
}
