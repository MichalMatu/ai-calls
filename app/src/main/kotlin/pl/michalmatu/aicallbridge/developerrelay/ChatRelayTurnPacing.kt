package pl.michalmatu.aicallbridge.developerrelay

import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat

/**
 * Estimates when PCM enqueued into the telephony TX pipe has actually had time to play.
 *
 * Pipe writes are buffered and return almost immediately, so write()/flush() completion is not an
 * audible-playback completion signal. The developer relay keeps the media generation alive for the
 * PCM duration plus a small guard before listening again or tearing the session down.
 */
internal object ChatRelayTurnPacing {
    private const val PCM16_BYTES_PER_SAMPLE = 2L
    private const val PLAYBACK_GUARD_MS = 250L

    fun pcmDurationMs(
        pcmBytes: Int,
        sampleRateHz: Int = LocalSpeechFormat.SAMPLE_RATE_HZ,
    ): Long {
        require(pcmBytes >= 0) { "pcm_bytes_must_not_be_negative" }
        require(sampleRateHz > 0) { "sample_rate_must_be_positive" }
        return pcmBytes.toLong() * 1_000L /
            (sampleRateHz.toLong() * PCM16_BYTES_PER_SAMPLE)
    }

    fun postTxHoldMs(
        pcmBytes: Int,
        sampleRateHz: Int = LocalSpeechFormat.SAMPLE_RATE_HZ,
    ): Long = pcmDurationMs(pcmBytes, sampleRateHz) + PLAYBACK_GUARD_MS

    fun postWriteHoldMs(
        pcmBytes: Int,
        txWriteWallMs: Long,
        sampleRateHz: Int = LocalSpeechFormat.SAMPLE_RATE_HZ,
    ): Long {
        require(txWriteWallMs >= 0L) { "tx_write_wall_ms_must_not_be_negative" }
        return maxOf(
            PLAYBACK_GUARD_MS,
            postTxHoldMs(pcmBytes, sampleRateHz) - txWriteWallMs,
        )
    }
}
