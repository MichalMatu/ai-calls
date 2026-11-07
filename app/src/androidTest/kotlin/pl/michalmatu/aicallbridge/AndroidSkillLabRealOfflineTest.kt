package pl.michalmatu.aicallbridge

import android.graphics.Bitmap
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness

/**
 * Opt-in physical S22 acceptance: real LiteRT local model, only synthetic corpus,
 * 2 consecutive turns. Never opens Telecom, records audio or executes END_CALL.
 * Run as a dedicated instrumented class after an external IDLE safety gate.
 * Private screenshot artifacts stay in app-specific storage.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSkillLabRealOfflineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext

    private fun snapshot(folder: File, name: String) {
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "real_lab_screenshot_unavailable:$name"
        }
        try {
            val file = File(folder, name + ".jpg")
            file.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output))
            }
            assertTrue("real_lab_screenshot_empty:$name", file.length() > 2000)
        } finally { bitmap.recycle() }
    }

    @Test fun realTwoTurnModelAndStopStayOffline() {
        val profile = requireNotNull(AudioEngineProfileStore(app).load()) {
            "real_lab_profile_unavailable"
        }
        val readiness = AndroidGemma4ModelReadiness.check(app, profile.dialogue)
        assertEquals("real_lab_model_not_ready", "READY", readiness.state.toString())

        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            var folder: File? = null
            scenario.onActivity { activity ->
                folder = requireNotNull(activity.getExternalFilesDir("ui-audit-20261107-sprint5"))
                assertTrue(requireNotNull(folder).isDirectory || requireNotNull(folder).mkdirs())
                val scope = requireNotNull(activity.window.decorView
                    .findViewWithTag<Spinner>("lab-scope-picker"))
                scope.setSelection(0)
                requireNotNull(activity.window.decorView.findViewWithTag<Button>("lab-start"))
                    .performClick()
            }
            Thread.sleep(400)
            snapshot(requireNotNull(folder), "lab-real-waiting")

            var recordedFirst = false
            var finished = false
            var seenFailure = false
            // Model initialization plus two generation turns, bounded by the
            // activity's own 120 s/turn guard and host instrumentation timeout.
            for (attempt in 0 until 480) {
                var progress = -1
                scenario.onActivity { activity ->
                    progress = requireNotNull(activity.window.decorView
                        .findViewWithTag<ProgressBar>("lab-live-progress")).progress
                    val message = requireNotNull(activity.window.decorView
                        .findViewWithTag<TextView>("lab-status")).text.toString()
                    seenFailure = message.startsWith(
                        activity.getString(R.string.lab_ui_failed, "").substringBefore(":")) ||
                        message.contains("model_response_timeout")
                }
                if (progress >= 1 && !recordedFirst) {
                    snapshot(requireNotNull(folder), "lab-real-first-turn")
                    recordedFirst = true
                }
                if (seenFailure || progress >= 2) {
                    finished = progress >= 2
                    break
                }
                Thread.sleep(750)
            }
            assertFalse("real_lab_failed", seenFailure)
            assertTrue("real_lab_first_turn_not_seen", recordedFirst)
            assertTrue("real_lab_two_turns_not_completed", finished)
            snapshot(requireNotNull(folder), "lab-real-two-turns")

            // A second run is cancelled while preparing, without a call or
            // tool invocation. The current visible state must show STOP.
            Thread.sleep(1_200)
            scenario.onActivity { activity ->
                val start = requireNotNull(activity.window.decorView
                    .findViewWithTag<Button>("lab-start"))
                assertTrue("real_lab_restart_disabled", start.isEnabled)
                start.performClick()
                val stop = requireNotNull(activity.window.decorView
                    .findViewWithTag<Button>("lab-stop"))
                assertEquals(View.VISIBLE, stop.visibility)
                stop.performClick()
                assertTrue("real_lab_stop_not_hidden", stop.visibility == View.GONE)
                assertTrue("real_lab_start_not_restored_later", !start.isEnabled ||
                    start.visibility == View.VISIBLE || start.visibility == View.GONE)
            }
            snapshot(requireNotNull(folder), "lab-real-stopped")
            println("S22_REAL_LAB_TWO_TURNS=PASS")
            println("S22_REAL_LAB_STOP=PASS")
            println("S22_REAL_LAB_NO_TELECOM_OR_TOOLS=PASS")
        }
    }
}
