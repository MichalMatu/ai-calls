package pl.michalmatu.aicallbridge

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity

@RunWith(AndroidJUnit4::class)
class AndroidAppNavigationContractTest {
    @Test fun homeShowsFourReachableTabsWithoutStartingCalls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("app-bottom-navigation"))
                for (route in listOf("home", "history", "lab", "settings")) {
                    val tab = requireNotNull(root.findViewWithTag<View>("app-tab:$route"))
                    assertTrue(tab.isShown)
                    assertTrue(tab.isClickable)
                }
            }
        }
    }
    @Test fun settingsHubUsesDirectLinksAndPreservesNavigation() {
        ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("app-screen-shell"))
                assertTrue(requireNotNull(root.findViewWithTag<View>("app-tab:settings")).isSelected)
                assertNull(root.findViewWithTag<View>("ui-accordion-toggle:basic"))
                assertTrue(requireNotNull(root.findViewWithTag<View>("settings-destination:GeneralSettingsActivity")).isClickable)
                assertTrue(requireNotNull(root.findViewWithTag<View>("settings-destination:DeveloperToolsActivity")).isClickable)
            }
        }
    }
}
