package pl.michalmatu.aicallbridge.conversation

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.*

class ConversationToolHistoryTest {
    @Test fun historyShowsToolStagesSeparatelyFromSpeechAndTx() {
        val root = Files.createTempDirectory("conversation-tool-history").toFile()
        try {
            val directory = File(root, "1000-test").apply { mkdir() }
            File(directory, "timeline.jsonl").writeText("""
                {"type":"AGENT_APPROVED_TEXT","turn":1,"text":"Do widzenia.","elapsed_ms":1}
                {"type":"TOOL_EVENT","turn":1,"tool":"END_CALL","stage":"REQUESTED","result":null,"elapsed_ms":2}
                {"type":"TOOL_EVENT","turn":1,"tool":"END_CALL","stage":"DISPATCHED","result":"REQUESTED","elapsed_ms":3}
                {"type":"AGENT_FULL_TX","turn":1,"transport_status":"WRITE_COMPLETE"}
                {"type":"TOOL_EVENT","turn":1,"tool":"END_CALL","stage":"CONFIRMED","result":null,"elapsed_ms":4}
                {"type":"END","elapsed_ms":5}
            """.trimIndent())
            val history = RecordedConversationHistory(root).load(directory.name)
            assertEquals(4, history.lines.size)
            assertEquals(3, history.lines.count { it.speaker == ConversationChatFeed.Speaker.TOOL })
            assertEquals("TX: wysłano lokalnie", history.lines.first().txStatus)
            assertTrue(history.lines.last().text.contains("potwierdził"))
            assertTrue(history.lines.drop(1).all { it.txStatus == null })
        } finally { root.deleteRecursively() }
    }
    @Test fun delayedAndroidConfirmationCanFinishSameChatButNeverAttachToNewGeneration() {
        val old = ConversationChatFeed.begin()
        val event = ConversationToolEvent(1, ConversationToolId.END_CALL, ConversationToolStage.CONFIRMED, null)
        ConversationChatFeed.finish(old, "conversation_completed")
        ConversationChatFeed.tool(old, event)
        assertEquals(ConversationChatFeed.Speaker.TOOL, ConversationChatFeed.snapshot().lines.single().speaker)
        ConversationChatFeed.begin()
        ConversationChatFeed.tool(old, event)
        assertTrue(ConversationChatFeed.snapshot().lines.isEmpty())
    }
}
