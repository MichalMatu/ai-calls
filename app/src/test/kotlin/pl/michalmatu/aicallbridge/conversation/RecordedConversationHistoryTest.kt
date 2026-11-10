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

    @Test fun pressureReclamationStopsWhenSpaceIsEnoughAndPreservesActiveRecorder() {
        val root = Files.createTempDirectory("conversation-reclaim-pressure").toFile()
        try {
            write(root, "1000-oldest", "OUTGOING", "remove-first")
            write(root, "2000-next", "INCOMING", "keep")
            write(root, "3000-active", "INCOMING", "actively-recording")
            File(root, "1000-oldest/COMPLETE").writeText("done")
            File(root, "2000-next/COMPLETE").writeText("done")
            // Even a stray marker on the active session must not authorize eviction.
            File(root, "3000-active/COMPLETE").writeText("partial")
            val history = RecordedConversationHistory(root)
            val deleted = history.reclaimCompletedForSpace("3000-active") {
                !File(root, "1000-oldest").exists()
            }
            assertEquals(1, deleted)
            assertFalse(File(root, "1000-oldest").exists())
            assertTrue(File(root, "2000-next").exists())
            assertTrue(File(root, "3000-active").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun pressureReclamationRejectsUnknownMarkersUnfinishedAndExternalLinks() {
        val root = Files.createTempDirectory("conversation-reclaim-safe").toFile()
        val external = Files.createTempDirectory("conversation-reclaim-outside").toFile()
        try {
            write(root, "1000-valid", "INCOMING", "remove")
            File(root, "1000-valid/COMPLETE").writeText("done")
            write(root, "2000-unfinished", "INCOMING", "preserve")
            File(root, "2000-unfinished/COMPLETE").writeText("stray")
            File(root, "2000-unfinished/timeline.jsonl").writeText(
                "not-json\n",
            )
            val unknown = File(root, "3000-unknown").apply { mkdirs() }
            File(unknown, "COMPLETE").writeText("stray")
            val linkedDir = File(root, "4000-outside-link")
            Files.createSymbolicLink(linkedDir.toPath(), external.toPath())
            val externalData = File(external, "keep.txt").apply { writeText("outside") }
            File(external, "COMPLETE").writeText("stray")
            val poisoned = File(root, "5000-poisoned").apply { mkdirs() }
            Files.createSymbolicLink(
                File(poisoned, "timeline.jsonl").toPath(),
                File(unknown, "nonexistent.jsonl").toPath(),
            )
            File(poisoned, "COMPLETE").writeText("stray")

            val deleted = RecordedConversationHistory(root)
                .reclaimCompletedForSpace("6000-active") { false }
            assertEquals(1, deleted)
            assertFalse(File(root, "1000-valid").exists())
            assertTrue(File(root, "2000-unfinished").exists())
            assertTrue(unknown.exists())
            assertTrue(linkedDir.exists())
            assertTrue(poisoned.exists())
            assertEquals("outside", externalData.readText())
        } finally { root.deleteRecursively(); external.deleteRecursively() }
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

    @Test fun historyListSkipsSymlinkedExternalTimelineWithoutReadingItsText() {
        val root = Files.createTempDirectory("conversation-history-private-list").toFile()
        val outside = Files.createTempDirectory("conversation-history-external").toFile()
        try {
            write(root, "1000-valid", "INCOMING", "visible-local")
            val external = File(outside, "outside-timeline.jsonl")
            external.writeText("""{"type":"COUNTERPART_STT","turn":1,"text":"DO_NOT_SHOW_EXTERNAL"}""" + "\n")
            val poisoned = File(root, "2000-poisoned").apply { mkdirs() }
            Files.createSymbolicLink(File(poisoned, "timeline.jsonl").toPath(), external.toPath())
            File(poisoned, "COMPLETE").writeText("untrusted-marker")
            val escaped = File(root, "3000-external-dir")
            Files.createSymbolicLink(escaped.toPath(), outside.toPath())

            val history = RecordedConversationHistory(root)
            val listing = history.list()
            assertEquals(listOf("1000-valid"), listing.map { it.id })
            assertTrue(listing.flatMap { it.lines }.none { it.text.contains("DO_NOT_SHOW_EXTERNAL") })
            assertTrue(runCatching { history.load("2000-poisoned") }.isFailure)
            assertFalse(history.canDelete("2000-poisoned"))
            assertFalse(history.deleteCompleted("2000-poisoned"))
            assertEquals(
                """{"type":"COUNTERPART_STT","turn":1,"text":"DO_NOT_SHOW_EXTERNAL"}""" + "\n",
                external.readText(),
            )
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }

    @Test fun symlinkCompleteMarkerNeverAuthorizesDeletion() {
        val root = Files.createTempDirectory("conversation-history-private-complete").toFile()
        val outside = Files.createTempDirectory("conversation-history-external-marker").toFile()
        try {
            write(root, "1000-valid", "INCOMING", "keep-this")
            val completed = File(outside, "COMPLETE").apply { writeText("external-only") }
            Files.createSymbolicLink(File(root, "1000-valid/COMPLETE").toPath(), completed.toPath())
            val history = RecordedConversationHistory(root)
            assertFalse(history.canDelete("1000-valid"))
            assertFalse(history.deleteCompleted("1000-valid"))
            assertEquals("keep-this", history.load("1000-valid").lines.first().text)
            assertEquals("external-only", completed.readText())
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
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
            ).joinToString("\n", postfix = "\n"))
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
            File(dir, "timeline.jsonl").writeText("""{"type":"RX_PCM","turn":1,"file":"turn-01-rx.wav","elapsed_ms":1}""" + "\n")
            val history = RecordedConversationHistory(root)
            assertTrue(history.load(dir.name).audioTimeline.isEmpty())
            assertNull(history.resolveAudioFile(dir.name, "turn-01-rx.wav"))
        } finally { root.deleteRecursively(); other.deleteRecursively() }
    }

    @Test fun completionAndInterruptedRxCodesComeOnlyFromSafeRecordedTimelineFields() {
        val root = Files.createTempDirectory("conversation-outcome").toFile()
        try {
            val dir = File(root, "1000-result").apply { mkdirs() }
            File(dir, "timeline.jsonl").writeText(listOf(
                """{"type":"RX_INTERRUPTED","turn":1,"reason_code":"human_takeover"}""",
                """{"type":"RX_INTERRUPTED","turn":2,"reason_code":"rejected secret: 123"}""",
                """{"type":"END","reason_code":"human_takeover"}""",
            ).joinToString("\n", postfix = "\n"))
            val session = RecordedConversationHistory(root).load(dir.name)
            assertEquals("human_takeover", session.endReasonCode)
            assertEquals(mapOf(1 to "human_takeover"), session.interruptedRxTurns)
            assertTrue(session.complete)
        } finally { root.deleteRecursively() }
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
