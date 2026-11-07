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
    val audioFiles: Map<Pair<Int, String>, String> = emptyMap(),
) {
    fun audioFor(line: ConversationChatFeed.Line): String? = when (line.speaker) {
        ConversationChatFeed.Speaker.COUNTERPART -> audioFiles[line.turn to "rx"]
        ConversationChatFeed.Speaker.ASSISTANT -> audioFiles[line.turn to "tx"]
        ConversationChatFeed.Speaker.TOOL -> null
    }
}

/** Read model over existing private timeline files. No duplicate transcript store or retention quota. */
internal class RecordedConversationHistory(private val root: File) {
    fun list(): List<RecordedConversation> = root.listFiles().orEmpty().filter {
        it.isDirectory && File(it, "timeline.jsonl").isFile
    }.sortedByDescending { it.name }.map { load(it.name) }

    /** Remove only a completed private archive, never a session still being recorded. */
    fun canDelete(id: String): Boolean = runCatching {
        val base = root.canonicalFile
        val candidate = File(root, id)
        val directory = candidate.canonicalFile
        !java.nio.file.Files.isSymbolicLink(candidate.toPath()) &&
            directory.parentFile == base && directory.isDirectory &&
            File(directory, "COMPLETE").isFile &&
            File(directory, "timeline.jsonl").isFile
    }.getOrDefault(false)

    fun deleteCompleted(id: String): Boolean {
        if (!canDelete(id)) return false
        val base = root.canonicalFile
        val directory = File(root, id).canonicalFile
        if (directory.parentFile != base || !load(id).complete) return false
        // walkFileTree does not follow symlinks; deleting a link cannot delete its target.
        java.nio.file.Files.walkFileTree(directory.toPath(), object :
            java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes):
                java.nio.file.FileVisitResult {
                java.nio.file.Files.delete(file)
                return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: java.nio.file.Path, error: java.io.IOException?):
                java.nio.file.FileVisitResult {
                if (error != null) throw error
                java.nio.file.Files.delete(dir)
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
        return !directory.exists()
    }

    /** Resolve only preexisting, private per-turn WAVs, never external paths. */
    fun resolveAudioFile(id: String, fileName: String): File? = runCatching {
        if (!fileName.matches(Regex("turn-[0-9]+-(rx|tx|tx-ack)[.]wav"))) return@runCatching null
        val directory = File(root, id).canonicalFile
        if (directory.parentFile != root.canonicalFile || !directory.isDirectory) return@runCatching null
        File(directory, fileName).canonicalFile.takeIf { it.parentFile == directory && it.isFile }
    }.getOrNull()

    fun load(id: String): RecordedConversation {
        val directory = File(root, id).canonicalFile
        require(directory.parentFile == root.canonicalFile && directory.isDirectory) { "invalid_conversation_history_id" }
        val lines = mutableListOf<ConversationChatFeed.Line>()
        val audioFiles = linkedMapOf<Pair<Int, String>, String>()
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
                    "RX_PCM", "TX_PCM", "TX_ACK_PCM" -> {
                        val turn = event["turn"].asInt
                        val channel = when (type) {
                            "RX_PCM" -> "rx"
                            "TX_PCM" -> "tx"
                            else -> "tx-ack"
                        }
                        val name = event.get("file")?.takeUnless { it.isJsonNull }?.asString
                        val expected = "turn-" + turn.toString().padStart(2, '0') + "-" + channel + ".wav"
                        if (turn >= 0 && name == expected) {
                            val wave = File(directory, expected).canonicalFile
                            if (wave.parentFile == directory && wave.isFile) audioFiles[turn to channel] = expected
                        }
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
        return RecordedConversation(id, direction, profile, start, complete, lines.toList(), invalid, audioFiles.toMap())
    }
}
