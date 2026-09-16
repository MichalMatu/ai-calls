package pl.michalmatu.aicallbridge.helper.samsung;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

public final class Pcm16PipeFramerTest {
    @Test
    public void chunkBytesFor20MsMatchesSupportedRates() {
        assertEquals(640, Pcm16PipeFramer.chunkBytesFor20Ms(16_000));
        assertEquals(1_920, Pcm16PipeFramer.chunkBytesFor20Ms(48_000));
    }

    @Test
    public void pumpPreservesPcmAcrossOddReadBoundaries() throws Exception {
        byte[] source = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        InputStream input = new FragmentedInputStream(source, new int[] {1, 3, 1, 2, 3});
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        Pcm16PipeFramer.pump(input, 4, () -> false, output::write);

        assertArrayEquals(source, output.toByteArray());
    }

    @Test
    public void pumpRejectsHalfSampleAtEof() {
        byte[] source = new byte[] {1, 2, 3};
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThrows(
            EOFException.class,
            () -> Pcm16PipeFramer.pump(
                new ByteArrayInputStream(source),
                4,
                () -> false,
                output::write
            )
        );
        assertArrayEquals(new byte[] {1, 2}, output.toByteArray());
    }

    @Test
    public void pumpStopsBeforeReadWhenAlreadyAborted() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Pcm16PipeFramer.pump(
            new ByteArrayInputStream(new byte[] {1, 2, 3, 4}),
            4,
            () -> true,
            output::write
        );
        assertEquals(0, output.size());
    }

    private static final class FragmentedInputStream extends InputStream {
        private final byte[] data;
        private final int[] fragments;
        private int position;
        private int fragmentIndex;

        FragmentedInputStream(byte[] data, int[] fragments) {
            this.data = data;
            this.fragments = fragments;
        }

        @Override
        public int read() throws IOException {
            if (position >= data.length) {
                return -1;
            }
            return data[position++] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (position >= data.length) {
                return -1;
            }
            int fragment = fragments[Math.min(fragmentIndex, fragments.length - 1)];
            fragmentIndex++;
            int count = Math.min(Math.min(fragment, length), data.length - position);
            System.arraycopy(data, position, buffer, offset, count);
            position += count;
            return count;
        }
    }
}
