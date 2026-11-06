package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.conversationtools.*
import pl.michalmatu.aicallbridge.tools.ConversationToolUi

import com.google.gson.JsonParser
import java.io.File

internal data class RecordedConversation(
    val id: String,
    val direction: ConversationDirection,
    val profileId: String?,
    val startedAtEpochMs: Long,
    val complete: Boolean,
    val lines: List<ConversationChatFeed.Line>,
    val invalidRecords: Int,
)

/** Read model over existing private timeline files. No duplicate transcript store or retention quota. */
internal class RecordedConversationHistory(private val root: File) {
    fun list(): List<RecordedConversation> = root.listFiles().orEmpty().filter {
        it.isDirectory && File(it, "timeline.jsonl").isFile
    }.sortedByDescending { it.name }.map { load(it.name) }

    fun load(id: String): RecordedConversation {
        val directory = File(root, id).canonicalFile
        require(directory.parentFile == root.canonicalFile && directory.isDirectory) { "invalid_conversation_history_id" }
        val lines = mutableListOf<ConversationChatFeed.Line>()
        var direction = ConversationDirection.UNSPECIFIED
        var profile: String? = null
        var start = id.substringBefore('-').toLongOrNull() ?: 0L
        var complete = false
        var invalid = 0
        var kind = ConversationChatFeed.ResponseKind.UNSPECIFIED
        File(directory, "timeline.jsonl").useLines { events -> events.forEach { raw ->
            if (raw.isNotBlank()) try {
                val event = JsonParser.parseString(raw).asJsonObject
                val type = event.get("type")?.asString
                when (type) {
                    "CONVERSATION" -> {
                        direction = ConversationDirection.valueOf(event["direction"].asString)
                        profile = event.get("profile_id")?.asString
                        event.get("started_at_ms")?.let { start = it.asLong }
                        event.get("response_kind")?.let { kind = ConversationChatFeed.ResponseKind.valueOf(it.asString) }
                    }
                    "COUNTERPART_STT", "AGENT_APPROVED_TEXT" -> lines.add(ConversationChatFeed.Line(
                        turn = event["turn"].asInt,
                        speaker = if (type == "COUNTERPART_STT") ConversationChatFeed.Speaker.COUNTERPART else ConversationChatFeed.Speaker.ASSISTANT,
                        text = event["text"].asString,
                        atEpochMs = start + (event.get("elapsed_ms")?.asLong ?: 0L),
                        responseKind = if (type == "AGENT_APPROVED_TEXT") event.get("response_kind")?.asString?.let(ConversationChatFeed.ResponseKind::valueOf) ?: kind else ConversationChatFeed.ResponseKind.UNSPECIFIED,
                    ))
                    "TOOL_EVENT" -> {
                        val tool = ConversationToolEvent(event["turn"].asInt,
                            ConversationToolId.valueOf(event["tool"].asString),
                            ConversationToolStage.valueOf(event["stage"].asString),
                            event.get("result")?.takeUnless { it.isJsonNull }?.asString?.let(ConversationToolResult::valueOf))
                        lines.add(ConversationChatFeed.Line(tool.turn, ConversationChatFeed.Speaker.TOOL,
                            ConversationToolUi.text(tool), atEpochMs = start + event["elapsed_ms"].asLong))
                    }
                    "AGENT_FULL_TX" -> {
                        val index = lines.indexOfLast { it.turn == event["turn"].asInt && it.speaker == ConversationChatFeed.Speaker.ASSISTANT }
                        if (index >= 0) lines[index] = lines[index].copy(txStatus =
                            if (event["transport_status"].asString == "WRITE_COMPLETE") "TX: wysłano lokalnie" else "TX: niepełne")
                    }
                    "END" -> complete = true
                }
            } catch (_: RuntimeException) { invalid++ }
        } }
        return RecordedConversation(id, direction, profile, start, complete, lines.toList(), invalid)
    }
}
