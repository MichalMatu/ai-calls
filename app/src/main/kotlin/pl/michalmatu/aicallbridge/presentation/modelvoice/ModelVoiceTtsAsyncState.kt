package pl.michalmatu.aicallbridge.presentation.modelvoice

/** Main-thread TTS state. Android owns the engine, executor, rendering and playback. */
internal class ModelVoiceTtsAsyncState {
    data class QueuedPreview(val voiceId: String?)

    private var generation = 0
    var voicesReady: Boolean = false
        private set
    private var previewQueued = false
    private var queuedVoiceId: String? = null

    /** A new request fences older callbacks but retains the user's pending preview. */
    fun beginLoad(): Int {
        voicesReady = false
        return ++generation
    }

    fun accepts(loadGeneration: Int): Boolean = loadGeneration == generation

    fun markReady() {
        voicesReady = true
    }

    /** A null voice ID requests Android's automatic voice, not no preview. */
    fun queuePreview(voiceId: String?) {
        previewQueued = true
        queuedVoiceId = voiceId
    }

    fun takeQueuedPreview(): QueuedPreview? {
        if (!previewQueued) return null
        previewQueued = false
        return QueuedPreview(queuedVoiceId)
    }

    /** Pause and failed initialization cancel pending playback. */
    fun cancelPreview() {
        previewQueued = false
    }

    fun onEngineChanged() {
        cancelPreview()
        queuedVoiceId = null
    }

    /** Closing the owner invalidates all previously dispatched callbacks. */
    fun invalidate() {
        ++generation
    }
}
