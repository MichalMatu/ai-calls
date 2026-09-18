package pl.michalmatu.aicallbridge.realtime

import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFormat
import pl.michalmatu.aicallbridge.audio.PcmFrame

class RealtimeEventTraceTest {
    @Test
    fun traceIsBoundedAndUsesAliasesInsteadOfRawIdentityOrContent() {
        var now = 1_000_000_000L
        val trace = RealtimeEventTrace(maxEvents = 3, monotonicNs = { now.also { now += 1_000_000L } })
        val part = RealtimeOutputPartId("resp-secret", "item-secret", 0, 1)

        trace.record(RealtimeTraceEventType.CONNECT_START)
        trace.record(RealtimeTraceEventType.OUTPUT_AUDIO, partId = part, byteCount = 960)
        trace.record(RealtimeTraceEventType.OUTPUT_TRANSCRIPT_DONE, partId = part, charCount = 27)
        trace.record(
            RealtimeTraceEventType.FUNCTION_CALL,
            responseId = "resp-secret",
            callId = "call-secret",
            functionName = "evaluate_proposal",
        )

        val snapshot = trace.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals(listOf(2L, 3L, 4L), snapshot.map { it.sequence })
        assertEquals("R1", snapshot[0].responseAlias)
        assertEquals("I1", snapshot[0].itemAlias)
        assertEquals("R1", snapshot[2].responseAlias)
        assertEquals("C1", snapshot[2].callAlias)

        val rendered = trace.renderCompact()
        assertFalse(rendered.contains("resp-secret"))
        assertFalse(rendered.contains("item-secret"))
        assertFalse(rendered.contains("call-secret"))
        assertTrue(rendered.contains("fn=evaluate_proposal"))
        assertTrue(rendered.contains("chars=27"))
    }

    @Test
    fun tracingTransportRecordsLifecycleWithoutTranscriptArgumentsOrPcm() {
        var now = 0L
        val trace = RealtimeEventTrace(monotonicNs = { now.also { now += 1_000_000L } })
        val delegate = FakeTransport()
        val transport = TracingRealtimeTransport(delegate, trace)
        val listener = RecordingListener()
        transport.setListener(listener)

        assertTrue(runSuspend { transport.connect(config()) }.isSuccess)
        val part = RealtimeOutputPartId("resp-raw", "item-raw", 0, 0)
        val frame = PcmFrame(PcmFormat(24_000, 1, 16), byteArrayOf(1, 2, 3, 4), 5L)
        delegate.currentListener!!.onRemoteSpeechStarted()
        delegate.currentListener!!.onOutputAudio(part, frame)
        delegate.currentListener!!.onOutputAudioTranscriptDelta(part, "private transcript")
        delegate.currentListener!!.onOutputAudioTranscriptDone(part, "private transcript")
        delegate.currentListener!!.onOutputAudioDone(part)
        delegate.currentListener!!.onFunctionCall(
            RealtimeFunctionCall("call-raw", "evaluate_proposal", "{\"secret\":\"private arguments\"}", "resp-raw"),
        )
        delegate.currentListener!!.onResponseDone("resp-raw", RealtimeResponseStatus.COMPLETED)
        assertTrue(transport.submitFunctionOutput("call-raw", "{\"private\":true}").isSuccess)
        transport.close()

        val rendered = trace.renderCompact()
        assertTrue(rendered.contains("CONNECT_SUCCESS"))
        assertTrue(rendered.contains("REMOTE_SPEECH_STARTED"))
        assertTrue(rendered.contains("OUTPUT_AUDIO r=R1 i=I1"))
        assertTrue(rendered.contains("bytes=4"))
        assertTrue(rendered.contains("chars=18"))
        assertTrue(rendered.contains("FUNCTION_CALL r=R1 call=C1 fn=evaluate_proposal"))
        assertTrue(rendered.contains("RESPONSE_DONE r=R1 status=COMPLETED"))
        assertFalse(rendered.contains("resp-raw"))
        assertFalse(rendered.contains("item-raw"))
        assertFalse(rendered.contains("call-raw"))
        assertFalse(rendered.contains("private transcript"))
        assertFalse(rendered.contains("private arguments"))
        assertEquals(1, listener.audioFrames)
        assertEquals(1, listener.functionCalls)
    }

    @Test
    fun identityAliasTableIsBounded() {
        val trace = RealtimeEventTrace(maxIdentityAliases = 1)
        trace.record(RealtimeTraceEventType.RESPONSE_DONE, responseId = "first", status = RealtimeResponseStatus.COMPLETED)
        trace.record(RealtimeTraceEventType.RESPONSE_DONE, responseId = "second", status = RealtimeResponseStatus.COMPLETED)

        assertEquals("R1", trace.snapshot()[0].responseAlias)
        assertEquals("R?", trace.snapshot()[1].responseAlias)
    }

    @Test
    fun untrustedDiagnosticLabelsCannotInjectTraceContent() {
        val trace = RealtimeEventTrace()
        val injectedFunctionName = "evaluate_proposal\nsecret=leak"
        val oversizedErrorType = "X".repeat(512)

        trace.record(
            RealtimeTraceEventType.FUNCTION_CALL,
            functionName = injectedFunctionName,
            errorType = oversizedErrorType,
        )

        val event = trace.snapshot().single()
        assertEquals("REDACTED", event.functionName)
        assertEquals("REDACTED", event.errorType)
        val rendered = trace.renderCompact()
        assertFalse(rendered.contains("secret=leak"))
        assertFalse(rendered.contains(oversizedErrorType))
        assertFalse(rendered.contains('\n'))
        assertTrue(rendered.contains("fn=REDACTED"))
        assertTrue(rendered.contains("error=REDACTED"))
    }

    @Test
    fun functionCallDebugRenderingCannotInjectUntrustedName() {
        val call = RealtimeFunctionCall(
            callId = "call-safe",
            name = "commit_proposal\nsecret=leak",
            argumentsJson = "{}",
            responseId = "resp-safe",
        )

        val rendered = call.toString()
        assertFalse(rendered.contains("secret=leak"))
        assertFalse(rendered.contains('\n'))
        assertTrue(rendered.contains("name=REDACTED"))
        assertFalse(rendered.contains("call-safe"))
        assertFalse(rendered.contains("resp-safe"))
    }

    private class FakeTransport : RealtimeTransport {
        var currentListener: RealtimeTransport.Listener? = null

        override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> = Result.success(Unit)
        override fun sendAudio(frame: PcmFrame): Result<Unit> = Result.success(Unit)
        override fun cancelResponse(): Result<Unit> = Result.success(Unit)
        override fun submitFunctionOutput(callId: String, outputJson: String): Result<Unit> = Result.success(Unit)
        override fun close() = Unit
        override fun setListener(listener: RealtimeTransport.Listener?) {
            currentListener = listener
        }
    }

    private class RecordingListener : RealtimeTransport.Listener {
        var audioFrames = 0
        var functionCalls = 0

        override fun onAudio(frame: PcmFrame) = Unit
        override fun onOutputAudio(partId: RealtimeOutputPartId, frame: PcmFrame) {
            audioFrames++
        }
        override fun onRemoteSpeechStarted() = Unit
        override fun onRemoteSpeechStopped() = Unit
        override fun onFunctionCall(call: RealtimeFunctionCall) {
            functionCalls++
        }
        override fun onError(error: Throwable) = Unit
    }

    private fun config() = RealtimeSessionConfig(
        sessionEndpoint = "wss://api.openai.com/v1/realtime",
        clientSecret = RealtimeClientSecret("ek_test_secret", Long.MAX_VALUE),
        model = "gpt-realtime",
        instructions = "test",
    )

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : kotlin.coroutines.Continuation<T> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) {
                result = value
            }
        })
        return result!!.getOrThrow()
    }
}
