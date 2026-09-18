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
}
