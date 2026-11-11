package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteProposal

/** Synthetic isolated storage only: never touch the owner's notes or a Call. */
@RunWith(AndroidJUnit4::class)
class AndroidNotebookEncryptionAcceptanceTest {
    @Test fun encryptedAppendSurvivesReopenWithoutPlaintextLeakage() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString()
        val dir = File(app.cacheDir, "notebook-qa-" + suffix)
        assertTrue(dir.mkdirs())
        val isolated = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = dir
        }
        val alias = "pl.michalmatu.aicallbridge.notebook.test." + suffix
        val prefs = "notebook-qa-" + suffix
        val synthetic = "SYNTHETIC_NOTE_VALUE_" + suffix
        try {
            val store = AndroidConversationPreparationStore(isolated, "notebook-qa", alias, prefs)
            assertTrue(store.notebooks().isEmpty())
            val note = ConversationNoteProposal(ConversationNoteKind.NOTE, synthetic)
            assertTrue(store.appendNote("incoming-qa", "INCOMING", 1, note))
            assertTrue(store.appendNote("incoming-qa", "INCOMING", 1, note))
            assertTrue(store.appendNote("incoming-qa", "INCOMING", 2,
                ConversationNoteProposal(ConversationNoteKind.CALENDAR_PROPOSAL, "Proponowany termin")))
            assertTrue(store.appendNote("outgoing-qa", "OUTGOING", 1,
                ConversationNoteProposal(ConversationNoteKind.TASK, "Następny krok")))
            val reopened = AndroidConversationPreparationStore(isolated, "notebook-qa", alias, prefs)
            val books = reopened.notebooks()
            assertEquals(2, books.size)
            // Simulate legacy AtomicFile crash state: encrypted index exists only as .bak.
            val indexHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest("notebook-index-v2".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val indexBase = File(dir, "notebook-qa/$indexHash.json")
            val backup = File(indexBase.parentFile, indexBase.name + ".bak")
            assertTrue(indexBase.renameTo(backup))
            val recovered = AndroidConversationPreparationStore(isolated, "notebook-qa", alias, prefs)
            assertEquals(2, recovered.notebooks().size)
            assertTrue(indexBase.exists())
            assertTrue(recovered.deleteNotebook("incoming-qa"))
            assertEquals(listOf("outgoing-qa"),
                reopened.notebooks().map { it.sessionId })
            assertEquals(2, books.single { it.sessionId == "incoming-qa" }.entries.size)
            assertEquals(synthetic, books.single { it.sessionId == "incoming-qa" }.entries[0].text)
            assertEquals(1, books.single { it.sessionId == "outgoing-qa" }.entries.size)
            File(dir, "notebook-qa").listFiles().orEmpty().forEach {
                assertFalse(it.readText().contains(synthetic))
            }
        } finally {
            dir.deleteRecursively()
            app.deleteSharedPreferences(prefs)
        }
    }
}
