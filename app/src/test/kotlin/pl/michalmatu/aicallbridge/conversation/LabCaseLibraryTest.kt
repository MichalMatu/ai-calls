package pl.michalmatu.aicallbridge.conversation

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class LabCaseLibraryTest {
    private val draft = LabCaseRevision(0, "Private fixture", "Assess context", LabRole.GENERAL,
        "Opening", "Caller state", "Fact A", "Preserve facts",
        "question => Clarify", "Finish when done")

    @Test fun createRevisePersistSelectDelete() {
        val initial = LabCaseLibrary().create(draft)
        val id = initial.cases.single().id
        assertEquals(1, initial.cases.single().selectedRevision)
        val revised = initial.update(id, draft.copy(title = "Updated"))
        assertEquals(2, revised.cases.single().versions.size)
        assertEquals(2, revised.cases.single().selectedRevision)
        val saved = Gson().fromJson(Gson().toJson(revised), LabCaseLibrary::class.java).validated()
        assertEquals("Updated", saved.cases.single().selected().title)
        val previous = saved.select(id, 1)
        assertEquals("Private fixture", previous.cases.single().selected().title)
        val copied = previous.duplicate(id)
        assertEquals(2, copied.cases.size)
        assertNotEquals(id, copied.cases.last().id)
        assertEquals(1, copied.cases.last().versions.size)
        assertEquals(1, copied.delete(id).cases.size)
        assertTrue(copied.delete(id).cases.none { it.id == id })
    }

    @Test fun rejectsInvalidInputAndMissingRevision() {
        try { LabCaseLibrary().create(draft.copy(goal = " ")); fail() }
        catch (_: IllegalArgumentException) { }
        val current = LabCaseLibrary().create(draft)
        try { current.select(current.cases.single().id, 42); fail() }
        catch (_: IllegalArgumentException) { }
        try { current.delete("missing"); fail() }
        catch (_: IllegalArgumentException) { }
        assertTrue(LabCaseLibrary().cases.isEmpty())
    }
}
