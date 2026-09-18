package pl.michalmatu.aicallbridge.agent

import com.google.gson.JsonParser
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionResponder

class CallRealtimeCommitmentIntegrationTest {
    @Test
    fun inPolicyProposalIssuesOpaquePermitAndCommitConsumesItOnce() {
        val workflow = activeWorkflow(maxPrice = "200.00")
        val gate = CallCommitmentGate { "permit-1" }
        val proposalHandler = CallRealtimeProposalFunctionHandler(workflow, gate)
        val commitmentHandler = CallRealtimeCommitmentFunctionHandler(workflow, gate)
        val evaluation = CapturingResponder()

        proposalHandler.onFunctionCall(
            evaluateCall(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic A","location":"Wroclaw"}""",
            ),
            evaluation,
        )

        val evaluationOutput = JsonParser.parseString(evaluation.single()).asJsonObject
        assertEquals("autonomously_allowed", evaluationOutput.get("decision").asString)
        assertEquals("permit-1", evaluationOutput.get("commitment_authorization").asString)
        assertTrue(gate.hasAuthorization())

        val commitment = CapturingResponder()
        commitmentHandler.onFunctionCall(commitCall("permit-1"), commitment)

        assertEquals("authorized", JsonParser.parseString(commitment.single()).asJsonObject.get("commitment").asString)
        assertFalse(gate.hasAuthorization())
        assertFails<IllegalStateException> {
            commitmentHandler.onFunctionCall(commitCall("permit-1"), CapturingResponder())
        }
    }

    @Test
    fun newerEvaluatedProposalRevokesOlderPermit() {
        val workflow = activeWorkflow(maxPrice = "500.00")
        val tokens = ArrayDeque(listOf("permit-old", "permit-new"))
        val gate = CallCommitmentGate { tokens.removeFirst() }
        val proposalHandler = CallRealtimeProposalFunctionHandler(workflow, gate)
        val commitmentHandler = CallRealtimeCommitmentFunctionHandler(workflow, gate)
        val first = CapturingResponder()
        val second = CapturingResponder()

        proposalHandler.onFunctionCall(
            evaluateCall(
                """{"scheduled_at":null,"price":{"amount":"100.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic A","location":null}""",
            ),
            first,
        )
        proposalHandler.onFunctionCall(
            evaluateCall(
                """{"scheduled_at":null,"price":{"amount":"120.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic B","location":null}""",
            ),
            second,
        )

        assertFails<IllegalStateException> {
            commitmentHandler.onFunctionCall(commitCall("permit-old"), CapturingResponder())
        }
        commitmentHandler.onFunctionCall(commitCall("permit-new"), CapturingResponder())
        assertFalse(gate.hasAuthorization())
    }

    @Test
    fun userApprovalIssuesPermitOnlyAfterHeldDecisionIsResolved() {
        val workflow = activeWorkflow(maxPrice = "100.00")
        val gate = CallCommitmentGate { "permit-user" }
        val proposalHandler = CallRealtimeProposalFunctionHandler(workflow, gate)
        val commitmentHandler = CallRealtimeCommitmentFunctionHandler(workflow, gate)
        val responder = CapturingResponder()

        proposalHandler.onFunctionCall(
            evaluateCall(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic A","location":null}""",
            ),
            responder,
        )

        assertTrue(responder.outputs.isEmpty())
        assertFalse(gate.hasAuthorization())
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state())

        val approved = proposalHandler.approvePendingProposal()

        assertTrue(approved.isSuccess)
        val output = JsonParser.parseString(responder.single()).asJsonObject
        assertEquals("user_approved", output.get("decision").asString)
        assertEquals("permit-user", output.get("commitment_authorization").asString)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        commitmentHandler.onFunctionCall(commitCall("permit-user"), CapturingResponder())
        assertFalse(gate.hasAuthorization())
    }

    @Test
    fun userRejectionNeverIssuesCommitmentPermit() {
        val workflow = activeWorkflow(maxPrice = "100.00")
        val gate = CallCommitmentGate { "permit-should-not-exist" }
        val proposalHandler = CallRealtimeProposalFunctionHandler(workflow, gate)
        val responder = CapturingResponder()

        proposalHandler.onFunctionCall(
            evaluateCall(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic A","location":null}""",
            ),
            responder,
        )
        val rejected = proposalHandler.rejectPendingProposal()

        assertTrue(rejected.isSuccess)
        assertEquals("user_rejected", JsonParser.parseString(responder.single()).asJsonObject.get("decision").asString)
        assertFalse(gate.hasAuthorization())
    }

    @Test
    fun commitmentToolSchemaAcceptsOnlyOpaqueAuthorization() {
        val tool = CallRealtimeCommitmentFunctionHandler.tool()

        assertEquals("commit_proposal", tool.name)
        assertTrue(tool.parametersJson.contains("\"authorization\""))
        assertTrue(tool.parametersJson.contains("\"additionalProperties\":false"))
        assertTrue(tool.parametersJson.contains("\"required\":[\"authorization\"]"))
        assertFalse(tool.parametersJson.contains("price"))
        assertFalse(tool.parametersJson.contains("provider"))
    }

    private class CapturingResponder : CallRealtimeFunctionResponder {
        val outputs = mutableListOf<String>()

        override fun submit(outputJson: String): Result<Unit> {
            outputs += outputJson
            return Result.success(Unit)
        }

        fun single(): String = outputs.single()
    }

    companion object {
        private fun activeWorkflow(maxPrice: String): CallWorkflow =
            CallWorkflow(
                CallTask(
                    "clinic",
                    "book",
                    "consultation",
                    CallConstraints(
                        listOf(),
                        MoneyAmount(BigDecimal(maxPrice), "PLN"),
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

        private fun evaluateCall(arguments: String) =
            RealtimeFunctionCall("evaluate_1", "evaluate_proposal", arguments)

        private fun commitCall(authorization: String) =
            RealtimeFunctionCall(
                "commit_1",
                "commit_proposal",
                "{\"authorization\":\"$authorization\"}",
            )

        private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
            try {
                block()
            } catch (error: Throwable) {
                if (error is T) return error
                throw AssertionError("expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}", error)
            }
            throw AssertionError("expected ${T::class.java.simpleName}")
        }
    }
}
