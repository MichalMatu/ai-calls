package pl.michalmatu.aicallbridge.textagent

internal enum class Gemma4ModelOperationState {
    IDLE,
    IMPORTING,
    DOWNLOADING,
}

/** Serializes large model mutations without becoming model identity/activation authority. */
internal class Gemma4ModelOperationGate {
    private val lock = Any()
    private var current = Gemma4ModelOperationState.IDLE

    fun state(): Gemma4ModelOperationState = synchronized(lock) { current }

    fun tryBeginImport(): Boolean = tryBegin(Gemma4ModelOperationState.IMPORTING)

    fun finishImport() = finish(Gemma4ModelOperationState.IMPORTING)

    fun tryBeginDownload(): Boolean = tryBegin(Gemma4ModelOperationState.DOWNLOADING)

    fun finishDownload() = finish(Gemma4ModelOperationState.DOWNLOADING)

    private fun tryBegin(next: Gemma4ModelOperationState): Boolean = synchronized(lock) {
        if (current != Gemma4ModelOperationState.IDLE) return@synchronized false
        current = next
        true
    }

    private fun finish(expected: Gemma4ModelOperationState) {
        synchronized(lock) {
            if (current == expected) current = Gemma4ModelOperationState.IDLE
        }
    }
}
