package pl.michalmatu.aicallbridge.realtime

import pl.michalmatu.aicallbridge.audio.PcmFrame

interface RealtimeTransport {
    suspend fun connect(config: RealtimeSessionConfig): Result<Unit>

    /** Non-blocking local enqueue of one already-adapted Realtime PCM frame. */
    fun sendAudio(frame: PcmFrame): Result<Unit>

    /** Non-blocking local cancellation request for the current model response. */
    fun cancelResponse(): Result<Unit>

    /**
     * Non-blocking function result submission. Transports that do not support function tools fail
     * closed by default instead of silently dropping an approval/business-logic result.
     */
    fun submitFunctionOutput(callId: String, outputJson: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Realtime function output is not supported"))

    /** Immediate local transport teardown; implementations must not await remote acknowledgement. */
    fun close()

    fun setListener(listener: Listener?)

    interface Listener {
        fun onAudio(frame: PcmFrame)
        fun onRemoteSpeechStarted()
        fun onRemoteSpeechStopped()
        fun onFunctionCall(call: RealtimeFunctionCall) = Unit
        fun onError(error: Throwable)
    }
}

data class RealtimeSessionConfig(
    val sessionEndpoint: String,
    val clientSecret: RealtimeClientSecret,
    val model: String,
    val instructions: String,
    val tools: List<RealtimeFunctionTool> = emptyList(),
) {
    init {
        require(sessionEndpoint.isNotBlank()) { "sessionEndpoint must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(instructions.isNotBlank()) { "instructions must not be blank" }
        require(tools.map { it.name }.distinct().size == tools.size) {
            "Realtime function tool names must be unique"
        }
    }
}
