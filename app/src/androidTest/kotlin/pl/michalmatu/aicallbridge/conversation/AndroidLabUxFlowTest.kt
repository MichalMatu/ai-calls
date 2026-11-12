package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Emulator-only UI flow through the real runner and a controllable, synthetic backend. */
class AndroidLabUxFlowTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private class Backend : TextCallAgentBackend {
        @Volatile var reply: TextCallAgentBackend.Listener? = null
        @Volatile var closed = false
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) { reply = listener }
        override fun cancel() {}
        override fun close() { closed = true }
    }
    private fun emulatorOnly() = assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
    private fun eventually(check: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        while (System.nanoTime() < until) {
            if (check()) return
            Thread.sleep(80)
        }
        assertTrue("UI condition not reached", check())
    }
    private fun view(activity: ConversationSkillLabActivity, tag: String): View =
        requireNotNull(activity.window.decorView.findViewWithTag(tag))
    private fun installRunner(activity: ConversationSkillLabActivity, backend: Backend,
                              ready: CountDownLatch = CountDownLatch(0)): SkillLabRunner {
        val field = activity.javaClass.getDeclaredField("runner").apply { isAccessible = true }
        (field.get(activity) as SkillLabRunner).close()
        val render = activity.javaClass.getDeclaredMethod("renderState", SkillLabUiState::class.java)
            .apply { isAccessible = true }
        val runner = SkillLabRunner(SkillLabModelSessionFactory {
            ready.await()
            SkillLabModelSession { backend }
        }, { action -> activity.runOnUiThread(action) }, { state -> render.invoke(activity, state) })
        field.set(activity, runner)
        return runner
    }
    private fun capture(label: String) {
        instrument.waitForIdleSync()
        Thread.sleep(200)
        assertEquals(instrument.targetContext.packageName, instrument.uiAutomation.rootInActiveWindow?.packageName?.toString())
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val run = InstrumentationRegistry.getArguments().getString("uiAuditRunId") ?: "lab-ux"
        require(run.matches(Regex("[A-Za-z0-9_-]{6,48}")))
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "ui-audit-$run")
        check(folder.isDirectory || folder.mkdirs())
        try { File(folder, "$label.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun setupOwnsTheViewportAndDoesNotShowAnEmptyChat() {
        emulatorOnly()
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scene ->
            instrument.waitForIdleSync()
            scene.onActivity { activity ->
                val setup = view(activity, "lab-options-scroll")
                assertTrue(setup.isShown)
                assertEquals((setup.parent as View).height, setup.height)
                assertFalse(view(activity, "lab-session-screen").isShown)
                assertFalse(view(activity, "lab-manual-composer").isShown)
                assertNull(activity.window.decorView.findViewWithTag<View>("lab-model-diagnostics"))
                view(activity, "lab-advanced-toggle").performClick()
                assertTrue(view(activity, "lab-context-tokens").isShown)
                view(activity, "lab-advanced-toggle").performClick()
            }
            capture("lab-setup")
        }
    }

    @Test fun chatShowsProgressRepliesKeyboardDiagnosticsAndStopsLateReplies() {
        emulatorOnly()
        // Establish accessibility before opening the first app window.
        instrument.uiAutomation
        val backend = Backend()
        val ready = CountDownLatch(1)
        lateinit var runner: SkillLabRunner
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scene ->
            scene.onActivity { activity ->
                runner = installRunner(activity, backend, ready)
                view(activity, "lab-start").performClick()
                assertTrue(view(activity, "lab-session-screen").isShown)
                assertFalse(view(activity, "lab-options-scroll").isShown)
                assertTrue(view(activity, "lab-stop").isShown)
                assertTrue(view(activity, "lab-wait-clock").isShown)
            }
            capture("lab-starting")
            ready.countDown()
            eventually { runner.state.phase == SkillLabPhase.INPUT_READY }
            instrument.waitForIdleSync()
            scene.onActivity { activity ->
                val input = view(activity, "lab-manual-input") as EditText
                input.setText("Dzień dobry, proszę przyjąć wiadomość testową.")
                input.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            if (Build.VERSION.SDK_INT >= 30) eventually {
                var shown = false
                scene.onActivity { shown = it.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
                shown
            }
            scene.onActivity { activity ->
                val stop = view(activity, "lab-stop")
                val rect = Rect()
                assertTrue(stop.getGlobalVisibleRect(rect))
                assertTrue("STOP clipped by keyboard", rect.height() >= 44 * activity.resources.displayMetrics.density)
                assertTrue("chat has no useful viewport", view(activity, "lab-transcript-scroll").height >= 80 * activity.resources.displayMetrics.density)
            }
            capture("lab-keyboard")
            scene.onActivity { view(it, "lab-manual-send").performClick() }
            eventually { backend.reply != null }
            val first = requireNotNull(backend.reply)
            capture("lab-waiting")
            first.onComplete("""{"reply":"Dziękuję. Zapisałem przykładową wiadomość do testu.\nTo druga linia odpowiedzi.","tool":"none"}""")
            eventually { runner.state.completed.size == 1 && runner.state.phase == SkillLabPhase.INPUT_READY }
            instrument.waitForIdleSync()
            scene.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(requireNotNull(root.findViewWithTag<TextView>("lab-reply:0:1")).text.contains("przykładową"))
                assertFalse(view(activity, "lab-wait-clock").isShown)
                activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view(activity, "lab-manual-input").windowToken, 0)
            }
            capture("lab-chat")
            scene.onActivity { activity ->
                view(activity, "lab-observe").performClick()
                val dialog = activity.javaClass.getDeclaredField("diagnosticsDialog").apply { isAccessible = true }
                    .get(activity) as AlertDialog
                assertNotNull(dialog.window!!.decorView.findViewWithTag<View>("lab-model-diagnostics"))
                assertNotNull(dialog.window!!.decorView.findViewWithTag<View>("lab-decision:0:1"))
            }
            capture("lab-diagnostics")
            scene.onActivity { activity ->
                (activity.javaClass.getDeclaredField("diagnosticsDialog").apply { isAccessible = true }.get(activity) as AlertDialog).dismiss()
                (view(activity, "lab-manual-input") as EditText).setText("Druga wiadomość")
                view(activity, "lab-manual-send").performClick()
            }
            eventually { backend.reply !== first }
            val late = requireNotNull(backend.reply)
            scene.onActivity { view(it, "lab-stop").performClick() }
            eventually { runner.state.manualStartAllowed && backend.closed }
            late.onComplete("""{"reply":"STALE_REPLY_MUST_NOT_APPEAR","tool":"none"}""")
            instrument.waitForIdleSync()
            scene.onActivity { activity ->
                assertEquals(1, runner.state.completed.size)
                assertNull(activity.window.decorView.findViewWithTag<View>("lab-reply:0:2"))
                assertFalse(view(activity, "lab-manual-send").isEnabled)
                assertTrue(view(activity, "lab-return-setup").isShown)
            }
            capture("lab-stopped")
            scene.onActivity { activity ->
                activity.navigateBack()
                assertTrue(view(activity, "lab-options-scroll").isShown)
                assertFalse(view(activity, "lab-session-screen").isShown)
                view(activity, "lab-resume-chat").performClick()
                assertTrue(view(activity, "lab-reply:0:1").isShown)
            }
        }
    }

    @Test fun backendFailureIsReadableAndRecreationNeverRestartsTheTest() {
        emulatorOnly()
        val backend = Backend()
        lateinit var runner: SkillLabRunner
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scene ->
            scene.onActivity { activity -> runner = installRunner(activity, backend); view(activity, "lab-start").performClick() }
            eventually { runner.state.phase == SkillLabPhase.INPUT_READY }
            scene.onActivity { activity ->
                (view(activity, "lab-manual-input") as EditText).setText("Test błędu")
                view(activity, "lab-manual-send").performClick()
            }
            eventually { backend.reply != null }
            requireNotNull(backend.reply).onError("model_response_timeout")
            eventually { runner.state.phase == SkillLabPhase.FAILED && runner.state.manualStartAllowed }
            instrument.waitForIdleSync()
            scene.onActivity { activity ->
                assertEquals(activity.getString(R.string.lab_chat_timeout), (view(activity, "lab-status") as TextView).text.toString())
                assertTrue(view(activity, "lab-return-setup").isShown)
            }
            capture("lab-error")
            scene.recreate()
            scene.onActivity { activity ->
                assertTrue(view(activity, "lab-options-scroll").isShown)
                assertFalse(view(activity, "lab-session-screen").isShown)
                assertFalse(view(activity, "lab-manual-send").isEnabled)
            }
        }
    }
}
