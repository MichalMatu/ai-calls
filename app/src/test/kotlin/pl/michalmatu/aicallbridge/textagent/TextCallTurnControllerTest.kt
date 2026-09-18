package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow

class TextCallTurnControllerTest {
    @Test
    fun completeBackendTextIsApprovedBeforeRelease() {
        val backend = FakeBackend()
        val order = mutableListOf<String>()
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy {
                order += "approval"
                TextOutputDecision.RELEASE
            },
        )
        var approved = ""

        controller.submitUserText("dzień dobry", object : TextCallTurnController.Listener {
            override fun onApprovedResponse(text: String) {
                order += "approved"
                approved = text
            }

            override fun onDroppedResponse() = error("unexpected drop")
            override fun onError(reason: String) = error(reason)
        })
        order += "backend_complete"
        backend.complete("gotowa odpowiedź")

        assertEquals("gotowa odpowiedź", approved)
        assertEquals(listOf("backend_complete", "approval", "approved"), order)
    }

    @Test
    fun droppedTextNeverBecomesApprovedOutput() {
        val backend = FakeBackend()
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy { TextOutputDecision.DROP },
        )
        var dropped = false
        var approved = false

        controller.submitUserText("test", object : TextCallTurnController.Listener {
            override fun onApprovedResponse(text: String) { approved = true }
            override fun onDroppedResponse() { dropped = true }
            override fun onError(reason: String) = error(reason)
        })
        backend.complete("nie wolno tego wypowiedzieć")

        assertTrue(dropped)
        assertFalse(approved)
    }

    @Test
    fun staleBackendCompletionAfterCancelIsIgnored() {
        val backend = FakeBackend()
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy { TextOutputDecision.RELEASE },
        )
        var callbackCount = 0

        controller.submitUserText("test", object : TextCallTurnController.Listener {
            override fun onApprovedResponse(text: String) { callbackCount += 1 }
            override fun onDroppedResponse() { callbackCount += 1 }
            override fun onError(reason: String) { callbackCount += 1 }
        })
        controller.cancel()
        backend.complete("spóźniona odpowiedź")

        assertEquals(0, callbackCount)
        assertTrue(backend.cancelCount >= 1)
    }

    @Test
    fun applicationPolicyRequiresActiveNegotiationAndNoCommitmentPermit() {
        val inactiveWorkflow = workflow(active = false)
        val inactiveGate = CallCommitmentGate { "inactive-token" }
        val inactivePolicy = CallTextAgentOutputApprovalPolicy(inactiveWorkflow, inactiveGate)
        assertEquals(TextOutputDecision.DROP, inactivePolicy.evaluate("tekst"))

        val activeWorkflow = workflow(active = true)
        val activeGate = CallCommitmentGate { "active-token" }
        val activePolicy = CallTextAgentOutputApprovalPolicy(activeWorkflow, activeGate)
        assertEquals(TextOutputDecision.RELEASE, activePolicy.evaluate("tekst"))

        activeGate.authorize(CallProposal(null, null, null, null, null))
        assertEquals(TextOutputDecision.DROP, activePolicy.evaluate("tekst"))
    }

    private fun workflow(active: Boolean): CallWorkflow {
        val task = CallTask(
            "diagnostic target",
            "diagnostic action",
            "diagnostic service",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
        if (active) {
            workflow.resolveTarget(CallResolvedTarget("diagnostic", "000"))
            workflow.markDialing()
            workflow.markCallActive()
        }
        return workflow
    }

    private class FakeBackend : TextCallAgentBackend {
        private var listener: TextCallAgentBackend.Listener? = null
        var cancelCount: Int = 0
            private set

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            this.listener = listener
        }

        fun complete(text: String) {
            listener?.onComplete(text)
        }

        override fun cancel() {
            cancelCount += 1
        }
    }
}
