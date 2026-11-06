package pl.michalmatu.aicallbridge.conversation

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RecordedConversationAudioHistoryTest {
    @Test fun onlyExistingAudioSegmentsAreLinkedAndPathsCannotEscapePrivateArchive() {
        val root = Files.createTempDirectory("aicalls-history-wav").toFile()
        try {
            val session = File(root, "1000-fixture").apply { mkdirs() }
            listOf("turn-01-rx.wav", "turn-02-tx.wav", "turn-02-tx-ack.wav").forEach {
                File(session, it).writeBytes(byteArrayOf(1, 2))
            }
            File(session, "timeline.jsonl").writeText(listOf(
                """{"type":"CONVERSATION","direction":"INCOMING","started_at_ms":1000}""",
                """{"type":"COUNTERPART_STT","turn":1,"text":"Dzien dobry","elapsed_ms":10}""",
                """{"type":"RX_PCM","turn":1,"file":"turn-01-rx.wav"}""",
                """{"type":"AGENT_APPROVED_TEXT","turn":2,"text":"Slucham.","elapsed_ms":20}""",
                """{"type":"TX_PCM","turn":2,"file":"turn-02-tx.wav"}""",
                """{"type":"TX_ACK_PCM","turn":2,"file":"turn-02-tx-ack.wav"}""",
                """{"type":"RX_PCM","turn":3,"file":"../secret.wav"}""",
                """{"type":"TX_PCM","turn":4,"file":null,"storage_error":true}""",
                """{"type":"END"}""",
            ).joinToString("\n"))
            val history = RecordedConversationHistory(root)
            val loaded = history.load(session.name)
            assertTrue(loaded.complete)
            assertEquals(2, loaded.lines.size)
            assertEquals(3, loaded.audioFiles.size)
            assertEquals("turn-01-rx.wav", loaded.audioFor(loaded.lines[0]))
            assertEquals("turn-02-tx.wav", loaded.audioFor(loaded.lines[1]))
            assertNotNull(history.resolveAudioFile(session.name, "turn-01-rx.wav"))
            assertNull(history.resolveAudioFile(session.name, "../../secret.wav"))
            assertNull(history.resolveAudioFile("../", "turn-01-rx.wav"))
            assertNull(history.resolveAudioFile(session.name, "turn-99-rx.wav"))
            assertFalse(loaded.audioFiles.values.contains("../secret.wav"))
        } finally {
            root.deleteRecursively()
        }
    }
}
