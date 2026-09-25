package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.DialogueSkillDecisionObserver
import pl.michalmatu.aicallbridge.textagent.DialogueSkillId
import pl.michalmatu.aicallbridge.textagent.DialogueSkillPolicy
import pl.michalmatu.aicallbridge.textagent.DialogueSkillTextBackend
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class GateCHybridDialogueBackendTest {
    @Test
    fun `bounded local skill owns reviewed response and telemetry source`() {
        val diagnostics = GateCHybridDiagnostics()
        val classifier = FakeBackend.complete(
            """{"skill":"ASK_REPEAT","confidence":0.93,"reason":"fragmented_input"}""",
        )
        val local = DialogueSkillTextBackend(
            classifierBackend = classifier,
            policy = policy(),
            observer = DialogueSkillDecisionObserver(diagnostics::onDecision),
        )
        val injected = FakeBackend.complete("injected fallback")
        val backend = GateCHybridDialogueBackendFactory.compose(local, injected, diagnostics)
        var completed: String? = null

        backend.generate("urwany fragment", listener(complete = { completed = it }))

        val snapshot = diagnostics.snapshot()
        assertEquals("Proszę powtórzyć.", completed)
        assertEquals(0, injected.generateCalls)
        assertEquals(DialogueSkillId.ASK_REPEAT, snapshot.decisions.single().skillId)
        assertEquals(0.93, snapshot.decisions.single().confidence, 0.0)
        assertEquals("fragmented_input", snapshot.decisions.single().reason)
        assertTrue(snapshot.localSkillErrors.isEmpty())
        assertEquals(listOf(GateCHybridResponseSource.LOCAL_SKILL), snapshot.responseSources)
        println(
            "HYBRID_PROOF skill=${snapshot.decisions.single().skillId} " +
                "confidence=${snapshot.decisions.single().confidence} " +
                "reason=${snapshot.decisions.single().reason} " +
                "source=${snapshot.responseSources.single()}",
        )
        backend.close()
    }

    @Test
    fun `Orange service number prompt bypasses local skill and reaches relay`() {
        val diagnostics = GateCHybridDiagnostics()
        val classifier = FakeBackend.complete(
            """{"skill":"ACKNOWLEDGE_NEUTRAL","confidence":0.99,"reason":"simple_prompt"}""",
        )
        val local = DialogueSkillTextBackend(
            classifierBackend = classifier,
            policy = policy(),
            observer = DialogueSkillDecisionObserver(diagnostics::onDecision),
        )
        val injected = FakeBackend.complete("runtime relay response")
        val backend = GateCHybridDialogueBackendFactory.compose(local, injected, diagnostics)
        var completed: String? = null
        val prompt = "Podaj dowolny numer twojej usługi lub wprowadź go na klawiaturze."

        backend.generate(prompt, listener(complete = { completed = it }))

        val snapshot = diagnostics.snapshot()
        assertTrue(GateCHybridDialogueBackendFactory.isOrangeServiceNumberPrompt(prompt))
        assertFalse(
            GateCHybridDialogueBackendFactory.isOrangeServiceNumberPrompt(
                "Podaj numer telefonu konsultanta.",
            ),
        )
        assertEquals("runtime relay response", completed)
        assertEquals(0, classifier.generateCalls)
        assertEquals(1, injected.generateCalls)
        assertTrue(snapshot.decisions.isEmpty())
        assertTrue(snapshot.localSkillErrors.isEmpty())
        assertEquals(listOf(GateCHybridResponseSource.CHAT_RELAY), snapshot.responseSources)
        backend.close()
    }

    @Test
    fun `low confidence local skill falls through once and telemetry names relay source`() {
        val diagnostics = GateCHybridDiagnostics()
        val classifier = FakeBackend.complete(
            """{"skill":"ASK_CLARIFY","confidence":0.41,"reason":"ambiguous"}""",
        )
        val local = DialogueSkillTextBackend(
            classifierBackend = classifier,
            policy = policy(),
            observer = DialogueSkillDecisionObserver(diagnostics::onDecision),
        )
        val injected = FakeBackend.complete("Bezpieczna odpowiedź z injection path.")
        val backend = GateCHybridDialogueBackendFactory.compose(local, injected, diagnostics)
        var completed: String? = null

        backend.generate("niejednoznaczny tekst", listener(complete = { completed = it }))

        val snapshot = diagnostics.snapshot()
        assertEquals("Bezpieczna odpowiedź z injection path.", completed)
        assertEquals(1, injected.generateCalls)
        assertEquals(DialogueSkillId.ASK_CLARIFY, snapshot.decisions.single().skillId)
        assertEquals(0.41, snapshot.decisions.single().confidence, 0.0)
        assertEquals("ambiguous", snapshot.decisions.single().reason)
        assertEquals(listOf("dialogue_skill_low_confidence"), snapshot.localSkillErrors)
        assertEquals(listOf(GateCHybridResponseSource.CHAT_RELAY), snapshot.responseSources)
        println(
            "HYBRID_PROOF skill=${snapshot.decisions.single().skillId} " +
                "confidence=${snapshot.decisions.single().confidence} " +
                "reason=${snapshot.decisions.single().reason} " +
                "source=${snapshot.responseSources.single()}",
        )
        backend.close()
    }

    @Test
    fun `local classifier error falls through once and records fallback source`() {
        val diagnostics = GateCHybridDiagnostics()
        val classifier = FakeBackend.error("engine_unavailable")
        val local = DialogueSkillTextBackend(
            classifierBackend = classifier,
            policy = policy(),
            observer = DialogueSkillDecisionObserver(diagnostics::onDecision),
        )
        val injected = FakeBackend.complete("Bezpieczna odpowiedź z injection path.")
        val backend = GateCHybridDialogueBackendFactory.compose(local, injected, diagnostics)
        var completed: String? = null

        backend.generate("tekst do klasyfikacji", listener(complete = { completed = it }))

        val snapshot = diagnostics.snapshot()
        assertEquals("Bezpieczna odpowiedź z injection path.", completed)
        assertEquals(1, injected.generateCalls)
        assertTrue(snapshot.decisions.isEmpty())
        assertEquals(
            listOf("dialogue_skill_classifier_engine_unavailable"),
            snapshot.localSkillErrors,
        )
        assertEquals(listOf(GateCHybridResponseSource.CHAT_RELAY), snapshot.responseSources)
        println(
            "HYBRID_PROOF skill=none confidence=none reason=engine_unavailable " +
                "source=${snapshot.responseSources.single()}",
        )
        backend.close()
    }

    private fun policy() = DialogueSkillPolicy(
        allowedResponses = mapOf(
            DialogueSkillId.ASK_REPEAT to "Proszę powtórzyć.",
            DialogueSkillId.ASK_CLARIFY to "Proszę doprecyzować.",
            DialogueSkillId.ACKNOWLEDGE_NEUTRAL to "Rozumiem.",
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
        private val errorReason: String?,
    ) : TextCallAgentBackend {
        var generateCalls = 0
            private set

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
            if (errorReason != null) {
                listener.onError(errorReason)
            } else {
                listener.onComplete(checkNotNull(completeText))
            }
        }

        override fun cancel() = Unit
        override fun close() = Unit

        companion object {
            fun complete(text: String) = FakeBackend(text, null)
            fun error(reason: String) = FakeBackend(null, reason)
        }
    }
}
