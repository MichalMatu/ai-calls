package pl.michalmatu.aicallbridge.conversation

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RecordedConversationHistoryTest {
    @Test fun twoSessionsSurviveANewLiveConversationAndKeepDirectionAndTransmission() {
        val root = Files.createTempDirectory("conversation-history").toFile()
        try {
            write(root, "1000-first", "OUTGOING", "Pierwsza")
            write(root, "2000-second", "INCOMING", "Druga")
            val history = RecordedConversationHistory(root)
            assertEquals(2, history.list().size)
            val first = history.load("1000-first")
            assertEquals(ConversationDirection.OUTGOING, first.direction)
            assertEquals("Pierwsza", first.lines.first().text)
            assertEquals("TX: wysłano lokalnie", first.lines.last().txStatus)
            assertEquals(ConversationDirection.INCOMING, history.load("2000-second").direction)
        } finally { root.deleteRecursively() }
    }

    @Test fun historyRetainsEveryMessageAndReportsMalformedRecords() {
        val root = Files.createTempDirectory("conversation-history").toFile()
        try {
            write(root, "1000-test", "INCOMING", "Pierwsza")
            val timeline = File(root, "1000-test/timeline.jsonl")
            repeat(1500) { timeline.appendText("{\"type\":\"COUNTERPART_STT\",\"turn\":${it+2},\"text\":\"Dalsza wypowiedź\",\"elapsed_ms\":2}\n") }
            timeline.appendText("broken-record\n")
            val result = RecordedConversationHistory(root).load("1000-test")
            assertEquals(1502, result.lines.size)
            assertEquals(1, result.invalidRecords)
            assertNotNull(runCatching { RecordedConversationHistory(root).load("../escape") }.exceptionOrNull())
        } finally { root.deleteRecursively() }
    }

    @Test fun scriptedOpeningRetainsItsSourceInsideModelDirectSession() {
        val root=Files.createTempDirectory("incoming-history").toFile()
        try {
            val directory=File(root,"1000-incoming").apply {mkdirs()}
            File(directory,"timeline.jsonl").writeText("""
                {"type":"CONVERSATION","direction":"INCOMING","profile_id":"incoming-assistant","response_kind":"MODEL_DIRECT"}
                {"type":"AGENT_APPROVED_TEXT","turn":0,"text":"Dzień dobry.","response_kind":"SCRIPT_ASSISTED"}
                {"type":"AGENT_FULL_TX","turn":0,"transport_status":"WRITE_COMPLETE"}
                {"type":"COUNTERPART_STT","turn":1,"text":"Chcę zostawić wiadomość."}
                {"type":"AGENT_APPROVED_TEXT","turn":1,"text":"Proszę mówić.","response_kind":"MODEL_DIRECT"}
            """.trimIndent()+"\n")
            val lines=RecordedConversationHistory(root).load(directory.name).lines
            assertEquals(ConversationChatFeed.ResponseKind.SCRIPT_ASSISTED,lines[0].responseKind)
            assertEquals(ConversationChatFeed.ResponseKind.MODEL_DIRECT,lines[2].responseKind)
            assertEquals("TX: wysłano lokalnie",lines[0].txStatus)
        } finally {root.deleteRecursively()}
    }

    @Test fun deletesOnlyCompletedSessionAndItsAudioWithoutTouchingOthers() {
        val root = Files.createTempDirectory("conversation-delete").toFile()
        try {
            write(root, "1000-first", "OUTGOING", "Remove me")
            write(root, "2000-second", "INCOMING", "Preserve me")
            val first = File(root, "1000-first")
            File(first, "turn-01-rx.wav").writeBytes(byteArrayOf(1, 2, 3, 4))
            File(first, "COMPLETE").writeText("local_complete\n")
            val history = RecordedConversationHistory(root)
            assertTrue(history.canDelete("1000-first"))
            assertTrue(history.deleteCompleted("1000-first"))
            assertFalse(first.exists())
            assertEquals("Preserve me", history.load("2000-second").lines.first().text)
            assertFalse(history.deleteCompleted("2000-second"))
        } finally { root.deleteRecursively() }
    }

    @Test fun bulkClearDeletesOnlyCompletedTranscriptsAndAudio() {
        val root = Files.createTempDirectory("conversation-clear-all").toFile()
        try {
            write(root, "1000-finished", "INCOMING", "remove")
            write(root, "2000-finished", "OUTGOING", "remove")
            write(root, "3000-active", "INCOMING", "keep")
            File(root, "1000-finished/COMPLETE").writeText("local_complete\\n")
            File(root, "2000-finished/COMPLETE").writeText("local_complete\\n")
            File(root, "1000-finished/turn-01-rx.wav").writeBytes(byteArrayOf(0, 1, 2))
            val unknown = File(root, "other-private-data").apply { mkdirs() }
            File(unknown, "keep.txt").writeText("untouched")
            val archive = RecordedConversationHistory(root)
            assertEquals(2, archive.countDeletableCompleted())
            val result = archive.deleteAllCompleted()
            assertEquals(2, result.deleted)
            assertEquals(1, result.skipped)
            assertEquals(0, result.failed)
            assertEquals(0, archive.countDeletableCompleted())
            assertTrue(File(root, "3000-active/timeline.jsonl").exists())
            assertEquals("untouched", File(unknown, "keep.txt").readText())
            assertFalse(File(root, "1000-finished").exists())
            assertFalse(File(root, "2000-finished").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun bulkClearDoesNotFollowRootSymlinksOrIncompleteMarker() {
        val root = Files.createTempDirectory("conversation-bulk-safe").toFile()
        val external = Files.createTempDirectory("conversation-bulk-external").toFile()
        try {
            write(root, "1000-valid", "INCOMING", "remove")
            File(root, "1000-valid/COMPLETE").writeText("done\\n")
            write(root, "2000-unfinished", "INCOMING", "keep")
            val link = File(root, "9000-link")
            Files.createSymbolicLink(link.toPath(), external.toPath())
            val keep = File(external, "timeline.jsonl")
            keep.writeText("PRIVATE_OUTSIDE")
            File(external, "COMPLETE").writeText("done\\n")
            val result = RecordedConversationHistory(root).deleteAllCompleted()
            assertEquals(1, result.deleted)
            assertTrue(result.skipped >= 1)
            assertEquals("PRIVATE_OUTSIDE", keep.readText())
            assertTrue(link.exists())
            assertTrue(File(root, "2000-unfinished").exists())
        } finally { root.deleteRecursively(); external.deleteRecursively() }
    }

    @Test fun refusesTraversalAndSymlinkEscapes() {
        val root = Files.createTempDirectory("conversation-delete-safe").toFile()
        val other = Files.createTempDirectory("conversation-external").toFile()
        try {
            write(root, "1000-safe", "INCOMING", "Safe")
            File(root, "1000-safe/COMPLETE").writeText("local_complete\n")
            val history = RecordedConversationHistory(root)
            assertFalse(history.deleteCompleted("../" + other.name))
            val outside = File(other, "untouched.txt").apply { writeText("preserve") }
            val link = File(root, "1000-safe/outside-link")
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            assertTrue(history.deleteCompleted("1000-safe"))
            assertEquals("preserve", outside.readText())
        } finally { root.deleteRecursively(); other.deleteRecursively() }
    }

    @Test fun availableAudioPlaylistUsesRecordedStartTimesNotCompletionOrAlphabeticalOrder() {
        val root = Files.createTempDirectory("conversation-playlist").toFile()
        try {
            val dir = File(root, "1000-playlist").apply { mkdirs() }
            listOf("turn-01-tx.wav", "turn-01-rx.wav", "turn-01-tx-ack.wav").forEach {
                File(dir, it).writeBytes(byteArrayOf(0, 0))
            }
            File(dir, "timeline.jsonl").writeText(listOf(
                """{"type":"CONVERSATION","direction":"INCOMING"}""",
                """{"type":"TX_PCM","turn":1,"file":"turn-01-tx.wav","elapsed_ms":200}""",
                """{"type":"RX_PCM","turn":1,"file":"turn-01-rx.wav","elapsed_ms":10}""",
                """{"type":"TX_ACK_PCM","turn":1,"file":"turn-01-tx-ack.wav","elapsed_ms":100}""",
                """{"type":"RX_PCM","turn":1,"file":"../escape.wav","elapsed_ms":0}""",
            ).joinToString("\\n", postfix = "\\n").replace("\\\\n", "\\n"))
            val session = RecordedConversationHistory(root).load(dir.name)
            assertEquals(listOf("turn-01-rx.wav", "turn-01-tx-ack.wav", "turn-01-tx.wav"), session.audioTimeline)
            assertEquals(3, session.audioFiles.size)
        } finally { root.deleteRecursively() }
    }

    @Test fun sequenceNeverListsMissingOrSymlinkEscapedWave() {
        val root = Files.createTempDirectory("conversation-playlist-safe").toFile()
        val other = Files.createTempDirectory("conversation-playlist-elsewhere").toFile()
        try {
            val dir = File(root, "1000-safe").apply { mkdirs() }
            val outside = File(other, "audio.wav").apply { writeBytes(byteArrayOf(0, 0)) }
            Files.createSymbolicLink(File(dir, "turn-01-rx.wav").toPath(), outside.toPath())
            File(dir, "timeline.jsonl").writeText("""{"type":"RX_PCM","turn":1,"file":"turn-01-rx.wav","elapsed_ms":1}""" + "\\n")
            val history = RecordedConversationHistory(root)
            assertTrue(history.load(dir.name).audioTimeline.isEmpty())
            assertNull(history.resolveAudioFile(dir.name, "turn-01-rx.wav"))
        } finally { root.deleteRecursively(); other.deleteRecursively() }
    }

    private fun write(root: File, id: String, direction: String, text: String) {
        val dir = File(root, id).apply { mkdirs() }
        File(dir, "timeline.jsonl").writeText("""
            {"type":"START","format":1}
            {"type":"CONVERSATION","direction":"$direction","profile_id":"test","started_at_ms":1000}
            {"type":"COUNTERPART_STT","turn":1,"text":"$text","elapsed_ms":1}
            {"type":"AGENT_APPROVED_TEXT","turn":1,"text":"Odpowiedź","elapsed_ms":2}
            {"type":"AGENT_FULL_TX","turn":1,"transport_status":"WRITE_COMPLETE","elapsed_ms":3}
            {"type":"END","reason_code":"completed","elapsed_ms":4}
        """.trimIndent()+"\n")
    }
}
