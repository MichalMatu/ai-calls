package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder

/** Privacy-bounded, read-only inspection of the owner's latest post-deploy GSM call.
 * Reports evidence booleans, counts and non-sensitive weekday tokens only.
 * Never transmits or publishes note/transcript content, session IDs or contact details.
 */
@RunWith(AndroidJUnit4::class)
class AndroidRecentGsmNoteAssessmentTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun metric(key: String, value: Any) {
        instrumentation.sendStatus(2, Bundle().apply {
            putString("stream", "\n" + key + "=" + value.toString() + "\n")
        })
    }

    @Test fun inspectLatestIncomingNoteAndTranscriptWithoutDisclosingText() {
        assertFalse("private_review_requires_idle_phone",
            context.getSystemService(TelecomManager::class.java).isInCall)
        val calls = RecordedConversationHistory(
            File(context.filesDir, CallSessionEvidenceRecorder.ROOT)).list()
        val call = calls.firstOrNull { it.direction == ConversationDirection.INCOMING &&
            it.startedAtEpochMs >= 1795314841000L }
        metric("GSM_REVIEW_NEW_CALL_FOUND", call != null)
        if (call == null) return
        metric("GSM_REVIEW_CALL_COMPLETE", call.complete)
        metric("GSM_REVIEW_END_REASON", call.endReasonCode ?: "UNKNOWN")
        metric("GSM_REVIEW_INVALID_ARCHIVE_RECORDS", call.invalidRecords)
        val caller = call.lines.filter {
            it.speaker == ConversationChatFeed.Speaker.COUNTERPART
        }.sortedBy { it.turn }
        val assistant = call.lines.filter {
            it.speaker == ConversationChatFeed.Speaker.ASSISTANT
        }
        metric("GSM_REVIEW_STT_TURNS", caller.size)
        metric("GSM_REVIEW_ASSISTANT_TURNS", assistant.size)
        metric("GSM_REVIEW_TX_LOCAL_COMPLETE", assistant.count {
            it.txStatus == "TX: wysłano lokalnie"
        })
        metric("GSM_REVIEW_RX_INTERRUPTED", call.interruptedRxTurns.size)

        val timeline = File(
            File(context.filesDir, CallSessionEvidenceRecorder.ROOT), call.id + "/timeline.jsonl")
        val tools = timeline.useLines { lines -> lines.mapNotNull {
            runCatching { JSONObject(it) }.getOrNull()
                ?.takeIf { obj -> obj.optString("type") == "TOOL_EVENT" }
        }.toList() }
        fun events(tool: String, stage: String): List<JSONObject> =
            tools.filter { it.optString("tool") == tool && it.optString("stage") == stage }
        metric("GSM_REVIEW_SAVE_CONFIRMED", events("SAVE_NOTE", "CONFIRMED").size)
        metric("GSM_REVIEW_SAVE_FAILED", events("SAVE_NOTE", "FAILED").size)
        metric("GSM_REVIEW_SAVE_REJECTED", events("SAVE_NOTE", "REJECTED").size)
        metric("GSM_REVIEW_END_CALL_CONFIRMED", events("END_CALL", "CONFIRMED").size)
        val confirmedSave = events("SAVE_NOTE", "CONFIRMED").lastOrNull()
        val firstEndDispatch = events("END_CALL", "DISPATCHED").firstOrNull()
        metric("GSM_REVIEW_SAVE_BEFORE_DISCONNECT",
            confirmedSave != null && firstEndDispatch != null &&
                confirmedSave.optLong("elapsed_ms", Long.MAX_VALUE) <=
                    firstEndDispatch.optLong("elapsed_ms", Long.MIN_VALUE))

        val notebooks = AndroidConversationPreparationStore(context).notebooks()
        metric("GSM_REVIEW_ENCRYPTED_NOTEBOOK_SESSIONS", notebooks.size)
        val match = notebooks.asSequence().filter { it.direction == "INCOMING" }
            .mapNotNull { book ->
                book.entries.maxByOrNull { it.timestampMs }?.let { entry -> book to entry }
            }
            .filter { (_, last) ->
                last.timestampMs >= call.startedAtEpochMs &&
                    last.timestampMs - call.startedAtEpochMs <= 30 * 60 * 1000L
            }
            .minByOrNull { (_, last) -> abs(last.timestampMs - call.startedAtEpochMs) }
            ?.first
        metric("GSM_REVIEW_CALL_NOTE_MATCH", match != null)
        if (match == null) return
        metric("GSM_REVIEW_NOTE_ENTRY_COUNT", match.entries.size)
        val auto = match.entries.singleOrNull { it.id.endsWith(":auto") }
        metric("GSM_REVIEW_ONE_AUTO_ENTRY", auto != null)
        if (auto == null) return
        metric("GSM_REVIEW_AUTO_NOTE_CHARS", auto.text.length)
        metric("GSM_REVIEW_AUTO_NOTE_LAST_TURN", auto.turn)
        metric("GSM_REVIEW_AUTO_NOTE_KIND", auto.kind.name)
        metric("GSM_REVIEW_AUTO_NOTE_AFTER_CALL_START", auto.timestampMs >= call.startedAtEpochMs)
        val joined = caller.joinToString("\n") { it.text }
        val lastText = caller.lastOrNull()?.text.orEmpty()
        val notesText = auto.text
        val dayPatterns = listOf(
            "MON" to Regex("(?iu)poniedział\\S*|monday"),
            "TUE" to Regex("(?iu)wtor\\S*|tuesday"),
            "WED" to Regex("(?iu)środ\\S*|środę|wednesday"),
            "THU" to Regex("(?iu)czwart\\S*|thursday"),
            "FRI" to Regex("(?iu)piąt\\S*|piątek|friday"),
            "SAT" to Regex("(?iu)sobot\\S*|saturday"),
            "SUN" to Regex("(?iu)niedziel\\S*|sunday"),
        )
        fun days(text: String) = dayPatterns.filter { (_, pattern) ->
            pattern.containsMatchIn(text)
        }.map { it.first }.joinToString("_").ifBlank { "NONE" }
        val corrections = Regex("(?iu)poprawk\\S*|zamiast|jednak|korekt\\S*|actually|instead|corrected|rather")
        metric("GSM_REVIEW_STT_DAYS", days(joined))
        metric("GSM_REVIEW_NOTE_DAYS", days(notesText))
        metric("GSM_REVIEW_STT_CORRECTION", corrections.containsMatchIn(joined))
        metric("GSM_REVIEW_NOTE_CORRECTION", corrections.containsMatchIn(notesText))
        metric("GSM_REVIEW_LAST_STT_INCLUDED_VERBATIM", lastText.isNotBlank() &&
            notesText.contains(lastText))
        metric("GSM_REVIEW_AUTO_NOTE_SAFE",
            ConversationAutoNoteCollector.safe(notesText))
        metric("GSM_REVIEW_TOOL_AND_NOTE_MATCH",
            events("SAVE_NOTE", "CONFIRMED").isNotEmpty() &&
                match.entries.any { it.timestampMs >= call.startedAtEpochMs })
        assertFalse("private_review_phone_changed", context.getSystemService(TelecomManager::class.java).isInCall)
    }
}
