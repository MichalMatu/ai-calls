package pl.michalmatu.aicallbridge.conversationcore

import org.junit.Assert.*
import org.junit.Test

class ScenarioPersonalLibraryTest {
    private val template = checkNotNull(ScenarioCatalog.byId("mechanic"))
    private val initial = template.draft()

    @Test fun createVersionsAndRestoreAreImmutable() {
        val library = ScenarioPersonalLibrary().create(
            template, initial, "personal-a", "Moja wizyta", "Zapytaj o diagnostykę")
        val changed = library.revise("personal-a", "Moja wizyta",
            "Ustal termin", initial.withValue("budget", "500 zł", template))
        assertEquals("300 zł", library.items.single().current.values["budget"])
        assertEquals("500 zł", changed.items.single().current.values["budget"])
        assertEquals(2, changed.items.single().current.number)
        val restored = changed.restore("personal-a", 1)
        assertEquals(3, restored.items.single().current.number)
        assertEquals("300 zł", restored.items.single().current.values["budget"])
        assertEquals(2, changed.items.single().current.number)
    }

    @Test fun duplicateSaveDoesNotCreateRedundantRevisionAndDeleteIsScoped() {
        val a = ScenarioPersonalLibrary().create(
            template, initial, "a", "Serwis", "Zapytaj o termin")
        val both = a.create(template, initial, "b", "Serwis", "Zapytaj o termin")
        assertEquals(1, both.revise("a", "Serwis", "Zapytaj o termin", initial)
            .items.first().current.number)
        assertEquals(listOf("b"), both.delete("a").items.map { it.id })
        assertEquals(2, both.items.size)
    }

    @Test fun rejectsInventedFieldsUnknownVersionsAndDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioPersonalLibrary().create(template,
                ScenarioDraft("mechanic", mapOf("phone_grant" to "true")),
                "bad", "Bad", "Goal")
        }
        val a = ScenarioPersonalLibrary().create(template, initial, "a", "Serwis", "Goal")
        assertThrows(IllegalArgumentException::class.java) {
            a.create(template, initial, "a", "Again", "Goal")
        }
        assertThrows(IllegalArgumentException::class.java) { a.restore("a", 9) }
        assertThrows(IllegalArgumentException::class.java) { a.delete("absent") }
        assertThrows(IllegalArgumentException::class.java) {
            a.revise("a", " ", "Goal", initial)
        }
    }

    @Test fun validationRejectsCorruptRevisionStructure() {
        val invalid = PersonalScenario("a", "mechanic", listOf(
            PersonalScenarioVersion(3, "Serwis", "Cel", emptyMap())))
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioPersonalLibrary(listOf(invalid)).validated()
        }
    }
}
