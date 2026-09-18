package pl.michalmatu.aicallbridge.localspeech

import kotlin.math.roundToInt

internal data class DecodedPcm16(
    val sampleRate: Int,
    val sourceChannelCount: Int,
    val monoSamples: ShortArray,
)

internal object LocalSpeechPcm {
    fun decodeWaveToMonoPcm16(bytes: ByteArray): DecodedPcm16 {
        require(bytes.size >= 12) { "wave_too_short" }
        require(ascii(bytes, 0, 4) == "RIFF") { "wave_missing_riff" }
        require(ascii(bytes, 8, 4) == "WAVE") { "wave_missing_wave" }

        var offset = 12
        var formatCode: Int? = null
        var channelCount: Int? = null
        var sampleRate: Int? = null
        var bitsPerSample: Int? = null
        var dataOffset: Int? = null
        var dataLength: Int? = null

        while (offset + 8 <= bytes.size) {
            val chunkId = ascii(bytes, offset, 4)
            val chunkLength = u32le(bytes, offset + 4)
            val payload = offset + 8
            require(chunkLength >= 0 && payload + chunkLength <= bytes.size) { "wave_chunk_out_of_bounds" }

            when (chunkId) {
                "fmt " -> {
                    require(chunkLength >= 16) { "wave_fmt_too_short" }
                    formatCode = u16le(bytes, payload)
                    channelCount = u16le(bytes, payload + 2)
                    sampleRate = u32le(bytes, payload + 4)
                    bitsPerSample = u16le(bytes, payload + 14)
                }
                "data" -> {
                    dataOffset = payload
                    dataLength = chunkLength
                    break
                }
            }
            offset = payload + chunkLength + (chunkLength and 1)
        }

        require(formatCode == 1) { "wave_not_pcm" }
        val channels = requireNotNull(channelCount) { "wave_missing_channels" }
        require(channels > 0) { "wave_invalid_channels" }
        val rate = requireNotNull(sampleRate) { "wave_missing_sample_rate" }
        require(rate > 0) { "wave_invalid_sample_rate" }
        require(bitsPerSample == 16) { "wave_not_pcm16" }
        val start = requireNotNull(dataOffset) { "wave_missing_data" }
        val length = requireNotNull(dataLength) { "wave_missing_data_length" }
        val frameBytes = channels * 2
        require(length % frameBytes == 0) { "wave_partial_frame" }

        val frames = length / frameBytes
        val mono = ShortArray(frames)
        var cursor = start
        for (frame in 0 until frames) {
            var sum = 0L
            repeat(channels) {
                sum += s16le(bytes, cursor).toLong()
                cursor += 2
            }
            mono[frame] = (sum / channels).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
        }
        return DecodedPcm16(rate, channels, mono)
    }

    fun resampleLinear(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        require(fromRate > 0 && toRate > 0) { "invalid_sample_rate" }
        if (samples.isEmpty()) return ShortArray(0)
        if (fromRate == toRate) return samples.copyOf()
        if (samples.size == 1) return shortArrayOf(samples[0])

        val outputSize = ((samples.size.toDouble() * toRate) / fromRate).roundToInt().coerceAtLeast(1)
        val output = ShortArray(outputSize)
        for (index in output.indices) {
            val sourcePosition = index.toDouble() * fromRate / toRate
            val left = sourcePosition.toInt().coerceIn(0, samples.lastIndex)
            val right = (left + 1).coerceAtMost(samples.lastIndex)
            val fraction = sourcePosition - left
            val interpolated = samples[left] + (samples[right] - samples[left]) * fraction
            output[index] = interpolated.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return output
    }

    fun toLittleEndianBytes(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            val value = sample.toInt()
            out[index * 2] = (value and 0xff).toByte()
            out[index * 2 + 1] = ((value ushr 8) and 0xff).toByte()
        }
        return out
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        bytes.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun u16le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun u32le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun s16le(bytes: ByteArray, offset: Int): Short = u16le(bytes, offset).toShort()
}
