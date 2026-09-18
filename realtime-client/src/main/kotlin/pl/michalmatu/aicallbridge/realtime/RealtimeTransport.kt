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

    /**
     * Function result submission with an explicit response-scoped follow-up policy.
     *
     * Legacy transports remain source compatible: the default only accepts [RealtimeFunctionFollowup.Auto]
     * and delegates to the original two-argument surface. Non-default follow-ups fail closed unless the
     * concrete transport implements them explicitly.
     */
    fun submitFunctionOutput(
        callId: String,
        outputJson: String,
        followup: RealtimeFunctionFollowup,
    ): Result<Unit> = when (followup) {
        RealtimeFunctionFollowup.Auto -> submitFunctionOutput(callId, outputJson)
        else -> Result.failure(
            UnsupportedOperationException("Realtime response-scoped function follow-up is not supported"),
        )
    }

    /** Immediate local transport teardown; implementations must not await remote acknowledgement. */
    fun close()

    fun setListener(listener: Listener?)

    interface Listener {
        /** Legacy output callback retained for transports/events without GA output-part identity. */
        fun onAudio(frame: PcmFrame)

        /**
         * Identified model audio belonging to one Realtime output content part.
         *
         * The default preserves source compatibility by forwarding to [onAudio]. Safety-sensitive
         * consumers can override this callback and correlate the PCM with transcript/done events.
         */
        fun onOutputAudio(partId: RealtimeOutputPartId, frame: PcmFrame) = onAudio(frame)

        /** Incremental transcript for the same identified output content part. */
        fun onOutputAudioTranscriptDelta(partId: RealtimeOutputPartId, delta: String) = Unit

        /** Final transcript for the same identified output content part. */
        fun onOutputAudioTranscriptDone(partId: RealtimeOutputPartId, transcript: String) = Unit

        /** Signals that audio generation for the identified output content part is complete. */
        fun onOutputAudioDone(partId: RealtimeOutputPartId) = Unit

        /** Final lifecycle outcome for one whole Realtime response. */
        fun onResponseDone(responseId: String, status: RealtimeResponseStatus) = Unit

        fun onRemoteSpeechStarted()
        fun onRemoteSpeechStopped()
        fun onFunctionCall(call: RealtimeFunctionCall) = Unit
        fun onError(error: Throwable)
    }
}

data class RealtimeSessionConfig @JvmOverloads constructor(
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
