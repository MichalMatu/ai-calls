package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.NativeConversationContextBackend
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class GemmaAudioCorpusRunnerTest {
    @Test fun threeSyntheticTurnsShareOneModelAndResetTheirHistory() {
        val model = FakeModel()
        val output = FakeOutput()
        var results: List<GemmaAudioCorpusCase>? = null
        val runner = GemmaAudioCorpusRunner(model, { output }, { it() })
        runner.start { results = it }
        assertEquals(1, model.prewarms)
        assertEquals(3, model.turns)
        assertEquals(3, model.resets)
        assertEquals(1, model.closes)
        assertEquals(3, results?.size)
        assertTrue(results.orEmpty().all { it.passed && it.code == "ok" })
        assertEquals(3, output.synthesis)
    }

    @Test fun oneNativeErrorDoesNotSkipRemainingCasesOrLeakContent() {
        val model = FakeModel(failAt = 2)
        val runner = GemmaAudioCorpusRunner(model, { FakeOutput() }, { it() })
        var results: List<GemmaAudioCorpusCase>? = null
        runner.start { results = it }
        assertEquals(listOf(true, false, true), results?.map { it.passed })
        assertEquals("native_error", results?.get(1)?.code)
        assertEquals(3, model.turns)
        assertEquals(3, model.resets)
    }

    @Test fun transcriptOnlyDecodingRejectsOtherFieldsAndBadTypes() {
        assertEquals("Proszę powtórzyć ostatnią informację.",
            GemmaAudioCorpus.decodeTranscriptOnly("""{"transcript":"Proszę powtórzyć ostatnią informację."}"""))
        assertNull(GemmaAudioCorpus.decodeTranscriptOnly("""{"transcript":42}"""))
        assertNull(GemmaAudioCorpus.decodeTranscriptOnly("""{"transcript":"ok","tool":"end_call"}"""))
        assertNull(GemmaAudioCorpus.decodeTranscriptOnly("""{"transcript":""}"""))
        assertNull(GemmaAudioCorpus.decodeTranscriptOnly("private invalid output"))
    }

    @Test fun malformedAudioReplyGetsSanitizedReasonWithoutText() {
        assertEquals("transcript_missing", GemmaAudioCorpus.invalidReplyCode(
            """{"reply":"Sekret do ukrycia","tool":"none","note":"","kind":"NOTE"}"""))
        assertEquals("reply_format_invalid", GemmaAudioCorpus.invalidReplyCode(
            "private recording 123-456-789"))
    }

    @Test fun wordErrorScoringHandlesPolishSentenceAndMismatch() {
        val phrase = GemmaAudioCorpus.phrases.first()
        assertEquals(0, GemmaAudioCorpus.wordErrors(phrase, phrase).first)
        assertTrue(GemmaAudioCorpus.wordErrors(phrase, "Nie wiem.").first > 0)
    }

    private class FakeModel(val failAt: Int = -1) : TextCallAgentBackend,
        NativeConversationContextBackend {
        var turns = 0; var resets = 0; var prewarms = 0; var closes = 0
        override fun prewarm() { prewarms++ }
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) =
            listener.onError("text_not_supported")
        override fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) {
            turns++
            if (turns == failAt) { listener.onError("synthetic_failure"); return }
            val transcript = GemmaAudioCorpus.phrases[turns - 1]
            val encoded = com.google.gson.JsonObject().apply {
                addProperty("transcript", transcript)
                addProperty("reply", "W porządku.")
                addProperty("tool", "none")
                addProperty("note", "")
                addProperty("kind", "NOTE")
            }
            listener.onComplete(encoded.toString())
        }
        override fun resetConversationContext() { resets++ }
        override fun cancel() = Unit
        override fun close() { closes++ }
    }
    private class FakeOutput : SpeechOutputProvider {
        var synthesis = 0
        override fun synthesize(text: String, listener: SpeechOutputProvider.Listener) {
            synthesis++
            listener.onPcm16Mono16k(ByteArray(32_000) { 2 })
        }
        override fun cancel() = Unit
        override fun close() = Unit
    }
}
