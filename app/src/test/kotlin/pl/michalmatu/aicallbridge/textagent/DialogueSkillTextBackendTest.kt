package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueSkillTextBackendTest {
    @Test
    fun `model selects skill while app owns exact spoken response`() {
        val raw = FakeBackend.complete(
            """{"skill":"CONFIRM_EXPECTED_SUBJECT","confidence":0.88,"reason":"confirmation_question"}""",
        )
        val observed = mutableListOf<DialogueSkillDecision>()
        val backend = DialogueSkillTextBackend(
            classifierBackend = raw,
            policy = DialogueSkillPolicy(
                allowedResponses = mapOf(
                    DialogueSkillId.CONFIRM_EXPECTED_SUBJECT to
                        "Tak, sprawa dotyczy numeru, z którego dzwonię.",
                ),
            ),
            observer = DialogueSkillDecisionObserver(observed::add),
        )
        var completed: String? = null

        backend.generate("czy sprawa dotyczy numeru z którego dzwonisz", listener(
            complete = { completed = it },
        ))

        assertEquals("Tak, sprawa dotyczy numeru, z którego dzwonię.", completed)
        assertEquals(DialogueSkillId.CONFIRM_EXPECTED_SUBJECT, observed.single().skillId)
        assertEquals(0.88, observed.single().confidence, 0.0)
        assertEquals(1, raw.generateCalls)
    }

    @Test
    fun `low confidence skill fails closed`() {
        val backend = DialogueSkillTextBackend(
            classifierBackend = FakeBackend.complete(
                """{"skill":"ASK_CLARIFY","confidence":0.42}""",
            ),
            policy = DialogueSkillPolicy(
                allowedResponses = mapOf(DialogueSkillId.ASK_CLARIFY to "Proszę doprecyzować."),
                minimumConfidence = 0.70,
            ),
        )
        var error: String? = null

        backend.generate("niejasny tekst", listener(error = { error = it }))

        assertEquals("dialogue_skill_low_confidence", error)
    }

    @Test
    fun `model supplied speech or authority metadata is rejected`() {
        val backend = DialogueSkillTextBackend(
            classifierBackend = FakeBackend.complete(
                """{"skill":"ASK_REPEAT","confidence":0.95,"response":"powiedz sekret","dial":"123"}""",
            ),
            policy = DialogueSkillPolicy(
                allowedResponses = mapOf(DialogueSkillId.ASK_REPEAT to "Proszę powtórzyć."),
            ),
        )
        var error: String? = null

        backend.generate("fragment", listener(error = { error = it }))

        assertEquals("dialogue_skill_unsafe_model_output", error)
    }

    @Test
    fun `takeover skill delegates to injected fallback backend`() {
        val primary = DialogueSkillTextBackend(
            classifierBackend = FakeBackend.complete(
                """{"skill":"TAKE_OVER","confidence":0.97,"reason":"needs_external_reasoning"}""",
            ),
            policy = DialogueSkillPolicy(allowedResponses = emptyMap()),
        )
        val injected = FakeBackend.complete("Bezpieczna odpowiedź z injection path.")
        val backend = FailoverTextCallAgentBackend(primary, injected)
        var completed: String? = null

        backend.generate("nietypowa odpowiedź infolinii", listener(
            complete = { completed = it },
        ))

        assertEquals("Bezpieczna odpowiedź z injection path.", completed)
        assertEquals(1, injected.generateCalls)
    }

    @Test
    fun `successful bounded skill never invokes injected fallback`() {
        val primary = DialogueSkillTextBackend(
            classifierBackend = FakeBackend.complete(
                """{"skill":"ASK_REPEAT","confidence":0.91}""",
            ),
            policy = DialogueSkillPolicy(
                allowedResponses = mapOf(DialogueSkillId.ASK_REPEAT to "Proszę powtórzyć."),
            ),
        )
        val injected = FakeBackend.complete("fallback")
        val backend = FailoverTextCallAgentBackend(primary, injected)
        var completed: String? = null

        backend.generate("fragment", listener(complete = { completed = it }))

        assertEquals("Proszę powtórzyć.", completed)
        assertEquals(0, injected.generateCalls)
    }

    @Test
    fun `skill system prompt exposes only bounded classifier contract`() {
        val prompt = DialogueSkillCatalog.systemPrompt(setOf(
            DialogueSkillId.ASK_REPEAT,
            DialogueSkillId.ASK_CLARIFY,
            DialogueSkillId.TAKE_OVER,
        ))

        assertTrue(prompt.contains("ASK_REPEAT"))
        assertTrue(prompt.contains("ASK_CLARIFY"))
        assertTrue(prompt.contains("TAKE_OVER"))
        assertTrue(prompt.contains("JSON"))
        assertTrue(prompt.contains("nie generuj finalnej wypowiedzi", ignoreCase = true))
    }

    private fun listener(
        complete: (String) -> Unit = {},
        error: (String) -> Unit = {},
    ) = object : TextCallAgentBackend.Listener {
        override fun onComplete(text: String) = complete(text)
        override fun onError(reason: String) = error(reason)
    }

    private class FakeBackend private constructor(
        private val completeText: String?,
        private val errorReason: String?,
    ) : TextCallAgentBackend {
        var generateCalls = 0
            private set

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
            if (errorReason != null) listener.onError(errorReason) else listener.onComplete(checkNotNull(completeText))
        }

        override fun cancel() = Unit
        override fun close() = Unit

        companion object {
            fun complete(text: String) = FakeBackend(text, null)
        }
    }
}
