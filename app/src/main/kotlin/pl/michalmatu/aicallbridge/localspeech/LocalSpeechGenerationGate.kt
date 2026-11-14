package pl.michalmatu.aicallbridge.localspeech

internal class LocalSpeechGenerationGate {
    private var generation = 0L

    @Synchronized
    fun begin(): Long {
        generation += 1L
        return generation
    }

    @Synchronized
    fun invalidate() {
        generation += 1L
    }

    @Synchronized
    fun isCurrent(candidate: Long): Boolean = generation == candidate

    /** Atomically claim a completed turn. A cancelled or superseded turn cannot publish output. */
    @Synchronized
    fun invalidateIfCurrent(candidate: Long): Boolean {
        if (generation != candidate) return false
        generation += 1L
        return true
    }
}
