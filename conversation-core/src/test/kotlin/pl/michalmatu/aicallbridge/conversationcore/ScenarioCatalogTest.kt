package pl.michalmatu.aicallbridge.conversationcore

import org.junit.Assert.*
import org.junit.Test

class ScenarioCatalogTest {
    @Test fun tenEverydayPresetsAreUniqueAndOnlyClirHasExistingAdapter() {
        assertEquals(10, ScenarioCatalog.templates.size)
        assertEquals(10, ScenarioCatalog.templates.map { it.id }.distinct().size)
        assertEquals("clir-enable", ScenarioCatalog.byId("clir")?.callAdapterId)
        assertTrue(ScenarioCatalog.templates.filter { it.id != "clir" }
            .all { it.callAdapterId == null })
        assertNull(ScenarioCatalog.byId("unknown"))
    }

    @Test fun mechanicFormUsesApprovedExampleAndCanBeEditedWithoutGrant() {
        val template = checkNotNull(ScenarioCatalog.byId("mechanic"))
        val draft = template.draft()
        assertEquals("Diagnostyka samochodu", draft.values["visit_reason"])
        assertEquals("Przyszły tydzień, najlepiej rano", draft.values["preferred_time"])
        assertEquals("300 zł", draft.values["budget"])
        assertTrue(draft.values.getValue("additional_instructions")
            .contains("Nie zatwierdzaj dodatkowych napraw"))
        assertTrue(draft.missingRequiredFields(template).isEmpty())
        val revised = draft.withValue("preferred_time", "Środa rano", template)
        assertEquals("Przyszły tydzień, najlepiej rano", draft.values["preferred_time"])
        assertTrue(revised.summary(template).contains("Środa rano"))
        assertTrue(revised.summary(template, "en").contains("Preferred time: Środa rano"))
        assertThrows(IllegalArgumentException::class.java) {
            draft.withValue("phone_grant", "granted", template)
        }
    }

    @Test fun clinicRequiresUserDetailsAndCannotInventAppointment() {
        val clinic = checkNotNull(ScenarioCatalog.byId("medical_clinic"))
        val draft = clinic.draft()
        assertEquals(listOf("specialty", "preferred_time"), draft.missingRequiredFields(clinic))
        val filled = draft.withValue("specialty", "Kardiolog", clinic)
            .withValue("preferred_time", "Wtorek po 12", clinic)
        assertEquals(emptyList<String>(), filled.missingRequiredFields(clinic))
        assertTrue(filled.summary(clinic).contains("Zapytaj o dostępne terminy"))
    }

    @Test fun duplicateFieldsAndMismatchedDraftAreRejected() {
        val sample = ScenarioFieldDefinition("service", "Usługa", "Service")
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioDefinition("bad", "Zadanie", "Task", "Cel", "Goal", listOf(sample, sample))
        }
        val mechanic = checkNotNull(ScenarioCatalog.byId("mechanic"))
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioDraft("restaurant", emptyMap()).summary(mechanic)
        }
    }
}
