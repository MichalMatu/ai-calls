package pl.michalmatu.aicallbridge.developerrelay

import java.io.OutputStream
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat

/**
 * Prevents a single multi-second, blocking PCM write from bypassing STOP or
 * live authorization. A queued stop is observed between 20 ms audio frames.
 *
 * This cannot retract frames already accepted by the cellular TX pipe.
 */
internal object AuthorizedPcmUplinkWriter {
    fun write(
        uplink: OutputStream,
        pcm: ByteArray,
        isAuthorized: () -> Boolean,
        frameBytes: Int = LocalSpeechFormat.bytesForDurationMs(20),
    ): Int {
        require(pcm.size % 2 == 0) { "invalid_pcm16_alignment" }
        require(frameBytes > 0 && frameBytes % 2 == 0) { "invalid_pcm_frame_size" }
        var sent = 0
        while (sent < pcm.size) {
            if (!isAuthorized()) return sent
            val length = minOf(frameBytes, pcm.size - sent)
            uplink.write(pcm, sent, length)
            sent += length
        }
        if (!isAuthorized()) return sent
        uplink.flush()
        return sent
    }
}
