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
