package pl.michalmatu.aicallbridge.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.R

/** Presentation fixtures only: does not invoke a privileged probe, grant permission or call. */
class AndroidDiagnosticResultUxTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private fun emulatorOnly() = assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
    private fun capture(name: String) {
        instrument.waitForIdleSync()
        Thread.sleep(250)
        assertEquals(instrument.targetContext.packageName, instrument.uiAutomation.rootInActiveWindow?.packageName?.toString())
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val run = InstrumentationRegistry.getArguments().getString("uiAuditRunId") ?: "lab-ux"
        require(run.matches(Regex("[A-Za-z0-9_-]{6,48}")))
        val directory = File(instrument.targetContext.getExternalFilesDir(null), "ui-audit-$run")
        check(directory.isDirectory || directory.mkdirs())
        try { File(directory, "$name.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        finally { bitmap.recycle() }
    }
    private val offCall = """probe=shizuku-user-service-v1
mode=offcall-prepare-abort
binder_ping=true
service_uid=2000
prepared_before=false
active_before=false
prepared_after_prepare=true
active_after_prepare=false
heartbeat_after_prepare=false
prepared_after_abort=false
active_after_abort=false
heartbeat_after_abort=false
privileged_uid=true
offcall_parity_ok=true"""

    @Test fun shizukuResultIsModalWithExpectedInactiveFlagsAndExplicitDetails() {
        emulatorOnly()
        instrument.uiAutomation
        ActivityScenario.launch(DeveloperToolsActivity::class.java).use { scene ->
            lateinit var result: DiagnosticResultDialog
            lateinit var dialog: AlertDialog
            scene.onActivity { activity ->
                assertNull(activity.window.decorView.findViewWithTag<View>("developer-probe-result"))
                result = DiagnosticResultDialog(activity)
                result.showStatus(activity.getString(R.string.shizuku_running))
                assertEquals(activity.getString(R.string.shizuku_pass), result.showResult(offCall))
                dialog = result.javaClass.getDeclaredField("dialog").apply { isAccessible = true }.get(result) as AlertDialog
                val root = dialog.window!!.decorView
                assertEquals(activity.getString(R.string.shizuku_pass), root.findViewWithTag<TextView>("diagnostic-result-summary").text.toString())
                assertEquals(View.GONE, root.findViewWithTag<View>("diagnostic-result-raw").visibility)
            }
            capture("shizuku-result")
            scene.onActivity {
                val root = dialog.window!!.decorView
                root.findViewWithTag<View>("diagnostic-result-details").performClick()
                assertTrue(root.findViewWithTag<TextView>("diagnostic-result-raw").text.contains("heartbeat_after_prepare=false"))
            }
            capture("shizuku-details")
            scene.onActivity { activity ->
                result.showResult("offcall_parity_ok=true")
                assertEquals(activity.getString(R.string.shizuku_incomplete), dialog.window!!.decorView
                    .findViewWithTag<TextView>("diagnostic-result-summary").text.toString())
                result.dismiss()
            }
        }
    }

    @Test fun audioSourceFailuresAreScopedAndRawMessagesRemainInDetails() {
        emulatorOnly()
        instrument.uiAutomation
        ActivityScenario.launch(CapabilityDiagnosticsActivity::class.java).use { scene ->
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
            var ready = false
            while (!ready && System.nanoTime() < deadline) {
                scene.onActivity { ready = it.window.decorView.findViewWithTag<View>("capabilities-details").isEnabled }
                Thread.sleep(80)
            }
            assertTrue("capability probe did not finish", ready)
            scene.onActivity { activity ->
                val raw = """model=Test phone
communication_device=id=1,type=BUILTIN_EARPIECE(1),dir=sink,product=Phone,rates=48000,channels=1
audio_source_MIC=result:initialized,state:1
audio_source_VOICE_DOWNLINK=error:UnsupportedOperationException:permission or configuration unavailable
audio_device[0]=id=2,type=TELEPHONY(18),dir=source,product=Phone,rates=48000,channels=1
audio_device[1]=id=3,type=TELEPHONY(18),dir=sink,product=Phone,rates=48000,channels=1"""
                activity.javaClass.getDeclaredField("technicalReport").apply { isAccessible = true }.set(activity, raw)
                activity.javaClass.getDeclaredMethod("render", Map::class.java).apply { isAccessible = true }
                    .invoke(activity, CapabilityReportParser.read(raw))
                val root = activity.window.decorView
                assertEquals(activity.getString(R.string.cap_source_error), root.findViewWithTag<TextView>(
                    "capabilities-value:audio_source_VOICE_DOWNLINK").text.toString())
                assertEquals(activity.getString(R.string.cap_earpiece), root.findViewWithTag<TextView>(
                    "capabilities-value:communication_device").text.toString())
                val routes = root.findViewWithTag<android.widget.LinearLayout>("capabilities-routes")
                val labels = (0 until routes.childCount).mapNotNull { (routes.getChildAt(it) as? TextView)?.text?.toString() }
                assertTrue(labels.contains(activity.getString(R.string.cap_inputs)))
                assertTrue(labels.contains(activity.getString(R.string.cap_outputs)))
            }
            capture("capabilities-top")
            scene.onActivity { activity ->
                val button = activity.window.decorView.findViewWithTag<View>("capabilities-details")
                button.requestRectangleOnScreen(android.graphics.Rect(0, 0, button.width, button.height), true)
            }
            capture("capabilities-sources")
            scene.onActivity { activity -> activity.window.decorView.findViewWithTag<View>("capabilities-details").performClick() }
            capture("capabilities-details")
            assertTrue(instrument.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(
                "UnsupportedOperationException").isNotEmpty())
            instrument.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }
}
