package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder

/** No live calls, private-session reads or real deletions: unique synthetic archive only. */
@RunWith(AndroidJUnit4::class)
class AndroidHistoryNavigationDeletionTest {
    private fun waitFor(scenario: ActivityScenario<ConversationHistoryActivity>,
                        tag: String, attempts: Int = 70): Boolean {
        repeat(attempts) {
            var found = false
            scenario.onActivity { activity ->
                found = activity.window.decorView.findViewWithTag<View>(tag)?.isShown == true
            }
            if (found) return true
            Thread.sleep(100)
        }
        return false
    }

    @Test fun detailBackReturnsToCallsAndDeletesOnlySyntheticArchive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, CallSessionEvidenceRecorder.ROOT)
        val id = "9999999999999-qa-" + UUID.randomUUID().toString()
        val directory = File(root, id)
        assertTrue(directory.mkdirs())
        try {
            File(directory, "timeline.jsonl").writeText(
                """{"type":"CONVERSATION","direction":"INCOMING","started_at_ms":9999999999999}""" + "\n" +
                """{"type":"COUNTERPART_STT","turn":1,"text":"SYNTHETIC_ONLY","elapsed_ms":1}""" + "\n" +
                """{"type":"END","elapsed_ms":2}""" + "\n")
            File(directory, "COMPLETE").writeText("local_complete\n")
            ActivityScenario.launch(ConversationHistoryActivity::class.java).use { scenario ->
                assertTrue("synthetic_session_missing", waitFor(scenario, "history-open:" + id))
                scenario.onActivity { activity ->
                    activity.window.decorView.findViewWithTag<Button>("history-open:" + id).performClick()
                }
                assertTrue("synthetic_details_missing", waitFor(scenario, "history-back-to-list"))
                scenario.onActivity { activity ->
                    val back = activity.window.decorView.findViewWithTag<View>("app-child-back")
                    assertNotNull(back)
                    back.performClick()
                    assertFalse("back_left_calls", activity.isFinishing)
                }
                assertTrue("back_did_not_restore_calls", waitFor(scenario, "history-open:" + id))
                scenario.onActivity { activity ->
                    activity.window.decorView.findViewWithTag<Button>("history-open:" + id).performClick()
                }
                assertTrue("details_did_not_reopen", waitFor(scenario, "history-back-to-list"))
                scenario.onActivity { activity ->
                    // Exercise the actual AndroidX system dispatcher, not only chrome Back.
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertFalse("system_back_left_calls", activity.isFinishing)
                }
                assertTrue("system_back_did_not_restore_calls", waitFor(scenario, "history-open:" + id))
                scenario.onActivity { activity ->
                    activity.window.decorView.findViewWithTag<Button>("history-delete:" + id).performClick()
                    val dialog = requireNotNull(activity.pendingDeleteConfirmation)
                    assertTrue(dialog.isShowing)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                    assertTrue("cancel_must_not_delete", directory.exists())
                    activity.window.decorView.findViewWithTag<Button>("history-delete:" + id).performClick()
                    val confirm = requireNotNull(activity.pendingDeleteConfirmation)
                    assertTrue(confirm.isShowing)
                    confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                }
                repeat(60) {
                    if (!directory.exists()) return@repeat
                    Thread.sleep(100)
                }
                assertFalse("synthetic_history_not_deleted", directory.exists())
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
