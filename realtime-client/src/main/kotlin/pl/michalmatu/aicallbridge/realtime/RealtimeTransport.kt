package pl.michalmatu.aicallbridge.realtime

import pl.michalmatu.aicallbridge.audio.PcmFrame

interface RealtimeTransport {
    suspend fun connect(config: RealtimeSessionConfig): Result<Unit>

    /** Non-blocking local enqueue of one already-adapted Realtime PCM frame. */
    fun sendAudio(frame: PcmFrame): Result<Unit>

    /** Non-blocking local cancellation request for the current model response. */
    fun cancelResponse(): Result<Unit>

    /** Immediate local transport teardown; implementations must not await remote acknowledgement. */
    fun close()

    fun setListener(listener: Listener?)

    interface Listener {
        fun onAudio(frame: PcmFrame)
        fun onRemoteSpeechStarted()
        fun onRemoteSpeechStopped()
        fun onError(error: Throwable)
    }
}

data class RealtimeSessionConfig(
    val sessionEndpoint: String,
    val clientSecret: RealtimeClientSecret,
    val model: String,
    val instructions: String,
) {
    init {
        require(sessionEndpoint.isNotBlank()) { "sessionEndpoint must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(instructions.isNotBlank()) { "instructions must not be blank" }
    }
}
