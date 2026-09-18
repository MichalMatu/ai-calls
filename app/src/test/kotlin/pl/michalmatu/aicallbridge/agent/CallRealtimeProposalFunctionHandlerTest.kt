package pl.michalmatu.aicallbridge.agent

import com.google.gson.JsonParser
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionResponder

class CallRealtimeProposalFunctionHandlerTest {
    @Test
    fun toolSchemaIsStrictAndRequiresExplicitNullableProposalFields() {
        val tool = CallRealtimeProposalFunctionHandler.tool()

        assertEquals("evaluate_proposal", tool.name)
        assertTrue(tool.parametersJson.contains("\"additionalProperties\":false"))
        assertTrue(tool.parametersJson.contains("\"scheduled_at\""))
        assertTrue(tool.parametersJson.contains("\"price\""))
        assertTrue(tool.parametersJson.contains("\"payment_mode\""))
        assertTrue(tool.parametersJson.contains("\"provider\""))
        assertTrue(tool.parametersJson.contains("\"location\""))
        assertTrue(tool.parametersJson.contains("\"required\":[\"scheduled_at\",\"price\",\"payment_mode\",\"provider\",\"location\"]"))
    }

    @Test
    fun inPolicyProposalReturnsAutonomousDecisionImmediately() {
        val workflow = activeWorkflow(
            CallConstraints(
                listOf(),
                MoneyAmount(BigDecimal("200.00"), "PLN"),
                setOf(CallPaymentMode.PRIVATE),
            ),
        )
        val handler = CallRealtimeProposalFunctionHandler(workflow)
        val responder = CapturingResponder()

        handler.onFunctionCall(
            call(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":"PRIVATE","provider":"Clinic A","location":"Wroclaw"}""",
            ),
            responder,
        )

        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        val output = JsonParser.parseString(responder.outputs.single()).asJsonObject
        assertEquals("autonomously_allowed", output.get("decision").asString)
        assertTrue(output.get("commitment_authorization").asString.isNotBlank())
        assertFalse(handler.hasPendingUserDecision())
    }

    @Test
    fun outOfPolicyProposalWaitsForUserThenApprovalUsesHeldResponderOnce() {
        val workflow = activeWorkflow(
            CallConstraints(
                listOf(),
                MoneyAmount(BigDecimal("100.00"), "PLN"),
                setOf(),
            ),
        )
        val originalTask = workflow.snapshot().task()
        val handler = CallRealtimeProposalFunctionHandler(workflow)
        val responder = CapturingResponder()

        handler.onFunctionCall(
            call(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":null,"provider":"Clinic A","location":"Wroclaw"}""",
            ),
            responder,
        )

        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state())
        assertEquals(listOf(CallPolicyReason.PRICE_ABOVE_MAX), workflow.snapshot().pendingDecision().reasons())
        assertTrue(handler.hasPendingUserDecision())
        assertTrue(responder.outputs.isEmpty())

        val result = handler.approvePendingProposal()

        assertTrue(result.isSuccess)
        assertEquals("Clinic A", result.getOrThrow().provider())
        val output = JsonParser.parseString(responder.outputs.single()).asJsonObject
        assertEquals("user_approved", output.get("decision").asString)
        assertTrue(output.get("commitment_authorization").asString.isNotBlank())
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        assertFalse(handler.hasPendingUserDecision())
        assertSame(originalTask, workflow.snapshot().task())
        assertEquals(BigDecimal("100.00"), workflow.snapshot().task().constraints().maxPrice().amount())
        assertTrue(handler.approvePendingProposal().isFailure)
    }

    @Test
    fun rejectionReturnsToNegotiationWithoutWideningAuthority() {
        val workflow = activeWorkflow(
            CallConstraints(
                listOf(),
                MoneyAmount(BigDecimal("100.00"), "PLN"),
                setOf(),
            ),
        )
        val handler = CallRealtimeProposalFunctionHandler(workflow)
        val responder = CapturingResponder()
        handler.onFunctionCall(
            call(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":null,"provider":"Clinic A","location":null}""",
            ),
            responder,
        )

        val result = handler.rejectPendingProposal()

        assertTrue(result.isSuccess)
        assertEquals(listOf("{\"decision\":\"user_rejected\"}"), responder.outputs)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        assertEquals(BigDecimal("100.00"), workflow.snapshot().task().constraints().maxPrice().amount())
    }

    @Test
    fun staleResponderFailureDoesNotFalselyApproveWorkflow() {
        val workflow = activeWorkflow(
            CallConstraints(
                listOf(),
                MoneyAmount(BigDecimal("100.00"), "PLN"),
                setOf(),
            ),
        )
        val handler = CallRealtimeProposalFunctionHandler(workflow)
        val responder = CapturingResponder(
            submitResult = Result.failure(IllegalStateException("stale responder")),
        )
        handler.onFunctionCall(
            call(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":null,"provider":"Clinic A","location":null}""",
            ),
            responder,
        )

        val result = handler.approvePendingProposal()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("stale responder"))
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state())
        assertTrue(handler.hasPendingUserDecision())
        assertTrue(responder.outputs.isEmpty())
    }

    @Test
    fun malformedOrUnexpectedToolCallsFailClosedBeforePolicy() {
        val workflow = activeWorkflow(CallConstraints.unconstrained())
        val handler = CallRealtimeProposalFunctionHandler(workflow)
        val responder = CapturingResponder()

        assertFails<IllegalArgumentException> {
            handler.onFunctionCall(
                RealtimeFunctionCall("call_x", "other_tool", "{}"),
                responder,
            )
        }
        assertFails<IllegalArgumentException> {
            handler.onFunctionCall(
                call(
                    """{"scheduled_at":null,"price":null,"payment_mode":null,"provider":null,"location":null,"extra":"nope"}""",
                ),
                responder,
            )
        }
        assertFails<IllegalArgumentException> {
            handler.onFunctionCall(
                call(
                    """{"scheduled_at":"not-a-date","price":null,"payment_mode":null,"provider":null,"location":null}""",
                ),
                responder,
            )
        }
        assertFails<IllegalArgumentException> {
            handler.onFunctionCall(
                call(
                    """{"scheduled_at":null,"price":{"amount":"10.00","currency":"BAD"},"payment_mode":null,"provider":null,"location":null}""",
                ),
                responder,
            )
        }

        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
        assertTrue(responder.outputs.isEmpty())
    }

    @Test
    fun secondProposalCannotReplaceOneWaitingForUserDecision() {
        val workflow = activeWorkflow(
            CallConstraints(
                listOf(),
                MoneyAmount(BigDecimal("100.00"), "PLN"),
                setOf(),
            ),
        )
        val handler = CallRealtimeProposalFunctionHandler(workflow)
        val first = CapturingResponder()
        handler.onFunctionCall(
            call(
                """{"scheduled_at":null,"price":{"amount":"150.00","currency":"PLN"},"payment_mode":null,"provider":"Clinic A","location":null}""",
            ),
            first,
        )
        val pending = workflow.snapshot().pendingProposal()

        assertFails<IllegalStateException> {
            handler.onFunctionCall(
                RealtimeFunctionCall(
                    "call_2",
                    "evaluate_proposal",
                    """{"scheduled_at":null,"price":null,"payment_mode":null,"provider":"Clinic B","location":null}""",
                ),
                CapturingResponder(),
            )
        }

        assertSame(pending, workflow.snapshot().pendingProposal())
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state())
    }

    private class CapturingResponder(
        private val submitResult: Result<Unit> = Result.success(Unit),
    ) : CallRealtimeFunctionResponder {
        val outputs = mutableListOf<String>()
        val calls = AtomicInteger()

        override fun submit(outputJson: String): Result<Unit> {
            calls.incrementAndGet()
            if (submitResult.isSuccess) outputs += outputJson
            return submitResult
        }
    }

    companion object {
        private fun activeWorkflow(constraints: CallConstraints): CallWorkflow {
            val workflow = CallWorkflow(
                CallTask(
                    "clinic",
                    "book",
                    "consultation",
                    constraints,
                    CallPreferences.none(),
                    mapOf(),
                ),
                CallConfirmationPolicy(),
            ) { }
            workflow.resolveTarget(CallResolvedTarget("Clinic", "+48123456789"))
            workflow.markDialing()
            workflow.markCallActive()
            return workflow
        }

        private fun call(arguments: String) =
            RealtimeFunctionCall("call_1", "evaluate_proposal", arguments)

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
