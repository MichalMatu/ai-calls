package pl.michalmatu.aicallbridge.realtime

import pl.michalmatu.aicallbridge.audio.PcmFrame

/** Realtime transport decorator that records only bounded/redacted protocol metadata. */
class TracingRealtimeTransport(
    private val delegate: RealtimeTransport,
    private val trace: RealtimeEventTrace,
) : RealtimeTransport {
    private var downstreamListener: RealtimeTransport.Listener? = null

    override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> {
        trace.record(RealtimeTraceEventType.CONNECT_START)
        val result = delegate.connect(config)
        if (result.isSuccess) {
            trace.record(RealtimeTraceEventType.CONNECT_SUCCESS)
        } else {
            trace.record(
                RealtimeTraceEventType.CONNECT_FAILURE,
                errorType = result.exceptionOrNull()?.javaClass?.simpleName,
            )
        }
        return result
    }

    override fun sendAudio(frame: PcmFrame): Result<Unit> = delegate.sendAudio(frame)

    override fun cancelResponse(): Result<Unit> {
        trace.record(RealtimeTraceEventType.CANCEL_RESPONSE)
        return delegate.cancelResponse()
    }

    override fun submitFunctionOutput(callId: String, outputJson: String): Result<Unit> {
        trace.record(RealtimeTraceEventType.FUNCTION_OUTPUT, callId = callId)
        return delegate.submitFunctionOutput(callId, outputJson)
    }

    override fun submitFunctionOutput(
        callId: String,
        outputJson: String,
        followup: RealtimeFunctionFollowup,
    ): Result<Unit> {
        trace.record(RealtimeTraceEventType.FUNCTION_OUTPUT, callId = callId)
        return delegate.submitFunctionOutput(callId, outputJson, followup)
    }

    override fun close() {
        trace.record(RealtimeTraceEventType.CLOSED)
        delegate.close()
    }

    override fun setListener(listener: RealtimeTransport.Listener?) {
        downstreamListener = listener
        delegate.setListener(if (listener == null) null else tracingListener)
    }

    private val tracingListener = object : RealtimeTransport.Listener {
        override fun onAudio(frame: PcmFrame) {
            trace.record(
                RealtimeTraceEventType.OUTPUT_AUDIO_UNIDENTIFIED,
                byteCount = frame.data.size,
            )
            downstreamListener?.onAudio(frame)
        }

        override fun onOutputAudio(partId: RealtimeOutputPartId, frame: PcmFrame) {
            trace.record(
                RealtimeTraceEventType.OUTPUT_AUDIO,
                partId = partId,
                byteCount = frame.data.size,
            )
            downstreamListener?.onOutputAudio(partId, frame)
        }

        override fun onOutputAudioTranscriptDelta(partId: RealtimeOutputPartId, delta: String) {
            trace.record(
                RealtimeTraceEventType.OUTPUT_TRANSCRIPT_DELTA,
                partId = partId,
                charCount = delta.length,
            )
            downstreamListener?.onOutputAudioTranscriptDelta(partId, delta)
        }

        override fun onOutputAudioTranscriptDone(partId: RealtimeOutputPartId, transcript: String) {
            trace.record(
                RealtimeTraceEventType.OUTPUT_TRANSCRIPT_DONE,
                partId = partId,
                charCount = transcript.length,
            )
            downstreamListener?.onOutputAudioTranscriptDone(partId, transcript)
        }

        override fun onOutputAudioDone(partId: RealtimeOutputPartId) {
            trace.record(RealtimeTraceEventType.OUTPUT_AUDIO_DONE, partId = partId)
            downstreamListener?.onOutputAudioDone(partId)
        }

        override fun onResponseDone(responseId: String, status: RealtimeResponseStatus) {
            trace.record(
                RealtimeTraceEventType.RESPONSE_DONE,
                responseId = responseId,
                status = status,
            )
            downstreamListener?.onResponseDone(responseId, status)
        }

        override fun onRemoteSpeechStarted() {
            trace.record(RealtimeTraceEventType.REMOTE_SPEECH_STARTED)
            downstreamListener?.onRemoteSpeechStarted()
        }

        override fun onRemoteSpeechStopped() {
            trace.record(RealtimeTraceEventType.REMOTE_SPEECH_STOPPED)
            downstreamListener?.onRemoteSpeechStopped()
        }

        override fun onFunctionCall(call: RealtimeFunctionCall) {
            trace.record(
                RealtimeTraceEventType.FUNCTION_CALL,
                responseId = call.responseId,
                callId = call.callId,
                functionName = call.name,
            )
            downstreamListener?.onFunctionCall(call)
        }

        override fun onError(error: Throwable) {
            trace.record(
                RealtimeTraceEventType.ERROR,
                errorType = error.javaClass.simpleName,
            )
            downstreamListener?.onError(error)
        }
    }
}
