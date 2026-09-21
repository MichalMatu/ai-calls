package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

class LocalPhoneLiveCallModeTest {
    @Test
    fun `legacy mode preserves selected supported provider`() {
        val mode = LocalPhoneLiveCallModeFactory.create(
            provider = TextLlmProvider.EDGE_GALLERY,
            gateCFastPath = false,
            targetDialAddress = null,
        )

        assertFalse(mode.gateCFastPath)
        assertEquals(TextLlmProvider.EDGE_GALLERY, mode.provider)
        assertEquals(null, mode.targetDialAddress)
        assertEquals(null, mode.fastPath)
    }

    @Test
    fun `gate c mode requires local provider and exact allowlisted target`() {
        val mode = LocalPhoneLiveCallModeFactory.create(
            provider = TextLlmProvider.LOCAL_PHONE_LLM,
            gateCFastPath = true,
            targetDialAddress = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
        )

        assertTrue(mode.gateCFastPath)
        assertEquals(TextLlmProvider.LOCAL_PHONE_LLM, mode.provider)
        assertEquals(GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER, mode.targetDialAddress)
        assertSame(mode.fastPath?.scenario?.workflow, mode.fastPath?.scenario?.workflow)
        assertEquals(0, mode.fastPath?.backend?.generateCalls)

        assertThrows(IllegalArgumentException::class.java) {
            LocalPhoneLiveCallModeFactory.create(
                provider = TextLlmProvider.EDGE_GALLERY,
                gateCFastPath = true,
                targetDialAddress = GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalPhoneLiveCallModeFactory.create(
                provider = TextLlmProvider.LOCAL_PHONE_LLM,
                gateCFastPath = true,
                targetDialAddress = "501234567",
            )
        }
    }
}
