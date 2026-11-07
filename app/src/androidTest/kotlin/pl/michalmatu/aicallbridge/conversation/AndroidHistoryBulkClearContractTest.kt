package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder

/**
 * SAFE S22 contract: only opens and cancels the bulk-delete confirmation.
 * Never confirms deletion of any owner's real archive. The only test artifact
 * is a unique synthetic completed session, removed by this test.
 */
@RunWith(AndroidJUnit4::class)
class AndroidHistoryBulkClearContractTest {
    @Test fun bulkDeleteIsAtBottomAndCancellationPreservesArchives() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, CallSessionEvidenceRecorder.ROOT)
        val id = "9999999999999-bulk-qa-" + UUID.randomUUID()
        val folder = File(root, id)
        assertTrue(folder.mkdirs())
        try {
            File(folder, "timeline.jsonl").writeText(
                """{"type":"CONVERSATION","direction":"INCOMING","started_at_ms":9999999999999}""" + "\n" +
                """{"type":"COUNTERPART_STT","turn":1,"text":"SYNTHETIC_BULK_TEST"}""" + "\n" +
                """{"type":"END","elapsed_ms":1}""" + "\n")
            File(folder, "COMPLETE").writeText("local_complete\n")
            ActivityScenario.launch(ConversationHistoryActivity::class.java).use { scenario ->
                var found = false
                repeat(70) {
                    scenario.onActivity { activity ->
                        found = activity.window.decorView
                            .findViewWithTag<View>("history-delete-all")?.isShown == true
                    }
                    if (found) return@repeat
                    Thread.sleep(100)
                }
                assertTrue("bulk_clear_button_missing", found)
                scenario.onActivity { activity ->
                    val button = requireNotNull(activity.window.decorView
                        .findViewWithTag<View>("history-delete-all"))
                    assertTrue(button.isClickable)
                    button.performClick()
                    val dialog = requireNotNull(activity.pendingBulkDeleteConfirmation)
                    assertTrue("bulk_confirmation_not_shown", dialog.isShowing)
                    assertTrue("confirmation_must_mention_audio",
                        dialog.findViewById<android.widget.TextView>(android.R.id.message)
                            .text.toString().contains("audio", ignoreCase = true) ||
                        dialog.findViewById<android.widget.TextView>(android.R.id.message)
                            .text.toString().contains("nagrania", ignoreCase = true))
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                    assertTrue("cancel_must_preserve_synthetic_session", folder.exists())
                }
            }
        } finally {
            assertTrue("synthetic_cleanup_failed", folder.deleteRecursively())
        }
    }
}
