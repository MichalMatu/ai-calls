package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class GateCLiveCallFastPathTest {
    @Test
    fun `fast path binds exact scenario and forbids backend generation`() {
        val fastPath = GateCLiveCallFastPathFactory.create("510100100")

        assertEquals(
            GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
            fastPath.scenario.workflow.snapshot().resolvedTarget()?.dialAddress(),
        )
        assertSame(fastPath.scenario.callPlan.task(), fastPath.scenario.workflow.snapshot().task())

        var error: String? = null
        fastPath.backend.generate("dowolny tekst", object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) = error("sentinel must never complete")
            override fun onError(reason: String) {
                error = reason
            }
        })

        assertEquals("gate_c_backend_generate_forbidden", error)
        assertEquals(1, fastPath.backend.generateCalls)
        assertTrue(fastPath.backend.isClosed.not())
        fastPath.backend.close()
        assertTrue(fastPath.backend.isClosed)
    }
}
