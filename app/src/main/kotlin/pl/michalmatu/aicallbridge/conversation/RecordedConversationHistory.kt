package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.conversationtools.*
import pl.michalmatu.aicallbridge.tools.ConversationToolUi
import pl.michalmatu.aicallbridge.developerrelay.RecordedAudioClipName

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
    /** Available clips in recorder timestamp order, not a continuous full-call recording. */
    val audioTimeline: List<String> = emptyList(),
    val endReasonCode: String? = null,
    val interruptedRxTurns: Map<Int, String> = emptyMap(),
) {
    fun audioFor(line: ConversationChatFeed.Line): String? = when (line.speaker) {
        ConversationChatFeed.Speaker.COUNTERPART -> audioFiles[line.turn to "rx"]
        ConversationChatFeed.Speaker.ASSISTANT -> audioFiles[line.turn to "tx"]
        ConversationChatFeed.Speaker.TOOL -> null
    }
}

/** Read model over existing private timeline files. No duplicate transcript store or retention quota. */
internal class RecordedConversationHistory(private val root: File) {
    private fun privateTimeline(directory: File): File? = runCatching {
        val candidate = File(directory, "timeline.jsonl")
        if (java.nio.file.Files.isSymbolicLink(candidate.toPath())) return@runCatching null
        candidate.canonicalFile.takeIf {
            it.parentFile == directory.canonicalFile && it.isFile
        }
    }.getOrNull()

    fun list(): List<RecordedConversation> = root.listFiles().orEmpty()
        .asSequence()
        .filter {
            it.isDirectory && !java.nio.file.Files.isSymbolicLink(it.toPath()) &&
                privateTimeline(it) != null
        }
        .sortedByDescending { it.name }
        .mapNotNull { runCatching { load(it.name) }.getOrNull() }
        .toList()

    /** Remove only a completed private archive, never a session still being recorded. */
    fun canDelete(id: String): Boolean = runCatching {
        val base = root.canonicalFile
        val candidate = File(root, id)
        val directory = candidate.canonicalFile
        !java.nio.file.Files.isSymbolicLink(candidate.toPath()) &&
            directory.parentFile == base && directory.isDirectory &&
            File(directory, "COMPLETE").isFile &&
            !java.nio.file.Files.isSymbolicLink(File(directory, "COMPLETE").toPath()) &&
            privateTimeline(directory) != null
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

    /**
     * Reclaim only previously completed private archives, oldest first, when storage
     * pressure is real. Never touch the active recorder, external symlinks, partial
     * sessions or unknown folders. The caller decides when enough space is available.
     */
    fun reclaimCompletedForSpace(activeSessionId: String, enoughSpace: () -> Boolean): Int {
        var deleted = 0
        for (candidate in root.listFiles().orEmpty().sortedBy { it.name }) {
            if (enoughSpace()) break
            if (!candidate.isDirectory || candidate.name == activeSessionId) continue
            if (runCatching { deleteCompleted(candidate.name) }.getOrDefault(false)) deleted++
        }
        return deleted
    }

    data class ClearCompletedResult(
        val deleted: Int,
        val skipped: Int,
        val failed: Int,
    )

    /** UI may clear only verified COMPLETE archives, never an in-flight recorder. */
    fun countDeletableCompleted(): Int = root.listFiles().orEmpty().count { candidate ->
        candidate.isDirectory && canDelete(candidate.name) &&
            runCatching { load(candidate.name).complete }.getOrDefault(false)
    }

    /**
     * Delete only existing, verified completed archives. Do not recursively clear
     * the root, unknown directories or sessions still being recorded.
     * Each session passes the same symlink/path/COMPLETE checks as single delete.
     */
    fun deleteAllCompleted(): ClearCompletedResult {
        var deleted = 0
        var skipped = 0
        var failed = 0
        root.listFiles().orEmpty().forEach { candidate ->
            if (!candidate.isDirectory ||
                !File(candidate, "timeline.jsonl").isFile) return@forEach
            if (!canDelete(candidate.name) ||
                !runCatching { load(candidate.name).complete }.getOrDefault(false)) {
                skipped++
                return@forEach
            }
            if (runCatching { deleteCompleted(candidate.name) }.getOrDefault(false)) {
                deleted++
            } else {
                failed++
            }
        }
        return ClearCompletedResult(deleted, skipped, failed)
    }

    /** Resolve only preexisting, private per-turn WAVs, never external paths. */
    fun resolveAudioFile(id: String, fileName: String): File? = runCatching {
        if (!RecordedAudioClipName.isSafe(fileName)) return@runCatching null
        val selected = File(root, id)
        if (java.nio.file.Files.isSymbolicLink(selected.toPath())) return@runCatching null
        val directory = selected.canonicalFile
        if (directory.parentFile != root.canonicalFile || !directory.isDirectory) return@runCatching null
        val candidate = File(directory, fileName)
        if (java.nio.file.Files.isSymbolicLink(candidate.toPath())) return@runCatching null
        candidate.canonicalFile.takeIf { it.parentFile == directory && it.isFile }
    }.getOrNull()

    private fun isSafeArchiveCode(value: String): Boolean =
        value.length in 1..80 && value.all { it.isLetterOrDigit() && it.code < 128 || it in "_.-" }

    fun load(id: String): RecordedConversation {
        val selected = File(root, id)
        require(!java.nio.file.Files.isSymbolicLink(selected.toPath())) { "invalid_conversation_history_id" }
        val directory = selected.canonicalFile
        require(directory.parentFile == root.canonicalFile && directory.isDirectory) { "invalid_conversation_history_id" }
        val timeline = requireNotNull(privateTimeline(directory)) { "invalid_private_conversation_timeline" }
        val lines = mutableListOf<ConversationChatFeed.Line>()
        val audioFiles = linkedMapOf<Pair<Int, String>, String>()
        val audioTimeline = mutableListOf<Triple<Long, Int, String>>()
        val interruptedRxTurns = linkedMapOf<Int, String>()
        var endReasonCode: String? = null
        var direction = ConversationDirection.UNSPECIFIED
        var profile: String? = null
        var start = id.substringBefore('-').toLongOrNull() ?: 0L
        var complete = false
        var invalid = 0
        var kind = ConversationChatFeed.ResponseKind.UNSPECIFIED
        timeline.useLines { events -> events.forEach { raw ->
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
                        if (turn >= 0 && name != null && RecordedAudioClipName.matches(name, turn, channel)) {
                            val candidate = File(directory, name)
                            val wave = candidate.canonicalFile
                            if (!java.nio.file.Files.isSymbolicLink(candidate.toPath()) &&
                                wave.parentFile == directory && wave.isFile) {
                                // The transcript links to the final RX window, but playback keeps all.
                                audioFiles[turn to channel] = name
                                val startedAt = event.get("elapsed_ms")?.asLong
                                    ?: audioTimeline.lastOrNull()?.first ?: 0L
                                audioTimeline += Triple(startedAt, audioTimeline.size, name)
                            }
                        }
                    }
                    "AGENT_FULL_TX" -> {
                        val index = lines.indexOfLast { it.turn == event["turn"].asInt && it.speaker == ConversationChatFeed.Speaker.ASSISTANT }
                        if (index >= 0) lines[index] = lines[index].copy(txStatus =
                            when (event["transport_status"].asString) {
                                "WRITE_COMPLETE" -> "TX: wysłano lokalnie"
                                "WRITE_REVOKED" -> "TX: zgoda cofnięta"
                                "WRITE_UNCERTAIN" -> "TX: wynik niepewny"
                                else -> "TX: niepełne"
                            })
                    }
                    "RX_INTERRUPTED" -> {
                        val turn = event["turn"].asInt
                        val code = event.get("reason_code")?.asString?.takeIf(::isSafeArchiveCode)
                        if (turn >= 0 && code != null) interruptedRxTurns[turn] = code
                    }
                    "END" -> {
                        complete = true
                        endReasonCode = event.get("reason_code")?.asString?.takeIf(::isSafeArchiveCode)
                    }
                }
            } catch (_: RuntimeException) { invalid++ }
        } }
        val playback = audioTimeline.sortedWith(
            compareBy<Triple<Long, Int, String>> { it.first }.thenBy { it.second },
        ).map { it.third }.distinct()
        return RecordedConversation(id, direction, profile, start, complete, lines.toList(), invalid,
            audioFiles.toMap(), playback, endReasonCode, interruptedRxTurns.toMap())
    }
}
