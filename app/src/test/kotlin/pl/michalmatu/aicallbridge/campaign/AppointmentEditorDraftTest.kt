package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.*
import org.junit.Test

class AppointmentEditorDraftTest {
    @Test fun validDraftAndNoAuthority() {
        val draft = AppointmentEditorDraft.create(
            "+48717117400", "Zapytaj o najbliższy wolny termin",
            "2026-11-11 09:00", "2026-12-31 17:00", "NFZ bez opłat")
        assertNotNull(draft)
        assertEquals("+48717117400", draft!!.target)
        assertEquals("2026-11-11 09:00", AppointmentEditorDraft.date(draft.from))
        val properties = AppointmentEditorDraft::class.java.declaredFields.map { it.name }.toSet()
        for (name in listOf("subscriptionId", "grantId", "disclosureFields",
            "identityFields", "authorized", "placeCall")) assertFalse(name, name in properties)
    }

    @Test fun badNumberDateAndRangeFailClosed() {
        assertNull(AppointmentEditorDraft.create("717117400", "Wizyta POZ",
            "2026-11-11 09:00", "2026-11-12 09:00", ""))
        assertNull(AppointmentEditorDraft.create("+48717117400", "Wizyta POZ",
            "2026-11-11 09:00a2026-11-11 09:00", "2026-11-12 09:00", ""))
        assertNull(AppointmentEditorDraft.create("+48717117400", "Wizyta POZ",
            "2026-12-11 09:00", "2026-11-12 09:00", ""))
    }
}
