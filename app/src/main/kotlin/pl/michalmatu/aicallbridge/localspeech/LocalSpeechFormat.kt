package pl.michalmatu.aicallbridge.localspeech

internal object LocalSpeechFormat {
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNEL_COUNT = 1
    const val BYTES_PER_SAMPLE = 2

    fun bytesForDurationMs(durationMs: Int): Int {
        require(durationMs >= 0) { "duration_must_be_non_negative" }
        return SAMPLE_RATE_HZ * CHANNEL_COUNT * BYTES_PER_SAMPLE * durationMs / 1_000
    }
}
