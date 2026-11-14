package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class ParakeetPiperAudioRouteRunnerTest {
    @Test fun onlyExplicitPiperWithParakeetAndVoiceIsAccepted() {
        val selected = AudioEngineProfile(
            input = SpeechInputId.PARAKEET_PL,
            ttsEnginePackage = PiperServiceContract.PACKAGE,
            voiceId = "pl_PL-gosia-medium",
        )
        assertTrue(ParakeetPiperAudioRoutePolicy.canProbe(selected))
        assertFalse(ParakeetPiperAudioRoutePolicy.canProbe(selected.copy(voiceId = null)))
        assertFalse(ParakeetPiperAudioRoutePolicy.canProbe(selected.copy(input = SpeechInputId.ANDROID_STT)))
        assertFalse(ParakeetPiperAudioRoutePolicy.canProbe(selected.copy(ttsEnginePackage = null)))
    }

    @Test fun syntheticSpeechProducesMetricsNotTranscript() {
        val output = FakeOutput()
        val input = FakeInput()
        var result: ParakeetPiperAudioRouteResult? = null
        val runner = ParakeetPiperAudioRouteRunner({ output }, { input }, { it() })
        runner.start { result = it }
        assertTrue(result?.passed == true)
        assertEquals("ok", result?.code)
        assertEquals(0, result?.wordErrors)
        assertTrue(input.writes > 0)
        assertTrue(output.closed)
        assertTrue(input.closed)
        assertFalse(result.toString().contains(ParakeetPiperAudioRoutePolicy.PHRASE))
    }

    @Test fun nativeFailureDoesNotLeakErrorContentOrClaimPass() {
        val input = FakeInput(fail = true)
        var result: ParakeetPiperAudioRouteResult? = null
        ParakeetPiperAudioRouteRunner({ FakeOutput() }, { input }, { it() })
            .start { result = it }
        assertFalse(result?.passed ?: true)
        assertEquals("parakeet_native_failed", result?.code)
        assertFalse(result.toString().contains("sensitive-voice-content"))
    }

    private class FakeOutput : SpeechOutputProvider {
        var closed = false
        override fun synthesize(text: String, listener: SpeechOutputProvider.Listener) {
            listener.onPcm16Mono16k(ByteArray(32_000) { 3 })
        }
        override fun cancel() = Unit
        override fun close() { closed = true }
    }

    private class FakeInput(private val fail: Boolean = false) : SpeechInputProvider {
        var writes = 0
        private lateinit var listener: SpeechInputProvider.Listener
        var closed = false
        override fun start(listener: SpeechInputProvider.Listener) {
            this.listener = listener
            listener.onReady()
        }
        override fun writePcm(bytes: ByteArray, offset: Int, length: Int): Boolean {
            writes++
            return true
        }
        override fun finishInput() {
            if (fail) listener.onError("sensitive-voice-content")
            else listener.onFinalTranscript(ParakeetPiperAudioRoutePolicy.PHRASE)
        }
        override fun cancel() = Unit
        override fun close() { closed = true }
    }
}
