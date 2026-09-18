package pl.michalmatu.aicallbridge.agent

import com.google.gson.JsonParser
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionFollowup
import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionResponder

class CallRealtimeCommitmentFollowupTest {
    @Test
    fun autonomousProposalForcesCommitToolAsImmediateFollowup() {
        val workflow = activeWorkflow("200.00")
        val gate = CallCommitmentGate { "permit-auto" }
        val handler = CallRealtimeProposalFunctionHandler(workflow, gate)
        val responder = RecordingResponder()

        handler.onFunctionCall(
            evaluateCall(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic A","location":"Wroclaw"}""",
            ),
            responder,
        )

        assertEquals(1, responder.outputs.size)
        assertEquals(
            RealtimeFunctionFollowup.ForceFunction(CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME),
            responder.followups.single(),
        )
        assertEquals(
            "permit-auto",
            JsonParser.parseString(responder.outputs.single()).asJsonObject
                .get("commitment_authorization").asString,
        )
    }

    @Test
    fun userApprovalAlsoForcesCommitToolOnlyAfterDecision() {
        val workflow = activeWorkflow("100.00")
        val gate = CallCommitmentGate { "permit-user" }
        val handler = CallRealtimeProposalFunctionHandler(workflow, gate)
        val responder = RecordingResponder()

        handler.onFunctionCall(
            evaluateCall(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic A","location":null}""",
            ),
            responder,
        )

        assertTrue(responder.outputs.isEmpty())
        assertTrue(handler.approvePendingProposal().isSuccess)
        assertEquals(
            RealtimeFunctionFollowup.ForceFunction(CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME),
            responder.followups.single(),
        )
    }

    @Test
    fun rejectedProposalKeepsAutomaticFollowupSoNegotiationCanContinue() {
        val workflow = activeWorkflow("100.00")
        val gate = CallCommitmentGate { "unused" }
        val handler = CallRealtimeProposalFunctionHandler(workflow, gate)
        val responder = RecordingResponder()

        handler.onFunctionCall(
            evaluateCall(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic A","location":null}""",
            ),
            responder,
        )
        assertTrue(handler.rejectPendingProposal().isSuccess)

        assertEquals(listOf(RealtimeFunctionFollowup.Auto), responder.followups)
    }

    @Test
    fun authorizedCommitDisablesToolsForImmediateSpokenFollowup() {
        val workflow = activeWorkflow("200.00")
        val gate = CallCommitmentGate { "permit-commit" }
        val proposalHandler = CallRealtimeProposalFunctionHandler(workflow, gate)
        val commitmentHandler = CallRealtimeCommitmentFunctionHandler(workflow, gate)
        val evaluation = RecordingResponder()
        proposalHandler.onFunctionCall(
            evaluateCall(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic A","location":null}""",
            ),
            evaluation,
        )
        val permit = JsonParser.parseString(evaluation.outputs.single()).asJsonObject
            .get("commitment_authorization").asString
        val commitment = RecordingResponder()

        commitmentHandler.onFunctionCall(
            RealtimeFunctionCall(
                "commit_1",
                CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME,
                "{\"authorization\":\"$permit\"}",
            ),
            commitment,
        )

        assertEquals(listOf(RealtimeFunctionFollowup.NoTools), commitment.followups)
        assertEquals("authorized", JsonParser.parseString(commitment.outputs.single()).asJsonObject.get("commitment").asString)
    }

    private class RecordingResponder : CallRealtimeFunctionResponder {
        val outputs = mutableListOf<String>()
        val followups = mutableListOf<RealtimeFunctionFollowup>()

        override fun submit(outputJson: String): Result<Unit> {
            outputs += outputJson
            followups += RealtimeFunctionFollowup.Auto
            return Result.success(Unit)
        }

        override fun submit(
            outputJson: String,
            followup: RealtimeFunctionFollowup,
        ): Result<Unit> {
            outputs += outputJson
            followups += followup
            return Result.success(Unit)
        }
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
            RealtimeFunctionCall(
                "evaluate_1",
                CallRealtimeProposalFunctionHandler.FUNCTION_NAME,
                arguments,
            )
    }
}
