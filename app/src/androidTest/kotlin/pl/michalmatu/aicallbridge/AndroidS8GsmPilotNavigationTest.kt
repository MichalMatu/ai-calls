package pl.michalmatu.aicallbridge

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.ui.DeveloperToolsActivity

/** S8-only UI acceptance. Never dials, attaches PCM or changes phone roles. */
@RunWith(AndroidJUnit4::class)
class AndroidS8GsmPilotNavigationTest {
    @Test fun isolatedS8ShowsGsmPilotInsteadOfSuspendedSipLabs() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("S8 lab debug package required", target.packageName.endsWith(".s8lab"))
        ActivityScenario.launch(DeveloperToolsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val gsmPilot = root.findViewWithTag<View>("developer-s8-gsm-pilot")
                assertNotNull("no_shizuku_gsm_pilot_button_missing", gsmPilot)
                assertTrue("gsm_pilot_not_visible", requireNotNull(gsmPilot).isShown)
                assertTrue("gsm_pilot_not_clickable", gsmPilot.isClickable)
                assertNull("suspended_sip_lab_exposed",
                    root.findViewWithTag<View>("developer-s8-sip-lab"))
                assertNull("suspended_voip_lab_exposed",
                    root.findViewWithTag<View>("developer-s8-voip-lab"))
            }
        }
    }
}
