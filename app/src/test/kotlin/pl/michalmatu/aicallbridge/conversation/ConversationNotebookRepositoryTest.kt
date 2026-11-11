package pl.michalmatu.aicallbridge.conversation

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteProposal

class ConversationNotebookRepositoryTest {
    private val note = ConversationNoteProposal(ConversationNoteKind.NOTE, "synthetic A")
    private val plan = ConversationNoteProposal(ConversationNoteKind.CALENDAR_PROPOSAL, "proposed time")

    @Test fun eachCallHasIndependentEncryptedRecordIdentifierAndDeletionLeavesOtherIntact() {
        val records = mutableMapOf<String, String>()
        val repo = ConversationNotebookRepository(
            read = records::get,
            write = { key, value -> records[key] = value },
            remove = { key -> records.remove(key); Unit },
            now = { 1234L },
        )
        assertTrue(repo.append("incoming-one", "INCOMING", 1, note))
        assertTrue(repo.append("incoming-one", "INCOMING", 2, plan))
        assertTrue(repo.append("outgoing-two", "OUTGOING", 1, note))
        assertTrue(repo.append("incoming-one", "INCOMING", 1, note))
        assertEquals(2, repo.list().size)
        assertEquals(2, repo.list().single { it.sessionId == "incoming-one" }.entries.size)
        assertTrue(records.containsKey("notebook-call-v2-incoming-one"))
        assertTrue(records.containsKey("notebook-call-v2-outgoing-two"))
        assertFalse(records.containsKey("notebook-library-v1"))
        assertTrue(repo.delete("incoming-one"))
        assertFalse(repo.delete("incoming-one"))
        assertEquals(listOf("outgoing-two"), repo.list().map { it.sessionId })
        assertFalse(records.containsKey("notebook-call-v2-incoming-one"))
        assertTrue(records.containsKey("notebook-call-v2-outgoing-two"))
    }

    @Test fun oldNotebookLibraryMigratesBeforeReadAndCanBeRetriedAfterFailedIndexWrite() {
        val legacy = ConversationNotebookLibrary(conversations = listOf(
            ConversationNotebook("incoming-old", "INCOMING",
                listOf(ConversationNotebookEntry("incoming-old:1", 1, 1000L,
                    ConversationNoteKind.NOTE, "legacy synthetic"))),
            ConversationNotebook("outgoing-old", "OUTGOING",
                listOf(ConversationNotebookEntry("outgoing-old:1", 1, 2000L,
                    ConversationNoteKind.TASK, "old task"))),
        ))
        val records = mutableMapOf("notebook-library-v1" to Gson().toJson(legacy))
        var rejectIndex = true
        fun repository() = ConversationNotebookRepository(
            read = records::get,
            write = { key, value ->
                if (key == "notebook-index-v2" && rejectIndex)
                    error("synthetic write interruption")
                records[key] = value
            },
            remove = { key -> records.remove(key); Unit },
        )
        assertTrue(runCatching { repository().list() }.isFailure)
        assertTrue(records.containsKey("notebook-library-v1"))
        assertTrue(records.containsKey("notebook-call-v2-incoming-old"))
        rejectIndex = false
        val books = repository().list()
        assertEquals(2, books.size)
        assertEquals("legacy synthetic",
            books.single { it.sessionId == "incoming-old" }.entries.single().text)
        assertFalse(records.containsKey("notebook-library-v1"))
        assertTrue(repository().append("incoming-old", "INCOMING", 2, plan))
        assertEquals(2, repository().list().single {
            it.sessionId == "incoming-old"
        }.entries.size)
    }

    @Test fun failedNewCallAppendLeavesRecoverableIndexWithoutOrphaningNotes() {
        val records = mutableMapOf<String, String>()
        var interrupt = true
        fun repository() = ConversationNotebookRepository(
            read = records::get,
            write = { key, value ->
                if (interrupt && key == "notebook-call-v2-incoming-interrupted")
                    error("synthetic_crash_before_record")
                records[key] = value
            },
            remove = { key -> records.remove(key); Unit },
            now = { 9000L },
        )
        assertTrue(runCatching {
            repository().append("incoming-interrupted", "INCOMING", 1, note)
        }.isFailure)
        assertTrue(records.containsKey("notebook-index-v2"))
        assertTrue(repository().list().isEmpty())
        interrupt = false
        assertTrue(repository().append("incoming-interrupted", "INCOMING", 1, note))
        assertEquals(1, repository().list().single().entries.size)
    }

    @Test fun filtersAndOrdersWithoutDecryptionOrAnyAndroidDependency() {
        val incoming = ConversationNotebook("incoming-x", "INCOMING",
            listOf(ConversationNotebookEntry("incoming-x:1", 1, 1000L,
                ConversationNoteKind.NOTE, "Warsztat za 170 PLN")))
        val outgoing = ConversationNotebook("outgoing-x", "OUTGOING",
            listOf(ConversationNotebookEntry("outgoing-x:1", 1, 2000L,
                ConversationNoteKind.CALENDAR_PROPOSAL, "Thursday proposed")))
        assertEquals(listOf("outgoing-x", "incoming-x"),
            ConversationNotebookListPolicy.filter(listOf(incoming, outgoing), "", null)
                .map { it.sessionId })
        assertEquals(listOf("incoming-x"),
            ConversationNotebookListPolicy.filter(listOf(incoming, outgoing), "170", "INCOMING")
                .map { it.sessionId })
        assertEquals(listOf("outgoing-x"),
            ConversationNotebookListPolicy.filter(listOf(incoming, outgoing), "calendar", null)
                .map { it.sessionId })
        assertTrue(ConversationNotebookListPolicy.filter(
            listOf(incoming, outgoing), "missing", null).isEmpty())
    }
}
