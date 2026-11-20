package pl.michalmatu.aicallbridge.campaign
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog

class ScenarioCustomOutgoingTest {
    @Test fun customScenarioIsInformationOnlyAndTemplatesUnchanged() {
        val custom = requireNotNull(ScenarioCatalog.byId("custom"))
        assertNull(custom.callAdapterId)
        assertEquals(10, ScenarioCatalog.templates.size)
        assertEquals(listOf("additional_instructions"), custom.fields.filter { it.required }.map { it.id })
    }
    @Test fun customTaskHasExactEnquiryGrant() {
        val task = ScenarioOutgoingTask.create("+48123456789", 2,
            "voice-subscription:2:slot:1:carrier:0", "custom",
            "Własne pytania", "Uzyskaj informacje na temat usługi",
            "Zapytaj o możliwości, nie akceptuj zobowiązań.")
        assertEquals(CallService.INQUIRY, task.request().service)
        assertEquals("INQUIRE_SCENARIO_custom", task.request().action)
        val grant = task.grant(123L)
        assertTrue(CampaignAuthorizationPolicy.matchesExactTask(grant, task.request()))
        assertTrue(grant.allowedDisclosureFields.isEmpty())
        assertFalse(grant.recordCallEvidence)
        assertFalse(CampaignAuthorizationPolicy.matchesExactTask(grant,
            task.copy(target = "+48999888777").request()))
    }
}
