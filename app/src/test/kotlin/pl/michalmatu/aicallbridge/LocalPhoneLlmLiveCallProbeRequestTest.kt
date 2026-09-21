package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

class LocalPhoneLlmLiveCallProbeRequestTest {
    @Test
    fun `gate c request requires local provider and exact allowlisted target`() {
        val request = LocalPhoneLlmLiveCallProbeRequest.create(
            provider = TextLlmProvider.LOCAL_PHONE_LLM,
            gateCFastPath = true,
            liveCallTarget = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
        )

        assertTrue(request.gateCFastPath)
        assertEquals(GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER, request.liveCallTarget)

        assertThrows(IllegalArgumentException::class.java) {
            LocalPhoneLlmLiveCallProbeRequest.create(
                provider = TextLlmProvider.EDGE_GALLERY,
                gateCFastPath = true,
                liveCallTarget = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalPhoneLlmLiveCallProbeRequest.create(
                provider = TextLlmProvider.LOCAL_PHONE_LLM,
                gateCFastPath = true,
                liveCallTarget = "501234567",
            )
        }
    }

    @Test
    fun `legacy request preserves provider and carries no target`() {
        val request = LocalPhoneLlmLiveCallProbeRequest.create(
            provider = TextLlmProvider.EDGE_GALLERY,
            gateCFastPath = false,
            liveCallTarget = null,
        )

        assertFalse(request.gateCFastPath)
        assertNull(request.liveCallTarget)
        assertEquals(TextLlmProvider.EDGE_GALLERY, request.provider)
    }
}
