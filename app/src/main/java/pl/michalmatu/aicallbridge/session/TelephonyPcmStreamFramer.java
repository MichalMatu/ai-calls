package pl.michalmatu.aicallbridge.session;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

/** Pure-Java framing boundary for the production mono PCM16LE 16 kHz telephony streams. */
public final class TelephonyPcmStreamFramer {
    public static final int SAMPLE_RATE_HZ = 16_000;
    public static final int FRAME_DURATION_MS = 20;
    public static final int FRAME_BYTES_20_MS = SAMPLE_RATE_HZ * 2 * FRAME_DURATION_MS / 1000;

    private static final PcmFormat FORMAT = new PcmFormat(SAMPLE_RATE_HZ, 1, 16);

    /**
     * Reads exactly one 20 ms frame. Returns null only for clean EOF before any byte of a frame.
     */
    public PcmFrame read20MsFrame(InputStream input, long monotonicTimestampNs) throws IOException {
        Objects.requireNonNull(input, "input");
        byte[] data = new byte[FRAME_BYTES_20_MS];
        int offset = 0;
        while (offset < data.length) {
            int count = input.read(data, offset, data.length - offset);
            if (count < 0) {
                if (offset == 0) {
                    return null;
                }
                throw new EOFException(
                    "telephony downlink ended mid-frame after " + offset + " of " + data.length + " bytes"
                );
            }
            if (count == 0) {
                continue;
            }
            offset += count;
        }
        return new PcmFrame(FORMAT, data, monotonicTimestampNs);
    }

    /** Writes caller-provided telephony PCM as-is after validating the frozen media format. */
    public void writeFrame(OutputStream output, PcmFrame frame) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(frame, "frame");
        PcmFormat format = Objects.requireNonNull(frame.getFormat(), "frame.format");
        byte[] data = Objects.requireNonNull(frame.getData(), "frame.data");
        if (format.getSampleRateHz() != SAMPLE_RATE_HZ
            || format.getChannels() != 1
            || format.getBitsPerSample() != 16) {
            throw new IllegalArgumentException("telephony uplink must be mono PCM16LE at 16000 Hz");
        }
        if ((data.length & 1) != 0) {
            throw new IllegalArgumentException("telephony uplink must contain whole PCM16 samples");
        }
        output.write(data);
    }
}
