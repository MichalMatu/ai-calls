package pl.michalmatu.aicallbridge.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.michalmatu.aicallbridge.realtime.RealtimeOutputPartId
import pl.michalmatu.aicallbridge.session.CallRealtimeOutputDecision

class CallRealtimeAgentOutputApprovalPolicyTest {
    private val partId = RealtimeOutputPartId("resp_1", "item_1", 0, 0)

    @Test
    fun releasesOnlyNormalActiveNegotiationWithoutPendingCommitmentAuthority() {
        val workflow = activeWorkflow(CallPreferences.none())
        val gate = CallCommitmentGate { "policy-token" }
        val policy = CallRealtimeAgentOutputApprovalPolicy(workflow, gate)

        assertEquals(
            CallRealtimeOutputDecision.RELEASE,
            policy.evaluate(partId, "Dzień dobry, pytam o dostępne terminy."),
        )

        gate.authorize(CallProposal(null, null, null, "Clinic A", "Wroclaw"))
        assertEquals(
            CallRealtimeOutputDecision.DROP,
            policy.evaluate(partId, "Zgadzam się i rezerwuję."),
        )

        gate.clear()
        assertEquals(
            CallRealtimeOutputDecision.RELEASE,
            policy.evaluate(partId, "Czy jest inny termin?"),
        )
    }

    @Test
    fun dropsSpeechOutsideActiveNegotiationIncludingPendingUserDecision() {
        val researching = workflow(CallPreferences.none())
        val researchingPolicy = CallRealtimeAgentOutputApprovalPolicy(
            researching,
            CallCommitmentGate { "research-token" },
        )
        assertEquals(
            CallRealtimeOutputDecision.DROP,
            researchingPolicy.evaluate(partId, "anything"),
        )

        val pending = activeWorkflow(
            CallPreferences(
                emptyList(),
                listOf("Clinic A"),
                emptyList(),
            ),
        )
        pending.evaluateProposal(CallProposal(null, null, null, "Clinic B", "Wroclaw"))
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, pending.snapshot().state())

        val pendingPolicy = CallRealtimeAgentOutputApprovalPolicy(
            pending,
            CallCommitmentGate { "pending-token" },
        )
        assertEquals(
            CallRealtimeOutputDecision.DROP,
            pendingPolicy.evaluate(partId, "To rezerwuję bez pytania użytkownika."),
        )
    }

    @Test
    fun sessionSpecBindsOutputPolicyToTheSameCommitmentGate() {
        val workflow = activeWorkflow(CallPreferences.none())
        val spec = CallRealtimeAgentSessionSpec.create(
            workflow = workflow,
            sessionEndpoint = "wss://api.openai.com/v1/realtime",
            model = "gpt-realtime-2.1",
        )

        assertEquals(
            CallRealtimeOutputDecision.RELEASE,
            spec.outputApprovalPolicy.evaluate(partId, "Normalna negocjacja."),
        )

        spec.commitmentGate.authorize(CallProposal(null, null, null, "Clinic A", "Wroclaw"))
        assertEquals(
            CallRealtimeOutputDecision.DROP,
            spec.outputApprovalPolicy.evaluate(partId, "Przedwczesne potwierdzenie."),
        )
    }

    private fun activeWorkflow(preferences: CallPreferences): CallWorkflow =
        workflow(preferences).apply {
            resolveTarget(CallResolvedTarget("Clinic A", "510100100"))
            markDialing()
            markCallActive()
        }

    private fun workflow(preferences: CallPreferences): CallWorkflow =
        CallWorkflow(
            CallTask(
                "dermatolog",
                "umow wizyte",
                "konsultacja",
                CallConstraints.unconstrained(),
                preferences,
                emptyMap(),
            ),
            CallConfirmationPolicy(),
        ) { }
}
