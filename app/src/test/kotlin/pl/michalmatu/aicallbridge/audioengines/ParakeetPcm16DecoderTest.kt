package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class ParakeetPcm16DecoderTest {
    @Test fun decodeSplitLittleEndianSamplesAndCarry() {
        val value = Pcm16FloatDecoder()
        assertTrue(value.convert(byteArrayOf(0x00, 0x00, 0x01)).contentEquals(floatArrayOf(0f)))
        assertTrue(value.hasIncompleteSample())
        val sample = value.convert(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0xff.toByte(), 0x7f))
        assertEquals(3, sample.size)
        assertEquals(1f / 32768f, sample[0], 0.00001f)
        assertEquals(-1f, sample[1], 0.00001f)
        assertEquals(32767f / 32768f, sample[2], 0.00001f)
        assertFalse(value.hasIncompleteSample())
    }


    @Test fun pcm16ChunkingProducesExactlyTheSameSamplesForAllPartitions() {
        val samples = shortArrayOf(
            Short.MIN_VALUE, (-16_384).toShort(), (-257).toShort(), (-1).toShort(),
            0, 1, 257, 16_384, Short.MAX_VALUE,
        )
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            val bits = sample.toInt()
            bytes[index * 2] = bits.toByte()
            bytes[index * 2 + 1] = (bits ushr 8).toByte()
        }
        val expected = FloatArray(samples.size) { samples[it].toInt() / 32768f }
        for (chunk in 1..(bytes.size + 2)) {
            val converter = Pcm16FloatDecoder()
            val actual = ArrayList<Float>()
            var start = 0
            while (start < bytes.size) {
                val end = minOf(start + chunk, bytes.size)
                converter.convert(bytes.copyOfRange(start, end)).forEach { actual.add(it) }
                start = end
            }
            assertArrayEquals("chunk size $chunk", expected, actual.toFloatArray(), 0f)
            assertFalse("chunk size $chunk leaves half a sample", converter.hasIncompleteSample())
        }
    }

    @Test fun oddTotalBytesRetainOneIncompleteSampleAcrossEmptyCallbacks() {
        val converter = Pcm16FloatDecoder()
        assertArrayEquals(floatArrayOf(), converter.convert(byteArrayOf(0x7f)), 0f)
        assertArrayEquals(floatArrayOf(), converter.convert(byteArrayOf()), 0f)
        assertTrue(converter.hasIncompleteSample())
        assertArrayEquals(floatArrayOf(32767f / 32768f),
            converter.convert(byteArrayOf(0x7f)), 0f)
        assertFalse(converter.hasIncompleteSample())
    }

    @Test fun deterministicFuzzedChunksNeverDuplicateOrDropSamples() {
        val input = ByteArray(8192) { ((it * 37 + 19) and 255).toByte() }
        val expected = FloatArray(input.size / 2) { i ->
            val bits = (input[i * 2].toInt() and 255) or
                ((input[i * 2 + 1].toInt() and 255) shl 8)
            bits.toShort().toInt() / 32768f
        }
        for (seed in 1..16) {
            val rng = java.util.Random(seed.toLong())
            val converter = Pcm16FloatDecoder()
            val decoded = ArrayList<Float>(expected.size)
            var offset = 0
            while (offset < input.size) {
                val count = minOf(input.size - offset, rng.nextInt(131) + 1)
                converter.convert(input.copyOfRange(offset, offset + count))
                    .forEach { decoded.add(it) }
                offset += count
            }
            assertArrayEquals("seed $seed", expected, decoded.toFloatArray(), 0f)
            assertFalse(converter.hasIncompleteSample())
        }
    }

    @Test fun modelIdIsExactOfficialStatefulFilename() {
        assertEquals("parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm", ParakeetModelFile.NAME)
    }
}
