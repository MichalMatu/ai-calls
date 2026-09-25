package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialogueActionBackendTest {
    @Test
    fun `Gemma action executes app owned task subject response`() {
        val classifier = FakeBackend.complete(
            """{"action":"STATE_TASK_SUBJECT","confidence":0.97,"reason":"asks_purpose"}""",
        )
        var decision: DialogueActionDecision? = null
        var completed: String? = null
        val backend = DialogueActionBackend(
            classifierBackend = classifier,
            policy = policy(),
            executor = DialogueActionExecutor { _, selected, listener ->
                decision = selected
                listener.onComplete("app-owned-subject")
            },
        )

        backend.generate("W jakiej sprawie dzwonisz?", listener(complete = { completed = it }))

        assertEquals("app-owned-subject", completed)
        assertEquals(DialogueActionId.STATE_TASK_SUBJECT, decision?.actionId)
        assertEquals(1, classifier.generateCalls)
    }

    @Test
    fun `extraneous argument is discarded for argumentless action`() {
        val classifier = FakeBackend.complete(
            """{"action":"STATE_TASK_SUBJECT","confidence":0.97,"argument":"SPRAWA"}""",
        )
        var decision: DialogueActionDecision? = null
        val backend = DialogueActionBackend(
            classifierBackend = classifier,
            policy = policy(),
            executor = DialogueActionExecutor { _, selected, listener ->
                decision = selected
                listener.onComplete("ok")
            },
        )

        backend.generate("W jakiej sprawie dzwonisz?", listener())

        assertEquals(DialogueActionId.STATE_TASK_SUBJECT, decision?.actionId)
        assertNull(decision?.argument)
    }

    @Test
    fun `system prompt carries task effect and fact semantics without values`() {
        val prompt = DialogueActionCatalog.systemPrompt(
            policy(),
            DialogueTaskContext(
                subject = "Włączenie CLIR dla bieżącej usługi.",
                authorizedEffect = "Włączyć usługę CLIR.",
                argumentHints = mapOf("PHONE" to "numer telefonu lub numer usługi"),
            ),
        )

        assert(prompt.contains("Włączenie CLIR"))
        assert(prompt.contains("Włączyć usługę CLIR"))
        assert(prompt.contains("PHONE"))
        assert(prompt.contains("numer telefonu lub numer usługi"))
    }

    @Test
    fun `authorized fact action carries identifier but never value`() {
        val classifier = FakeBackend.complete(
            """{"action":"DISCLOSE_AUTHORIZED_FACT","confidence":0.95,"argument":"phone"}""",
        )
        var decision: DialogueActionDecision? = null
        val backend = DialogueActionBackend(
            classifierBackend = classifier,
            policy = policy(),
            executor = DialogueActionExecutor { _, selected, listener ->
                decision = selected
                listener.onComplete("executor-owned")
            },
        )

        backend.generate("Podaj numer usługi.", listener())

        assertEquals(DialogueActionId.DISCLOSE_AUTHORIZED_FACT, decision?.actionId)
        assertEquals("PHONE", decision?.argument)
    }

    @Test
    fun `unsupported fact argument fails closed`() {
        val classifier = FakeBackend.complete(
            """{"action":"DISCLOSE_AUTHORIZED_FACT","confidence":0.99,"argument":"PESEL"}""",
        )
        var error: String? = null
        var executed = false
        val backend = DialogueActionBackend(
            classifierBackend = classifier,
            policy = policy(),
            executor = DialogueActionExecutor { _, _, _ -> executed = true },
        )

        backend.generate("Podaj PESEL.", listener(error = { error = it }))

        assertEquals("dialogue_action_argument_not_allowed", error)
        assertEquals(false, executed)
    }

    @Test
    fun `take over fails closed to outer fallback`() {
        val classifier = FakeBackend.complete(
            """{"action":"TAKE_OVER","confidence":0.99,"reason":"unsupported"}""",
        )
        var error: String? = null
        val backend = DialogueActionBackend(
            classifierBackend = classifier,
            policy = policy(),
            executor = DialogueActionExecutor { _, _, _ -> error("must not execute") },
        )

        backend.generate("Nietypowa prośba.", listener(error = { error = it }))

        assertEquals("dialogue_action_takeover_required", error)
    }

    @Test
    fun `low confidence never executes`() {
        val classifier = FakeBackend.complete(
            """{"action":"STATE_TASK_SUBJECT","confidence":0.31}""",
        )
        var error: String? = null
        var completed: String? = null
        val backend = DialogueActionBackend(
            classifierBackend = classifier,
            policy = policy(),
            executor = DialogueActionExecutor { _, _, listener ->
                listener.onComplete("should-not-run")
            },
        )

        backend.generate(
            "niepewny tekst",
            listener(complete = { completed = it }, error = { error = it }),
        )

        assertNull(completed)
        assertEquals("dialogue_action_low_confidence", error)
    }

    private fun policy() = DialogueActionPolicy(
        allowedActions = setOf(
            DialogueActionId.ASK_REPEAT,
            DialogueActionId.ASK_CLARIFY,
            DialogueActionId.ACKNOWLEDGE_NEUTRAL,
            DialogueActionId.STATE_TASK_SUBJECT,
            DialogueActionId.DISCLOSE_AUTHORIZED_FACT,
            DialogueActionId.CONFIRM_AUTHORIZED_EFFECT,
        ),
        allowedArguments = mapOf(
            DialogueActionId.DISCLOSE_AUTHORIZED_FACT to setOf("PHONE"),
        ),
        minimumConfidence = 0.72,
    )

    private fun listener(
        complete: (String) -> Unit = {},
        error: (String) -> Unit = {},
    ) = object : TextCallAgentBackend.Listener {
        override fun onComplete(text: String) = complete(text)
        override fun onError(reason: String) = error(reason)
    }

    private class FakeBackend private constructor(
        private val completeText: String?,
    ) : TextCallAgentBackend {
        var generateCalls = 0
            private set

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
            listener.onComplete(checkNotNull(completeText))
        }

        override fun cancel() = Unit
        override fun close() = Unit

        companion object {
            fun complete(text: String) = FakeBackend(text)
        }
    }
}
