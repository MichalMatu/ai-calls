package pl.michalmatu.aicallbridge.campaign

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class CampaignEvidenceArchiveTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun completePrivateSessionArchiveContainsOnlyTimelineAndAudio() {
        val root = folder.newFolder("sessions")
        val session = File(root, "2026-session")
        assertTrue(session.mkdir())
        File(session, "timeline.jsonl").writeText("{\"type\":\"START\"}\n")
        File(session, "turn-01-rx.wav").writeBytes(byteArrayOf(1, 2, 3))
        File(session, "turn-01-tx.wav").writeBytes(byteArrayOf(4, 5, 6))
        File(session, "unrelated-secret.txt").writeText("never export")
        File(session, "COMPLETE").writeText("local_complete")

        val destination = ByteArrayOutputStream()
        assertEquals(4, CampaignEvidenceArchive.writeCompletedSession(session, destination))
        val archived = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(destination.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                archived[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        assertEquals(
            setOf("COMPLETE", "timeline.jsonl", "turn-01-rx.wav", "turn-01-tx.wav"),
            archived.keys,
        )
        assertTrue(archived["timeline.jsonl"]!!.toString(Charsets.UTF_8).contains("START"))
        assertEquals(listOf(session), CampaignEvidenceArchive.completedSessions(root))
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteSessionIsNeverExported() {
        val session = folder.newFolder("unfinished")
        File(session, "timeline.jsonl").writeText("{}")
        CampaignEvidenceArchive.writeCompletedSession(session, ByteArrayOutputStream())
    }
}
