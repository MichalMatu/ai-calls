package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

public final class TelephonyPcmStreamFramerTest {
    private static final PcmFormat TELEPHONY = new PcmFormat(16_000, 1, 16);

    @Test
    public void accumulatesShortReadsIntoOneTwentyMsFrame() throws Exception {
        byte[] source = pattern(TelephonyPcmStreamFramer.FRAME_BYTES_20_MS);
        TelephonyPcmStreamFramer framer = new TelephonyPcmStreamFramer();

        PcmFrame frame = framer.read20MsFrame(new ShortReadInputStream(source, 73), 123L);

        assertEquals(TELEPHONY, frame.getFormat());
        assertEquals(123L, frame.getMonotonicTimestampNs());
        assertArrayEquals(source, frame.getData());
    }

    @Test
    public void cleanEofBetweenFramesReturnsNull() throws Exception {
        TelephonyPcmStreamFramer framer = new TelephonyPcmStreamFramer();

        assertNull(framer.read20MsFrame(new ByteArrayInputStream(new byte[0]), 1L));
    }

    @Test
    public void partialFrameEofIsRejectedInsteadOfSendingCorruptPcm() {
        TelephonyPcmStreamFramer framer = new TelephonyPcmStreamFramer();
        byte[] partial = pattern(TelephonyPcmStreamFramer.FRAME_BYTES_20_MS - 2);

        assertThrows(
            EOFException.class,
            () -> framer.read20MsFrame(new ByteArrayInputStream(partial), 1L)
        );
    }

    @Test
    public void writesValidTelephonyPcmWithoutInventingSilenceOrReframing() throws Exception {
        TelephonyPcmStreamFramer framer = new TelephonyPcmStreamFramer();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] pcm = pattern(222);
        PcmFrame frame = new PcmFrame(TELEPHONY, pcm, 77L);

        framer.writeFrame(output, frame);

        assertArrayEquals(pcm, output.toByteArray());
    }

    @Test
    public void rejectsMalformedOrWrongFormatUplink() {
        TelephonyPcmStreamFramer framer = new TelephonyPcmStreamFramer();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThrows(
            IllegalArgumentException.class,
            () -> framer.writeFrame(
                output,
                new PcmFrame(new PcmFormat(24_000, 1, 16), new byte[] {0, 0}, 1L)
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> framer.writeFrame(output, new PcmFrame(TELEPHONY, new byte[] {1}, 1L))
        );
    }

    private static byte[] pattern(int count) {
        byte[] result = new byte[count];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (i * 31 + 7);
        }
        return result;
    }

    private static final class ShortReadInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final int maxRead;

        ShortReadInputStream(byte[] data, int maxRead) {
            delegate = new ByteArrayInputStream(data);
            this.maxRead = maxRead;
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, Math.min(length, maxRead));
        }
    }
}
