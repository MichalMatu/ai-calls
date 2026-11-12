package pl.michalmatu.aicallbridge.conversation

import android.app.Instrumentation
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.PhoneTaskWizardActivity
import pl.michalmatu.aicallbridge.campaign.CampaignAuthorizationStore

/** Off-call navigation only: no user consent, no grant and no Telecom dial. */
@RunWith(AndroidJUnit4::class)
class AndroidScenarioOutboundNavigationTest {
    @Test fun scenarioFormOpensPrivateConfirmationWithoutAutorization() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val before = CampaignAuthorizationStore(context).load()
        val monitor: Instrumentation.ActivityMonitor = instrumentation.addMonitor(
            PhoneTaskWizardActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(ScenarioLibraryActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    requireNotNull(activity.window.decorView.findViewWithTag<View>(
                        "scenario-preset:mechanic")).performClick()
                    val form = activity.window.decorView
                    assertNotNull(form.findViewWithTag<View>("scenario-prepare-outgoing"))
                    requireNotNull(form.findViewWithTag<View>(
                        "scenario-prepare-outgoing")).performClick()
                }
                val next = instrumentation.waitForMonitorWithTimeout(monitor, 9000)
                assertNotNull("confirmation_screen_not_launched", next)
                val deadline = System.currentTimeMillis() + 9000
                var formReady = false
                while (!formReady && System.currentTimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        formReady = next?.window?.decorView?.findViewWithTag<View>("task-goal") != null
                    }
                    if (!formReady) Thread.sleep(80)
                }
                try {
                    instrumentation.runOnMainSync {
                        val root = requireNotNull(next).window.decorView
                        assertNotNull(root.findViewWithTag<View>("task-goal"))
                        assertNotNull(root.findViewWithTag<View>("task-title"))
                        assertNotNull(root.findViewWithTag<View>("task-next"))
                    }
                } finally {
                    if (next != null) instrumentation.runOnMainSync { next.finish() }
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        val after = CampaignAuthorizationStore(context).load()
        assertEquals("navigation_must_not_change_grant", before, after)
    }
}
