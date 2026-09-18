package pl.michalmatu.aicallbridge.realtime

import pl.michalmatu.aicallbridge.audio.PcmFrame

interface RealtimeTransport {
    suspend fun connect(config: RealtimeSessionConfig): Result<Unit>

    suspend fun sendAudio(frame: PcmFrame): Result<Unit>

    suspend fun cancelResponse(): Result<Unit>

    suspend fun close()

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
