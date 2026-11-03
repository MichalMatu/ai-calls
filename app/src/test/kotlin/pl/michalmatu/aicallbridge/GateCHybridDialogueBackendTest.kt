package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.DialogueActionBackend
import pl.michalmatu.aicallbridge.textagent.DialogueActionDecisionObserver
import pl.michalmatu.aicallbridge.textagent.DialogueActionId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class GateCHybridDialogueBackendTest {
    @Test
    fun `Gemma service fact action routes control token to host executor`() {
        val diagnostics = GateCHybridDiagnostics()
        val classifier = FakeBackend.complete(
            """{"action":"DISCLOSE_AUTHORIZED_FACT","confidence":0.99,"argument":"PHONE","reason":"service_number"}""",
        )
        val hostRelay = FakeBackend.complete("Numer usługi to 1 2 3.")
        val backend = DialogueActionBackend(
            classifierBackend = classifier,
            policy = GateCHybridDialogueBackendFactory.actionPolicy(),
            executor = GateCHybridDialogueBackendFactory.actionExecutor(
                hostActionRelay = hostRelay,
                diagnostics = diagnostics,
                effectCommitControl = "[[COMMIT]]",
                canConfirmEffect = { true },
            ),
            observer = DialogueActionDecisionObserver(diagnostics::onDecision),
        )
        var completed: String? = null

        backend.generate(
            "Podaj dowolny numer twojej usługi.",
            listener(complete = { completed = it }),
        )

        assertEquals("Numer usługi to 1 2 3.", completed)
        assertEquals(1, classifier.generateCalls)
        assertEquals(1, hostRelay.generateCalls)
        assertEquals(
            GateCHybridDialogueBackendFactory.DISCLOSE_PHONE_CONTROL,
            hostRelay.lastInput,
        )
        assertEquals(
            DialogueActionId.DISCLOSE_AUTHORIZED_FACT,
            diagnostics.snapshot().decisions.single().actionId,
        )
    }

    @Test
    fun `task subject is spoken locally without relay`() {
        val diagnostics = GateCHybridDiagnostics()
        val classifier = FakeBackend.complete(
            """{"action":"STATE_TASK_SUBJECT","confidence":0.97}""",
        )
        val hostRelay = FakeBackend.complete("unused")
        val backend = DialogueActionBackend(
            classifierBackend = classifier,
            policy = GateCHybridDialogueBackendFactory.actionPolicy(),
            executor = GateCHybridDialogueBackendFactory.actionExecutor(
                hostActionRelay = hostRelay,
                diagnostics = diagnostics,
                effectCommitControl = "[[COMMIT]]",
                canConfirmEffect = { false },
            ),
            observer = DialogueActionDecisionObserver(diagnostics::onDecision),
        )
        var completed: String? = null

        backend.generate("W jakiej sprawie?", listener(complete = { completed = it }))

        assertEquals("Chodzi o blokadę prezentacji numeru, usługę CLIR.", completed)
        assertEquals(0, hostRelay.generateCalls)
        assertEquals(
            listOf(GateCHybridResponseSource.LOCAL_ACTION),
            diagnostics.snapshot().responseSources,
        )
    }

    @Test
    fun `app-owned call recovers locally when supervisor relay is disabled`() {
        val diagnostics = GateCHybridDiagnostics()
        val recovery = GateCHybridDialogueBackendFactory.localRecoveryBackend(diagnostics)
        var approvedText: String? = null
        var error: String? = null

        recovery.generate(
            "Potrzebuję wyjaśnienia.",
            listener(complete = { approvedText = it }, error = { error = it }),
        )

        assertTrue(approvedText!!.contains("usługi CLIR"))
        assertEquals(null, error)
        assertEquals(
            listOf(GateCHybridResponseSource.LOCAL_RECOVERY),
            diagnostics.snapshot().responseSources,
        )
        assertTrue(!approvedText!!.contains("potwierdzam", ignoreCase = true))
        assertTrue(!approvedText!!.contains("numer to", ignoreCase = true))
    }

    @Test
    fun `CLIR procedural questions release distinct reviewed answers`() {
        val actions = listOf(
            DialogueActionId.EXPLAIN_SERVICE_SCOPE,
            DialogueActionId.ASK_NEXT_SERVICE_STEP,
            DialogueActionId.REQUEST_SERVICE_TRANSFER,
        )
        val expected = listOf(
            "stałe ukrywanie",
            "Jaki krok",
            "połączyć z działem",
        )
        val diagnostics = GateCHybridDiagnostics()
        val control = GateCHybridDialogueBackendFactory.actionExecutor(
            hostActionRelay = FakeBackend.complete("unused"),
            diagnostics = diagnostics,
            effectCommitControl = "[[COMMIT]]",
            canConfirmEffect = { false },
        )
        actions.zip(expected).forEach { (action, fragment) ->
            var text: String? = null
            control.execute(
                "Pytanie konsultanta o usługę i dalsze kroki",
                pl.michalmatu.aicallbridge.textagent.DialogueActionDecision(action, 0.97),
                listener(complete = { text = it }),
            )
            assertTrue(text!!.contains(fragment))
            assertTrue(!text!!.contains("[[COMMIT]]"))
        }
        val policy = GateCHybridDialogueBackendFactory.actionPolicy()
        assertTrue(actions.all { it in policy.allowedActions })
        assertEquals(
            listOf(
                GateCHybridResponseSource.LOCAL_ACTION,
                GateCHybridResponseSource.LOCAL_ACTION,
                GateCHybridResponseSource.LOCAL_ACTION,
            ),
            diagnostics.snapshot().responseSources,
        )
    }

    @Test
    fun `CLIR new procedural responses never add effect confirmation without authority`() {
        val policy = GateCHybridDialogueBackendFactory.actionPolicy(false)
        assertTrue(DialogueActionId.CONFIRM_AUTHORIZED_EFFECT !in policy.allowedActions)
        assertTrue(DialogueActionId.ASK_NEXT_SERVICE_STEP in policy.allowedActions)
        assertTrue(DialogueActionId.EXPLAIN_SERVICE_SCOPE in policy.allowedActions)
        assertTrue(DialogueActionId.REQUEST_SERVICE_TRANSFER in policy.allowedActions)
    }

    @Test
    fun `generic action policy omits effect confirmation without explicit authority`() {
        val policy = GateCHybridDialogueBackendFactory.actionPolicy(
            allowEffectConfirmation = false,
        )

        assertTrue(
            pl.michalmatu.aicallbridge.textagent.DialogueActionId.CONFIRM_AUTHORIZED_EFFECT !in
                policy.allowedActions,
        )
    }

    @Test
    fun `effect confirmation is emitted only when control plane says ready`() {
        val diagnostics = GateCHybridDiagnostics()
        val hostRelay = FakeBackend.complete("unused")
        val executor = GateCHybridDialogueBackendFactory.actionExecutor(
            hostActionRelay = hostRelay,
            diagnostics = diagnostics,
            effectCommitControl = "[[COMMIT]]",
            canConfirmEffect = { true },
        )
        var completed: String? = null
        executor.execute(
            "Czy potwierdzasz?",
            pl.michalmatu.aicallbridge.textagent.DialogueActionDecision(
                actionId = DialogueActionId.CONFIRM_AUTHORIZED_EFFECT,
                confidence = 0.99,
            ),
            listener(complete = { completed = it }),
        )

        assertEquals("[[COMMIT]]", completed)
        assertTrue(hostRelay.generateCalls == 0)
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
    ) : TextCallAgentBackend {
        var generateCalls = 0
            private set
        var lastInput: String? = null
            private set

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
            lastInput = userText
            listener.onComplete(checkNotNull(completeText))
        }

        override fun cancel() = Unit
        override fun close() = Unit

        companion object {
            fun complete(text: String) = FakeBackend(text)
        }
    }
}
