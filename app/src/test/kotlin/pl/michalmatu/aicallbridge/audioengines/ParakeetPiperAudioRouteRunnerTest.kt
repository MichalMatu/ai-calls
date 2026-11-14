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

    @Test fun fixedAudioCorpusHasVersionAndStableDigest() {
        assertEquals("pl-audio-v1", ParakeetPiperFixedCorpus.ID)
        assertEquals(2, ParakeetPiperFixedCorpus.REPEATS)
        assertEquals(listOf("greeting", "appointment", "correction", "long", "goodbye"),
            ParakeetPiperFixedCorpus.cases.map { it.id })
        assertEquals(
            "fe64ca3f291656a7c067fb7adac35d956b66686485078f52725d50927e68e701",
            ParakeetPiperFixedCorpus.sha256)
        assertNull(ParakeetPiperFixedCorpus.find("private input"))
    }

    @Test fun runnerUsesExplicitFixedSyntheticPhrase() {
        val selected = ParakeetPiperFixedCorpus.find("goodbye")!!
        var actual = ""
        var outcome: ParakeetPiperAudioRouteResult? = null
        val output = object : SpeechOutputProvider {
            override fun synthesize(text: String, listener: SpeechOutputProvider.Listener) {
                actual = text
                listener.onPcm16Mono16k(ByteArray(32_000) { 5 })
            }
            override fun cancel() = Unit
            override fun close() = Unit
        }
        val input = object : SpeechInputProvider {
            private lateinit var callback: SpeechInputProvider.Listener
            override fun start(listener: SpeechInputProvider.Listener) {
                callback = listener
                listener.onReady()
            }
            override fun writePcm(bytes: ByteArray, offset: Int, length: Int) = true
            override fun finishInput() = callback.onFinalTranscript(selected.phrase)
            override fun cancel() = Unit
            override fun close() = Unit
        }
        ParakeetPiperAudioRouteRunner({ output }, { input }, { it() },
            phrase = selected.phrase).start { outcome = it }
        assertEquals(selected.phrase, actual)
        assertTrue(outcome?.passed == true)
        assertEquals(0, outcome?.wordErrors)
    }

    @Test fun piperFailurePreservesOnlyAllowedTechnicalReason() {
        assertEquals("tts_set_voice_failed",
            ParakeetPiperAudioRoutePolicy.safeTtsError("tts_set_voice_failed"))
        assertEquals("tts_synthesis_error_-1",
            ParakeetPiperAudioRoutePolicy.safeTtsError("tts_synthesis_error_-1"))
        assertEquals("tts_decode_failed",
            ParakeetPiperAudioRoutePolicy.safeTtsError("tts_decode_SecretException"))
        assertEquals("tts_synthesis_failed",
            ParakeetPiperAudioRoutePolicy.safeTtsError("private data 123456"))
        var result: ParakeetPiperAudioRouteResult? = null
        val output = object : SpeechOutputProvider {
            override fun synthesize(text: String, listener: SpeechOutputProvider.Listener) =
                listener.onError("tts_queue_failed")
            override fun cancel() = Unit
            override fun close() = Unit
        }
        ParakeetPiperAudioRouteRunner({ output }, { FakeInput() }, { it() })
            .start { result = it }
        assertFalse(result?.passed ?: true)
        assertEquals("tts_queue_failed", result?.code)
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
