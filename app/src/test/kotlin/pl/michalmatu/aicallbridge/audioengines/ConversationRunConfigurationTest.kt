package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class ConversationRunConfigurationTest {
    @Test fun cacheMissCreatesExactlyTheSelectedModelWithTheCapturedInstruction() {
        for (model in DialogueModelId.entries) {
            val profile = AudioEngineProfile(input = SpeechInputId.PARAKEET_PL, dialogue = model)
            val config = ConversationRunConfiguration.ModelDirect(profile, "instrukcja próby")
            val loaded = FakeBackend()
            var calls = 0
            assertSame(loaded, config.acquireModel(
                takePrepared = { actual, instruction ->
                    assertEquals(profile, actual)
                    assertEquals("instrukcja próby", instruction)
                    null
                },
                create = { actual, instruction ->
                    calls++
                    assertEquals(model, actual.dialogue)
                    assertEquals("instrukcja próby", instruction)
                    loaded
                },
            ))
            assertEquals(1, calls)
        }
    }

    @Test fun preparedModelIsReusedWithoutLoadingASecondEngine() {
        val config = ConversationRunConfiguration.ModelDirect(AudioEngineProfile.STABLE, "pierwsza")
        val warm = FakeBackend()
        assertSame(warm, config.acquireModel({ _, _ -> warm }, { _, _ -> error("second_engine_created") }))
    }

    @Test fun laterPreferenceChangesDoNotMixTheModelOrPromptOfAPreparedAttempt() {
        var selected = AudioEngineProfile(dialogue = DialogueModelId.QWEN_3_1_7B)
        var instructions = "instrukcja pierwszej próby"
        val pending = ConversationRunConfiguration.ModelDirect(selected, instructions)
        selected = selected.copy(dialogue = DialogueModelId.GEMMA_4_E4B, output = SpeechOutputId.PIPER_PL)
        instructions = "instrukcja kolejnej próby"
        val next = ConversationRunConfiguration.ModelDirect(selected, instructions)
        pending.acquireModel({ _, _ -> null }, { profile, prompt ->
            assertEquals(DialogueModelId.QWEN_3_1_7B, profile.dialogue)
            assertEquals(SpeechOutputId.ANDROID_TTS_PL, profile.output)
            assertEquals("instrukcja pierwszej próby", prompt)
            FakeBackend()
        })
        assertEquals(DialogueModelId.GEMMA_4_E4B, next.audioProfile.dialogue)
        assertEquals("instrukcja kolejnej próby", next.systemInstruction)
    }

    @Test fun selectedModelFailureIsPropagatedWithoutTryingADefaultModel() {
        val config = ConversationRunConfiguration.ModelDirect(
            AudioEngineProfile(dialogue = DialogueModelId.QWEN_3_1_7B), "cel",
        )
        var creates = 0
        val failure = IllegalStateException("selected_model_unavailable")
        val actual = runCatching { config.acquireModel({ _, _ -> null }, { profile, _ ->
            creates++
            assertEquals(DialogueModelId.QWEN_3_1_7B, profile.dialogue)
            throw failure
        }) }.exceptionOrNull()
        assertSame(failure, actual)
        assertEquals(1, creates)
    }

    @Test fun configurationDescriptionDoesNotExposeInstructions() {
        val config = ConversationRunConfiguration.ModelDirect(AudioEngineProfile.STABLE, "private-prompt-content")
        assertFalse(config.toString().contains("private-prompt-content"))
    }

    private class FakeBackend : TextCallAgentBackend {
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) = listener.onComplete("odpowiedź")
        override fun cancel() = Unit
    }
}
