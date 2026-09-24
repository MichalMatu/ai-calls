package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OrangeLiveActionAuthorityBoundaryTest {
    @Test
    fun `read-only caller id info remains available to legacy diagnostics`() {
        val action = OrangeLiveAction.fromWireId("caller_id_restriction_info")

        assertEquals(OrangeLiveAction.CALLER_ID_RESTRICTION_INFO, action)
        assertEquals("Jak działa zastrzeganie numeru?", action.reviewedResponse)
    }

    @Test
    fun `committing CLIR enable action requires generic authority`() {
        assertThrows(IllegalArgumentException::class.java) {
            OrangeLiveAction.fromWireId("caller_id_restriction_enable")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrangeLiveAction.CALLER_ID_RESTRICTION_ENABLE.reviewedResponse
        }
        assertThrows(IllegalArgumentException::class.java) {
            GateCLiveCallFastPathFactory.create(
                GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER,
                OrangeLiveAction.CALLER_ID_RESTRICTION_ENABLE,
            )
        }
    }
}
