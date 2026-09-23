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
    fun `gate c request accepts phone local providers and exact allowlisted target`() {
        val qwen = LocalPhoneLlmLiveCallProbeRequest.create(
            provider = TextLlmProvider.LOCAL_PHONE_LLM,
            gateCFastPath = true,
            liveCallTarget = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
        )
        val gemma = LocalPhoneLlmLiveCallProbeRequest.create(
            provider = TextLlmProvider.EDGE_GALLERY,
            gateCFastPath = true,
            liveCallTarget = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
        )

        assertTrue(qwen.gateCFastPath)
        assertEquals(GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER, qwen.liveCallTarget)
        assertEquals(OrangeLiveAction.GREETING, qwen.orangeLiveAction)
        assertEquals(TextLlmProvider.EDGE_GALLERY, gemma.provider)
        assertFalse(gemma.gateCHybridDialogue)

        assertThrows(IllegalArgumentException::class.java) {
            LocalPhoneLlmLiveCallProbeRequest.create(
                provider = TextLlmProvider.LOCAL_PHONE_LLM,
                gateCFastPath = true,
                liveCallTarget = "501234567",
            )
        }
    }

    @Test
    fun `gate c relay session explicitly enables hybrid dialogue`() {
        val request = LocalPhoneLlmLiveCallProbeRequest.create(
            provider = TextLlmProvider.EDGE_GALLERY,
            gateCFastPath = true,
            liveCallTarget = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
            gateCRelaySessionId = "  gate-c-hybrid-1  ",
        )

        assertTrue(request.gateCHybridDialogue)
        assertEquals("gate-c-hybrid-1", request.gateCRelaySessionId)

        assertThrows(IllegalArgumentException::class.java) {
            LocalPhoneLlmLiveCallProbeRequest.create(
                provider = TextLlmProvider.EDGE_GALLERY,
                gateCFastPath = false,
                liveCallTarget = null,
                gateCRelaySessionId = "relay-without-gate-c",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalPhoneLlmLiveCallProbeRequest.create(
                provider = TextLlmProvider.EDGE_GALLERY,
                gateCFastPath = true,
                liveCallTarget = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
                gateCRelaySessionId = "bad session id",
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
    fun `legacy request preserves provider and carries no target action or relay`() {
        val request = LocalPhoneLlmLiveCallProbeRequest.create(
            provider = TextLlmProvider.EDGE_GALLERY,
            gateCFastPath = false,
            liveCallTarget = null,
        )

        assertFalse(request.gateCFastPath)
        assertFalse(request.gateCHybridDialogue)
        assertNull(request.liveCallTarget)
        assertNull(request.orangeLiveAction)
        assertNull(request.gateCRelaySessionId)
        assertEquals(TextLlmProvider.EDGE_GALLERY, request.provider)
    }
}
