package pl.michalmatu.aicallbridge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRealtimeAgentSessionSpecTest {
    @Test
    fun factoryAlwaysBindsTaskInstructionsProposalToolAndMatchingHandler() {
        val workflow = CallWorkflow(
            CallTask(
                "dermatolog we Wroclawiu",
                "umow wizyte",
                "konsultacja dermatologiczna",
                CallConstraints.unconstrained(),
                CallPreferences.none(),
                mapOf("full_name" to "Jan Kowalski"),
            ),
            CallConfirmationPolicy(),
        ) { }

        val spec = CallRealtimeAgentSessionSpec.create(
            workflow = workflow,
            sessionEndpoint = "wss://api.openai.com/v1/realtime",
            model = "gpt-realtime",
        )

        assertEquals("wss://api.openai.com/v1/realtime", spec.request.sessionEndpoint)
        assertEquals("gpt-realtime", spec.request.model)
        assertEquals(16_000, spec.request.sampleRateHz)
        assertEquals(1, spec.request.tools.size)
        assertEquals(CallRealtimeProposalFunctionHandler.FUNCTION_NAME, spec.request.tools.single().name)
        assertTrue(spec.request.instructions.contains("dermatolog we Wroclawiu"))
        assertTrue(spec.request.instructions.contains("evaluate_proposal"))
        assertSame(spec.proposalHandler, spec.functionCallHandler)
    }

    @Test
    fun callerCanOverrideSampleRateButCannotSupplyAnIndependentHandlerOrToolSet() {
        val workflow = CallWorkflow(
            CallTask(
                "restaurant",
                "ask about availability",
                "table reservation",
                CallConstraints.unconstrained(),
                CallPreferences.none(),
                mapOf(),
            ),
            CallConfirmationPolicy(),
        ) { }

        val spec = CallRealtimeAgentSessionSpec.create(
            workflow = workflow,
            sessionEndpoint = "wss://example.invalid/realtime",
            model = "test-model",
            sampleRateHz = 24_000,
        )

        assertEquals(24_000, spec.request.sampleRateHz)
        assertEquals(
            listOf(CallRealtimeProposalFunctionHandler.FUNCTION_NAME),
            spec.request.tools.map { it.name },
        )
        assertTrue(spec.functionCallHandler is CallRealtimeProposalFunctionHandler)
    }
}
