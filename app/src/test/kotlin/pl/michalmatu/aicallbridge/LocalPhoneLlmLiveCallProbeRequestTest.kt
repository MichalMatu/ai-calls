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
        assertEquals(OrangeLiveAction.GREETING, request.orangeLiveAction)

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
    fun `gate c request accepts only known orange explorer actions`() {
        val request = LocalPhoneLlmLiveCallProbeRequest.create(
            provider = TextLlmProvider.LOCAL_PHONE_LLM,
            gateCFastPath = true,
            liveCallTarget = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
            orangeLiveActionId = "list_capabilities",
        )
        assertEquals(OrangeLiveAction.LIST_CAPABILITIES, request.orangeLiveAction)

        assertThrows(IllegalArgumentException::class.java) {
            LocalPhoneLlmLiveCallProbeRequest.create(
                provider = TextLlmProvider.LOCAL_PHONE_LLM,
                gateCFastPath = true,
                liveCallTarget = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
                orangeLiveActionId = "arbitrary_speech",
            )
        }
    }

    @Test
    fun `legacy request preserves provider and carries no target or explorer action`() {
        val request = LocalPhoneLlmLiveCallProbeRequest.create(
            provider = TextLlmProvider.EDGE_GALLERY,
            gateCFastPath = false,
            liveCallTarget = null,
        )

        assertFalse(request.gateCFastPath)
        assertNull(request.liveCallTarget)
        assertNull(request.orangeLiveAction)
        assertEquals(TextLlmProvider.EDGE_GALLERY, request.provider)
    }
}
