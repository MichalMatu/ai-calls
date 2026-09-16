package pl.michalmatu.aicallbridge.helper.samsung;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Pure-Java PCM16LE stream framing used by the privileged uplink pipe worker. */
final class Pcm16PipeFramer {
    private static final int PCM16_BYTES_PER_SAMPLE = 2;
    private static final int TARGET_CHUNK_MS = 20;

    interface Sink {
        void write(byte[] data, int offset, int length);
    }

    private Pcm16PipeFramer() {}

    static int chunkBytesFor20Ms(int sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be > 0");
        }
        int bytes = sampleRate * PCM16_BYTES_PER_SAMPLE * TARGET_CHUNK_MS / 1000;
        return Math.max(PCM16_BYTES_PER_SAMPLE, bytes & ~1);
    }

    static void pump(
        InputStream input,
        int chunkBytes,
        BooleanSupplier aborted,
        Sink sink
    ) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(aborted, "aborted");
        Objects.requireNonNull(sink, "sink");
        if (chunkBytes < PCM16_BYTES_PER_SAMPLE || (chunkBytes & 1) != 0) {
            throw new IllegalArgumentException("chunkBytes must be even and >= 2");
        }

        byte[] buffer = new byte[chunkBytes + 1];
        boolean hasCarry = false;
        while (!aborted.getAsBoolean()) {
            int offset = hasCarry ? 1 : 0;
            int count = input.read(buffer, offset, chunkBytes);
            if (count < 0) {
                if (hasCarry) {
                    throw new EOFException("uplink PCM stream ended on half a PCM16 sample");
                }
                return;
            }
            if (count == 0) {
                continue;
            }

            int available = count + offset;
            int evenBytes = available & ~1;
            if (evenBytes > 0) {
                sink.write(buffer, 0, evenBytes);
            }

            hasCarry = (available & 1) != 0;
            if (hasCarry) {
                buffer[0] = buffer[available - 1];
            }
        }
    }
}
