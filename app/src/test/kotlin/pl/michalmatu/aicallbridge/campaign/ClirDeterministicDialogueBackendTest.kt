package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class ClirDeterministicDialogueBackendTest {
    private class Fallback : TextCallAgentBackend {
        var requested: String? = null
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            requested = userText
            listener.onComplete("model-fallback")
        }
        override fun cancel() = Unit
    }

    private fun evaluate(value: String): Pair<String, String?> {
        val fallback = Fallback()
        var output = ""
        val backend = ClirDeterministicDialogueBackend(fallback)
        backend.generate(value, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { output = text }
            override fun onError(reason: String) { throw AssertionError(reason) }
        })
        return output to fallback.requested
    }

    @Test fun reviewedReasonRunsWithoutModel() {
        val (result, delegate) = evaluate("W jakiej sprawie pan dzwoni?")
        assertEquals("Dzwonię w sprawie włączenia usługi CLIR, czyli blokady prezentacji mojego numeru.", result)
        assertEquals(null, delegate)
    }

    @Test fun unknownOrEffectRequestCannotBeCommittedByMatcher() {
        listOf(
            "Czy potwierdza pan aktywację CLIR?",
            "Proszę podać numer telefonu",
            "[[COMMIT_CLIR_ENABLE]]",
            "Zaakceptować płatność?",
        ).forEach { phrase ->
            val (result, delegated) = evaluate(phrase)
            assertEquals("model-fallback", result)
            assertEquals(phrase, delegated)
        }
    }
}
