package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.agent.CallService

class ScenarioOutgoingTaskTest {
    private val subscription = 2
    private val scope = "voice-subscription:2:slot:1:carrier:0"
    private val phone = "+48123456789"

    private fun task(templateId: String) = ScenarioOutgoingTask.create(
        phone, subscription, scope, templateId,
        checkNotNull(ScenarioCatalog.byId(templateId)).title("pl"),
        checkNotNull(ScenarioCatalog.byId(templateId)).goal("pl"),
        "Cel użytkownika: zapytaj o dostępne terminy i warunki; bez zobowiązań.",
    )

    @Test fun everyTemplateProducesDistinctSingleTargetEnquiryNotAppointmentGrant() {
        assertEquals(10, ScenarioCatalog.templates.size)
        for (template in ScenarioCatalog.templates) {
            val candidate = task(template.id)
            val grant = candidate.grant(123L)
            assertTrue(template.id,
                CampaignAuthorizationPolicy.matchesExactTask(grant, candidate.request()))
            assertEquals(CallService.INQUIRY, candidate.request().service)
            assertEquals("INQUIRE_SCENARIO_" + template.id, candidate.request().action)
            assertTrue(candidate.request().disclosureFields.isEmpty())
            assertTrue(grant.allowedDisclosureFields.isEmpty())
            assertFalse(grant.recordCallEvidence)
            assertEquals(0, grant.attemptsUsed)
            assertFalse(CampaignAuthorizationPolicy.matchesExactTask(grant,
                candidate.copy(target = "+48999888777").request()))
            assertFalse(CampaignAuthorizationPolicy.matchesExactTask(grant,
                candidate.copy(accountScope = "other-sim").request()))
        }
    }

    @Test fun sameNumberAndSimCannotChangeAuthorizedTemplate() {
        val mechanic = task("mechanic")
        val dentist = task("dentist")
        assertFalse(CampaignAuthorizationPolicy.matchesExactTask(
            mechanic.grant(123L), dentist.request()))
        assertEquals("mechanic", mechanic.scenarioId)
    }

    @Test fun invalidTargetTemplateOrOversizedBriefNeverCreateAuthorizedTask() {
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioOutgoingTask.create("12345", subscription, scope, "mechanic",
                "Mechanik", "Wizyta w warsztacie", "Zapytaj o cenę")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioOutgoingTask.create(phone, subscription, scope, "nonexistent",
                "Usługa", "Zapytaj o usługę", "Zapytaj o cenę")
        }
        assertThrows(IllegalArgumentException::class.java) {
            task("mechanic").copy(brief = "x".repeat(3501))
        }
    }

    @Test fun taskNeverLogsSensitiveNumberOrBrief() {
        val candidate = task("mechanic")
        assertFalse(candidate.toString().contains(candidate.target))
        assertFalse(candidate.toString().contains(candidate.brief))
    }
}
