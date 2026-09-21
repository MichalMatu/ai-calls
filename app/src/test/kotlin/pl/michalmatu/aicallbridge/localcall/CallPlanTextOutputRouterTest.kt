package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPlanDecision
import pl.michalmatu.aicallbridge.agent.CallPolicyAction
import pl.michalmatu.aicallbridge.agent.CallPolicyDecision
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallTurnController
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision

class CallPlanTextOutputRouterTest {
    @Test
    fun `say routes exact text through application approval without backend generation`() {
        val backend = FakeBackend()
        val approvedInputs = mutableListOf<String>()
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy { text ->
                approvedInputs += text
                TextOutputDecision.RELEASE
            },
        )
        val router = CallPlanTextOutputRouter(controller)
        val listener = RecordingListener()
        val result = CallPlanTurnResult(CallPlanDecision.say("1990", "birth-year"), null)

        router.route(result, listener)

        assertEquals(listOf("1990"), approvedInputs)
        assertEquals(listOf("1990"), listener.approvedText)
        assertFalse(listener.dropped)
        assertTrue(listener.structured.isEmpty())
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `say dropped by application policy never becomes approved output`() {
        val backend = FakeBackend()
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy { TextOutputDecision.DROP },
        )
        val router = CallPlanTextOutputRouter(controller)
        val listener = RecordingListener()

        router.route(
            CallPlanTurnResult(CallPlanDecision.say("sensitive", "fact"), null),
            listener,
        )

        assertTrue(listener.dropped)
        assertTrue(listener.approvedText.isEmpty())
        assertTrue(listener.structured.isEmpty())
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `all non speech plan actions remain structured without approval or backend work`() {
        val backend = FakeBackend()
        var approvalCalls = 0
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy {
                approvalCalls += 1
                TextOutputDecision.RELEASE
            },
        )
        val router = CallPlanTextOutputRouter(controller)
        val listener = RecordingListener()
        val outcome = CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "done",
            null,
            null,
            null,
            null,
            null,
            null,
        )
        val proposal = CallProposal(null, null, null, "Clinic A", "Wrocław")
        val policy = CallPolicyDecision(CallPolicyAction.AUTONOMOUSLY_ALLOWED, emptyList())
        val results = listOf(
            CallPlanTurnResult(CallPlanDecision.askRepeat(), null),
            CallPlanTurnResult(CallPlanDecision.takeOver(null), null),
            CallPlanTurnResult(CallPlanDecision.complete(outcome, "done"), null),
            CallPlanTurnResult(CallPlanDecision.proposal(proposal, "offer"), policy),
        )

        results.forEach { router.route(it, listener) }

        assertEquals(0, approvalCalls)
        assertEquals(0, backend.generateCalls)
        assertEquals(4, listener.structured.size)
        results.zip(listener.structured).forEach { (expected, actual) -> assertSame(expected, actual) }
        assertTrue(listener.approvedText.isEmpty())
        assertFalse(listener.dropped)
    }

    private class RecordingListener : CallPlanTextOutputRouter.Listener {
        val approvedText = mutableListOf<String>()
        val structured = mutableListOf<CallPlanTurnResult>()
        var dropped = false

        override fun onApprovedText(text: String) {
            approvedText += text
        }

        override fun onDroppedText() {
            dropped = true
        }

        override fun onStructuredResult(result: CallPlanTurnResult) {
            structured += result
        }

        override fun onError(reason: String) = error(reason)
    }

    private class FakeBackend : TextCallAgentBackend {
        var generateCalls = 0
        var cancelCalls = 0

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
        }

        override fun cancel() {
            cancelCalls += 1
        }
    }
}
