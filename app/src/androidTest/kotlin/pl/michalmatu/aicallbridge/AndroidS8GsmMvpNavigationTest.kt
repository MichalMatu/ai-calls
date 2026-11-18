package pl.michalmatu.aicallbridge

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.AndroidCallControlsActivity
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity

/** Non-calling S8 GSM acceptance; no permission, role or media mutations. */
@RunWith(AndroidJUnit4::class)
class AndroidS8GsmMvpNavigationTest {
    @Test fun settingsHideParallelDeveloperPilotInS8() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(target.packageName.endsWith(".s8lab"))
        ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNull(root.findViewWithTag<View>("settings-destination:DeveloperToolsActivity"))
                assertNotNull(root.findViewWithTag<View>("settings-destination:IncomingAssistantSettingsActivity"))
            }
        }
    }

    @Test fun idleCallUiShowsOnlyIdleActions() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(target.packageName.endsWith(".s8lab"))
        ActivityScenario.launch(AndroidCallControlsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val idle = requireNotNull(root.findViewWithTag<View>("manual-call-idle-controls"))
                val ringing = requireNotNull(root.findViewWithTag<View>("manual-call-ringing-controls"))
                val active = requireNotNull(root.findViewWithTag<View>("manual-call-active-controls"))
                assertEquals(View.VISIBLE, idle.visibility)
                assertEquals(View.GONE, ringing.visibility)
                assertEquals(View.GONE, active.visibility)
                assertTrue(requireNotNull(root.findViewWithTag<View>("manual-call-dial")).isClickable)
                assertNotNull(root.findViewWithTag<View>("manual-call-status"))
            }
        }
    }
}
