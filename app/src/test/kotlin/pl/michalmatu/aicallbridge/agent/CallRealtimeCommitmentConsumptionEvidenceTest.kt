package pl.michalmatu.aicallbridge.agent

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionFollowup
import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionResponder

class CallRealtimeCommitmentConsumptionEvidenceTest {
    @Test
    fun successfulConsumeEmitsOneRedactedProposalBoundEvidenceWithoutCompletion() {
        val workflow = activeWorkflow()
        val proposal = proposal()
        val gate = CallCommitmentGate { "permit-evidence" }
        val authorization = gate.authorize(proposal)
        val evidence = mutableListOf<CallCommitmentConsumptionEvidence>()
        val handler = CallRealtimeCommitmentFunctionHandler(
            workflow = workflow,
            commitmentGate = gate,
            consumptionListener = CallCommitmentConsumptionListener { evidence += it },
        )
        val responder = RecordingResponder()

        handler.onFunctionCall(commitCall(authorization.value), responder)

        assertEquals(1, evidence.size)
        assertEquals(proposal, evidence.single().proposal)
        assertFalse(evidence.single().toString().contains("permit-evidence"))
        assertFalse(evidence.single().toString().contains("Clinic A"))
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        assertNull(workflow.snapshot().pendingProposal())
        assertNull(workflow.snapshot().outcome())
        assertEquals(1, responder.outputs.size)

        assertFails<IllegalStateException> {
            handler.onFunctionCall(commitCall(authorization.value), RecordingResponder())
        }

        assertEquals(1, evidence.size)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        assertNull(workflow.snapshot().outcome())
    }

    private class RecordingResponder : CallRealtimeFunctionResponder {
        val outputs = mutableListOf<String>()

        override fun submit(outputJson: String): Result<Unit> {
            outputs += outputJson
            return Result.success(Unit)
        }

        override fun submit(
            outputJson: String,
            followup: RealtimeFunctionFollowup,
        ): Result<Unit> = submit(outputJson)
    }

    companion object {
        private fun activeWorkflow(): CallWorkflow =
            CallWorkflow(
                CallTask(
                    "clinic",
                    "book",
                    "consultation",
                    CallConstraints(
                        listOf(),
                        MoneyAmount(BigDecimal("200.00"), "PLN"),
                        setOf(CallPaymentMode.PRIVATE),
                    ),
                    CallPreferences.none(),
                    mapOf(),
                ),
                CallConfirmationPolicy(),
            ) { }.apply {
                resolveTarget(CallResolvedTarget("Clinic", "+48123456789"))
                markDialing()
                markCallActive()
            }

        private fun proposal() = CallProposal(
            null,
            MoneyAmount(BigDecimal("150.00"), "PLN"),
            CallPaymentMode.PRIVATE,
            "Clinic A",
            "Wroclaw",
        )

        private fun commitCall(authorization: String) = RealtimeFunctionCall(
            "commit_1",
            CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME,
            "{\"authorization\":\"$authorization\"}",
        )

        private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
            try {
                block()
            } catch (error: Throwable) {
                if (error is T) return error
                throw AssertionError(
                    "expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}",
                    error,
                )
            }
            throw AssertionError("expected ${T::class.java.simpleName}")
        }
    }
}
