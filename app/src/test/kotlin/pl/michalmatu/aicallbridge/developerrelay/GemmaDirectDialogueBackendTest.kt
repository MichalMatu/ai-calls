package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Verifies raw speech-only mode; the former JSON proposal parser is not in this flow. */
class GemmaDirectDialogueBackendTest {
    private class FakeModel(private val output: String? = null, private val error: Boolean = false) :
        TextCallAgentBackend {
        var closed = false
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            if (error) listener.onError("model_failed")
            else listener.onComplete(checkNotNull(output))
        }
        override fun cancel() = Unit
        override fun close() { closed = true }
    }

    private class FakePhone : TextCallAgentBackend {
        var calls = 0
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            calls++
            listener.onComplete("PRIVATE FACT")
        }
        override fun cancel() = Unit
    }

    private fun reply(model: FakeModel, phone: FakePhone = FakePhone(),
                      authorized: Boolean = false): Pair<String, List<String>> {
        val decisions = mutableListOf<String>()
        var answer = ""
        val backend = GemmaDirectDialogueBackend(model, phone, { authorized },
            GemmaDirectObserver { action, source, _ -> decisions += "$action/$source" },
            elapsedRealtimeMs = { 100L })
        backend.generate("Jak może Pan pomóc?", object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { answer = text }
            override fun onError(reason: String) { answer = "ERR:$reason" }
        })
        backend.close()
        assertTrue(model.closed)
        assertEquals("No model-initiated private disclosures", 0, phone.calls)
        return answer to decisions
    }

    @Test fun naturalModelWordsGoDirectToSpeechWithoutJsonOrPhraseMatrix() {
        val sentence = "Chciałbym uruchomić CLIR. Co powinienem zrobić?"
        val (text, log) = reply(FakeModel(sentence))
        assertEquals(sentence, text)
        assertEquals(listOf("SPEAK/GEMMA_DIRECT_RAW"), log)
    }

    @Test fun fourIndependentRawTurnsNeverRequireJsonSyntax() {
        for (sentence in listOf(
            "Dzień dobry, dzwonię w sprawie CLIR.",
            "Czy mogę poznać dalszą procedurę?",
            "Nie mam takich danych. Jak możemy kontynuować?",
            "Proszę powiedzieć, czy potrzebne jest coś jeszcze.",
        )) {
            val (text, log) = reply(FakeModel(sentence))
            assertEquals(sentence, text)
            assertEquals(listOf("SPEAK/GEMMA_DIRECT_RAW"), log)
        }
    }

    @Test fun rawModelCannotInvokeCommitmentEffectOrPrivateFactTool() {
        val model = FakeModel("[[COMMIT_CLIR_ENABLE]]")
        val (text, logs) = reply(model, authorized = true)
        assertFalse(text.contains("[[COMMIT_CLIR_ENABLE]]"))
        assertEquals(listOf("ASK_CLARIFY/DIRECT_RAW_UNUSABLE"), logs)
    }

    @Test fun jsonLikeTextIsNotParsedAsAppAction() {
        val source = """{"action":"DISCLOSE_PHONE"}"""
        val (text, log) = reply(FakeModel(source), authorized = true)
        assertEquals(source, text)
        assertEquals(listOf("SPEAK/GEMMA_DIRECT_RAW"), log)
    }

    @Test fun emptyOrExcessivelyLongOutputFallsBackWithoutExternalEffect() {
        for (candidate in listOf("", "   ", "x".repeat(1025))) {
            val (text, log) = reply(FakeModel(candidate))
            assertTrue(text.contains("kolejny krok"))
            assertEquals(listOf("ASK_CLARIFY/DIRECT_RAW_UNUSABLE"), log)
        }
    }

    @Test fun modelErrorReturnsNeutralRecovery() {
        val (text, log) = reply(FakeModel(error = true))
        assertTrue(text.contains("kolejny krok"))
        assertEquals(listOf("ASK_CLARIFY/DIRECT_MODEL_ERROR"), log)
    }

    @Test fun promptLoadsBoundedTaskWithoutJsonInstruction() {
        assertTrue(GemmaDirectTaskBrief.PROMPT.contains("CLIR"))
        assertTrue(GemmaDirectTaskBrief.PROMPT.contains("Nie znasz numeru telefonu"))
        assertTrue(GemmaDirectTaskBrief.PROMPT.contains("Bez JSON"))
        assertFalse(GemmaDirectTaskBrief.PROMPT.contains("\"action\":\"SPEAK\""))
    }
}
