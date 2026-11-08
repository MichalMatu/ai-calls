package pl.michalmatu.aicallbridge

import android.graphics.Bitmap
import android.graphics.Rect
import pl.michalmatu.aicallbridge.conversation.SkillLabScoring
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity

/**
 * On-device screenshot of purely synthetic, presentation-only test progress.
 * Does NOT load a model, start the lab worker, dispatch END_CALL or touch Telecom.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSkillLabLivePreviewTest {
    @Test fun finishedSyntheticTurnIsParsedIntoReadableLiveCard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue("android_safe_question_heuristic",
            SkillLabScoring.mayNeedQuestionReview("Proszę podać numer telefonu."))
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            var folder: File? = null
            scenario.onActivity { activity ->
                val clazz = activity.javaClass
                val pending = clazz.declaredMethods.first { it.name == "showTurnPending" }
                    .apply { isAccessible = true }
                val complete = clazz.declaredMethods.first { it.name == "showTurnCompleted" }
                    .apply { isAccessible = true }
                val recordType = complete.parameterTypes[1]
                val record = recordType.declaredConstructors
                    .first { it.parameterTypes.size == 7 }
                    .apply { isAccessible = true }
                    .newInstance("Scenariusz testowy", 1, false, false,
                        "Dziękuję. Zapisałem przykładową wiadomość.", 239L, false)
                pending.invoke(activity, 0, "Scenariusz testowy", 1,
                    "Proszę zapisać przykładową wiadomość.", 0)
                complete.invoke(activity, 0, record, 1)
                assertEquals(1, requireNotNull(activity.window.decorView.findViewWithTag<ProgressBar>(
                    "lab-live-progress")).progress)
                val timeline = requireNotNull(activity.window.decorView.findViewWithTag<View>(
                    "lab-turn-timeline"))
                assertTrue(timeline.isShown)
                val reply = requireNotNull(activity.window.decorView.findViewWithTag<TextView>(
                    "lab-reply:0:1"))
                assertTrue(reply.text.contains("przykładową wiadomość"))
                assertFalse(reply.text.contains("TOOL_REPLY"))
                assertTrue(requireNotNull(activity.window.decorView.findViewWithTag<TextView>(
                    "lab-decision:0:1")).text.contains("END_CALL"))
                assertNull(activity.window.decorView.findViewWithTag<CheckBox>(
                    "lab-follow")) // Auto-follow is implicit unless the owner scrolls up.
                folder = requireNotNull(activity.getExternalFilesDir("ui-audit-20261107-sprint6"))
                assertTrue(requireNotNull(folder).isDirectory || requireNotNull(folder).mkdirs())
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(350)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull("lab_live_synthetic_capture_missing", screenshot)
            try {
                val file = File(requireNotNull(folder), "lab-live-synthetic.jpg")
                file.outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.JPEG, 82, it))
                }
                assertTrue(file.length() > 2000)
                println("S22_LAB_SYNTHETIC_LIVE_PREVIEW=PASS")
            } finally {
                screenshot.recycle()
            }
        }
    }

    @Test fun modelTimeoutAppearsAsReadableErrorWithoutRunningModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            var folder: File? = null
            scenario.onActivity { activity ->
                val failure = activity.javaClass.declaredMethods.first {
                    it.name == "renderFailure"
                }.apply { isAccessible = true }
                failure.invoke(activity, "model_response_timeout")
                val status = requireNotNull(activity.window.decorView
                    .findViewWithTag<TextView>("lab-status"))
                assertTrue(status.text.contains("model_response_timeout"))
                folder = requireNotNull(activity.getExternalFilesDir("ui-audit-20261107-sprint6"))
                assertTrue(requireNotNull(folder).isDirectory || requireNotNull(folder).mkdirs())
            }
            instrumentation.waitForIdleSync()
            val shot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val file = File(requireNotNull(folder), "lab-timeout-synthetic.jpg")
                file.outputStream().use {
                    assertTrue(shot.compress(Bitmap.CompressFormat.JPEG, 82, it))
                }
                assertTrue(file.length() > 2000)
            } finally { shot.recycle() }
            println("S22_LAB_TIMEOUT_PRESENTATION=PASS_SYNTHETIC")
        }
    }

    @Test fun stopNoticeAndLatestTurnRemainVisibleWhenAutoFollowEnabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            var folder: File? = null
            scenario.onActivity { activity ->
                val clazz = activity.javaClass
                val pending = clazz.declaredMethods.first { it.name == "showTurnPending" }
                    .apply { isAccessible = true }
                val complete = clazz.declaredMethods.first { it.name == "showTurnCompleted" }
                    .apply { isAccessible = true }
                val notice = clazz.declaredMethods.first { it.name == "showStopNotice" }
                    .apply { isAccessible = true }
                val ctor = complete.parameterTypes[1].declaredConstructors
                    .first { it.parameterTypes.size == 7 }.apply { isAccessible = true }
                repeat(2) { index ->
                    val turn = index + 1
                    pending.invoke(activity, 0, "Offline", turn,
                        "Syntetyczna wiadomość o długości opisowej $turn.", index)
                    val reply = ctor.newInstance("Offline", turn, false, false,
                        "Przykładowa, wyłącznie lokalna odpowiedź nr $turn.", 150L, false)
                    complete.invoke(activity, 0, reply, turn)
                }
                notice.invoke(activity)
                folder = requireNotNull(activity.getExternalFilesDir("ui-audit-20261107-sprint6"))
                assertTrue(requireNotNull(folder).isDirectory || requireNotNull(folder).mkdirs())
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val banner = requireNotNull(activity.window.decorView.findViewWithTag<TextView>(
                    "lab-stopped-banner"))
                assertTrue(banner.text.contains("2") && banner.text.isNotBlank())
                val bannerVisible = Rect()
                assertTrue("stop_banner_not_in_viewport",
                    banner.getGlobalVisibleRect(bannerVisible))
                assertTrue("stop_banner_too_clipped", bannerVisible.height() > 24)
                val last = requireNotNull(activity.window.decorView.findViewWithTag<View>(
                    "lab-turn:0:2"))
                val lastVisible = Rect()
                assertTrue("last_turn_not_in_viewport",
                    last.getGlobalVisibleRect(lastVisible))
                assertTrue("last_turn_too_clipped", lastVisible.height() > 24)
            }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val file = File(requireNotNull(folder), "lab-follow-stop-synthetic.jpg")
                file.outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.JPEG, 82, it))
                }
                assertTrue(file.length() > 2000)
            } finally { screenshot.recycle() }
            println("S22_LAB_SYNTHETIC_STOP_FOLLOW=PASS")
        }
    }
}
