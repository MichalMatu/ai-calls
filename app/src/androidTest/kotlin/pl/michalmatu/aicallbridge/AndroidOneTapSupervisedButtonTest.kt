package pl.michalmatu.aicallbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Physical S22 no-call contract: one tap cannot dial or attach AI off-call. */
@RunWith(AndroidJUnit4::class)
class AndroidOneTapSupervisedButtonTest {
    @Test fun oneTapOffCallWritesSafeReportAndDoesNotStartMedia() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED,
        )
        assumeTrue(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
        val telecom = context.getSystemService(TelecomManager::class.java)
        assumeTrue(telecom != null && !telecom.isInCall)

        val report = File(context.filesDir, "supervised-call-readiness-report.txt")
        report.delete()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val diagnosticToggle = root.findViewWithTag<Button>(
                    "ui-accordion-toggle:diagnostics",
                )
                requireNotNull(diagnosticToggle) { "developer_diagnostics_toggle_not_found" }
                diagnosticToggle.performClick()
                val button = findOneTap(root)
                requireNotNull(button) { "one_tap_button_not_found" }
                assertTrue("supervised pilot is available after expanding diagnostics", button.isShown)
                button.performClick()
            }
        }
        assertTrue("diagnostic file must be saved", report.isFile)
        val text = report.readText()
        assertTrue(text.contains("probe=supervised_call_readiness"))
        assertTrue(text.contains("telecom_in_call=false"))
        assertTrue(text.contains("failure_reason="))
        assertTrue(text.contains("report_complete=true"))
        assertTrue(!text.contains("phone_number="))
    }

    private fun findOneTap(view: View): Button? {
        if (view is Button && view.text.contains("Jeden przycisk:")) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findOneTap(view.getChildAt(index))
                if (found != null) return found
            }
        }
        return null
    }
}
