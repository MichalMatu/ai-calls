package pl.michalmatu.aicallbridge.developerrelay

import java.io.IOException
import java.io.OutputStream
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat

/**
 * Prevents a single multi-second, blocking PCM write from bypassing STOP or
 * live authorization. A queued stop is observed between 20 ms audio frames.
 *
 * This cannot retract frames already accepted by the cellular TX pipe.
 */
internal enum class PcmUplinkWriteStatus {
    COMPLETE,
    REVOKED,
    IO_UNCERTAIN,
}

internal data class PcmUplinkWriteResult(
    val acceptedBytes: Int,
    val status: PcmUplinkWriteStatus,
) {
    val complete: Boolean get() = status == PcmUplinkWriteStatus.COMPLETE
}

internal object AuthorizedPcmUplinkWriter {
    /**
     * Legacy byte-count interface for existing probes. A byte count is NOT proof
     * of completion. Production code must use writeOutcome() and check complete.
     */
    fun write(
        uplink: OutputStream,
        pcm: ByteArray,
        isAuthorized: () -> Boolean,
        frameBytes: Int = LocalSpeechFormat.bytesForDurationMs(20),
    ): Int {
        val result = writeOutcome(uplink, pcm, isAuthorized, frameBytes)
        if (result.status == PcmUplinkWriteStatus.IO_UNCERTAIN) {
            throw IOException("pcm_uplink_write_uncertain")
        }
        return result.acceptedBytes
    }

    /**
     * Tracks only frames whose write() returned successfully. A failed write can
     * have accepted unknown additional bytes: never report it as completed.
     * Even accepting the final frame is insufficient if authorization was revoked
     * before flush or if flush failed.
     */
    fun writeOutcome(
        uplink: OutputStream,
        pcm: ByteArray,
        isAuthorized: () -> Boolean,
        frameBytes: Int = LocalSpeechFormat.bytesForDurationMs(20),
    ): PcmUplinkWriteResult {
        require(pcm.size % 2 == 0) { "invalid_pcm16_alignment" }
        require(frameBytes > 0 && frameBytes % 2 == 0) { "invalid_pcm_frame_size" }
        var accepted = 0
        while (accepted < pcm.size) {
            if (!runCatching(isAuthorized).getOrDefault(false)) {
                return PcmUplinkWriteResult(accepted, PcmUplinkWriteStatus.REVOKED)
            }
            val length = minOf(frameBytes, pcm.size - accepted)
            if (runCatching { uplink.write(pcm, accepted, length) }.isFailure) {
                return PcmUplinkWriteResult(accepted, PcmUplinkWriteStatus.IO_UNCERTAIN)
            }
            accepted += length
        }
        if (!runCatching(isAuthorized).getOrDefault(false)) {
            return PcmUplinkWriteResult(accepted, PcmUplinkWriteStatus.REVOKED)
        }
        if (runCatching { uplink.flush() }.isFailure) {
            return PcmUplinkWriteResult(accepted, PcmUplinkWriteStatus.IO_UNCERTAIN)
        }
        if (!runCatching(isAuthorized).getOrDefault(false)) {
            return PcmUplinkWriteResult(accepted, PcmUplinkWriteStatus.REVOKED)
        }
        return PcmUplinkWriteResult(accepted, PcmUplinkWriteStatus.COMPLETE)
    }
}
