package pl.michalmatu.aicallbridge.conversation

import java.io.File
import java.nio.file.Files
import pl.michalmatu.aicallbridge.developerrelay.RecordedAudioClipName
import org.junit.Assert.*
import org.junit.Test

class RecordedConversationAudioHistoryTest {
    @Test fun localAudioSymlinksCannotRelabelTxAsRxOrAliasAnotherArchive() {
        val root = Files.createTempDirectory("aicalls-wave-alias").toFile()
        try {
            val session = File(root, "1000-private").apply { mkdirs() }
            val tx = File(session, "turn-01-tx.wav").apply { writeBytes(byteArrayOf(0, 0)) }
            Files.createSymbolicLink(File(session, "turn-01-rx.wav").toPath(), tx.toPath())
            File(session, "timeline.jsonl").writeText(
                """{"type":"RX_PCM","turn":1,"file":"turn-01-rx.wav"}""" + "\n" +
                """{"type":"TX_PCM","turn":1,"file":"turn-01-tx.wav"}""" + "\n" +
                """{"type":"END","reason_code":"completed"}""" + "\n",
            )
            val alias = File(root, "2000-alias")
            Files.createSymbolicLink(alias.toPath(), session.toPath())
            val history = RecordedConversationHistory(root)
            val loaded = history.load(session.name)
            assertFalse(loaded.audioFiles.containsKey(1 to "rx"))
            assertEquals("turn-01-tx.wav", loaded.audioFiles[1 to "tx"])
            assertEquals(listOf("turn-01-tx.wav"), loaded.audioTimeline)
            assertNull(history.resolveAudioFile(session.name, "turn-01-rx.wav"))
            assertNotNull(history.resolveAudioFile(session.name, "turn-01-tx.wav"))
            assertNull(history.resolveAudioFile(alias.name, "turn-01-tx.wav"))
            assertTrue(runCatching { history.load(alias.name) }.isFailure)
        } finally { root.deleteRecursively() }
    }

    @Test fun repeatedRxWindowsRetainDistinctClipsWithoutOverwritingFirstPart() {
        assertEquals("turn-01-rx.wav", RecordedAudioClipName.create(1, "rx"))
        assertEquals("turn-01-rx-part-002.wav", RecordedAudioClipName.create(1, "rx", 2))
        assertEquals("turn-01-rx-part-003.wav", RecordedAudioClipName.create(1, "rx", 3))
        assertFalse(RecordedAudioClipName.isSafe("turn-01-rx-part-001.wav"))
        assertFalse(RecordedAudioClipName.isSafe("turn-01-rx-part-000.wav"))
        assertFalse(RecordedAudioClipName.isSafe("../turn-01-rx.wav"))
        assertFalse(RecordedAudioClipName.matches("turn-02-rx-part-002.wav", 1, "rx"))

        val root = Files.createTempDirectory("aicalls-rx-parts").toFile()
        try {
            val session = File(root, "1000-multi-rx").apply { mkdirs() }
            listOf(
                "turn-01-rx.wav",
                "turn-01-rx-part-002.wav",
                "turn-01-rx-part-003.wav",
                "turn-01-tx.wav",
            ).forEach { name -> File(session, name).writeBytes(byteArrayOf(0, 0)) }
            File(session, "timeline.jsonl").writeText(listOf(
                """{"type":"RX_PCM","turn":1,"file":"turn-01-rx.wav","elapsed_ms":5}""",
                """{"type":"RX_PCM","turn":1,"file":"turn-01-rx-part-002.wav","elapsed_ms":10}""",
                """{"type":"TX_PCM","turn":1,"file":"turn-01-tx.wav","elapsed_ms":30}""",
                """{"type":"RX_PCM","turn":1,"file":"turn-01-rx-part-003.wav","elapsed_ms":20}""",
                """{"type":"RX_PCM","turn":1,"file":"turn-01-rx-part-001.wav","elapsed_ms":0}""",
                """{"type":"END","reason_code":"completed"}""",
            ).joinToString("\n", postfix = "\n"))
            val history = RecordedConversationHistory(root)
            val loaded = history.load(session.name)
            assertEquals(listOf(
                "turn-01-rx.wav", "turn-01-rx-part-002.wav",
                "turn-01-rx-part-003.wav", "turn-01-tx.wav",
            ), loaded.audioTimeline)
            assertNotNull(history.resolveAudioFile(session.name, "turn-01-rx.wav"))
            assertNotNull(history.resolveAudioFile(session.name, "turn-01-rx-part-002.wav"))
            assertNull(history.resolveAudioFile(session.name, "turn-01-rx-part-000.wav"))
            assertEquals(0, loaded.invalidRecords)
        } finally { root.deleteRecursively() }
    }

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
