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

    @Test fun routineClirProceduresAnswerWithoutWaitingForGemma() {
        val responses = mapOf(
            "Czym jest usługa CLIR?" to "stałe zastrzeganie",
            "Na czym polega usługa CLIR?" to "stałe zastrzeganie",
            "Jakie są kolejne kroki, żeby włączyć usługę CLIR?" to "Jaki krok",
            "Tą usługą zajmuje się inny dział." to "Proszę połączyć",
        )
        responses.forEach { (transcript, expectedFragment) ->
            val (answer, modelInput) = evaluate(transcript)
            assertEquals(null, modelInput)
            org.junit.Assert.assertTrue(answer.contains(expectedFragment))
            org.junit.Assert.assertTrue(!answer.contains("[[COMMIT"))
        }
    }

    @Test fun ambiguousIdentityOrCarrierEffectsStillRequireAppPolicy() {
        listOf(
            "Proszę podać PESEL.",
            "Czy autoryzuje pan płatność?",
            "Czy mam aktywować usługę CLIR?",
            "Proszę podać numer abonenta.",
            "Czy zgadza się pan na zmianę operatora?",
        ).forEach { input ->
            val (answer, delegated) = evaluate(input)
            assertEquals("model-fallback", answer)
            assertEquals(input, delegated)
        }
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
