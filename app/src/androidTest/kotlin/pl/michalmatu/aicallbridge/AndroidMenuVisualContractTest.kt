package pl.michalmatu.aicallbridge

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.ui.GeneralSettingsActivity

@RunWith(AndroidJUnit4::class)
class AndroidMenuVisualContractTest {
    private fun ratio(a: Int, b: Int): Double {
        val hi = maxOf(Color.luminance(a), Color.luminance(b))
        val lo = minOf(Color.luminance(a), Color.luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }
    private fun findButton(view: View, title: String): Button? {
        if (view is Button && view.text.contains(title)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findButton(view.getChildAt(i), title)?.let { return it }
        }
        return null
    }
    @Test fun directNavigationHasFourVisibleTabsAndSafetyActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                for (name in listOf("home", "history", "lab", "settings")) {
                    val tab = requireNotNull(root.findViewWithTag<View>("app-tab:$name"))
                    assertTrue("tab_visible:$name", tab.isShown)
                    assertTrue("tab_clickable:$name", tab.isClickable)
                }
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>("live-chat-scroll")).visibility)
                assertTrue(requireNotNull(findButton(root, "Rozpocznij autoryzowane zadanie AI")).isShown)
                assertTrue(requireNotNull(findButton(root, "STOP AI")).isShown)
                assertNull(root.findViewWithTag<View>("ui-accordion-toggle:basic"))
            }
        }
    }
    @Test fun basicSelectorsStayReadableInTheirDedicatedScreen() {
        ActivityScenario.launch(GeneralSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                for (name in listOf("settings-audio-mode-spinner", "settings-provider-spinner")) {
                    val spinner = requireNotNull(root.findViewWithTag<Spinner>(name))
                    val text = spinner.adapter.getView(spinner.selectedItemPosition, null, spinner) as TextView
                    val fill = requireNotNull((spinner.background as GradientDrawable).color).defaultColor
                    assertTrue("value_contrast:$name", ratio(text.currentTextColor, fill) >= 4.5)
                }
                assertNotNull(root.findViewWithTag<View>("app-screen-shell"))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val shell = requireNotNull(root.findViewWithTag<View>("app-screen-shell"))
                    val top = shell.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top
                    if (top != null && top > 0) assertTrue(shell.paddingTop >= top)
                }
            }
        }
    }
    @Test fun compactChatCanBeExpandedAndCollapsedWithoutCalling() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val toggle = requireNotNull(root.findViewWithTag<Button>("live-chat-toggle"))
                val scroll = requireNotNull(root.findViewWithTag<View>("live-chat-scroll"))
                assertEquals(View.GONE, scroll.visibility)
                toggle.performClick()
                assertEquals(View.VISIBLE, scroll.visibility)
                toggle.performClick()
                assertEquals(View.GONE, scroll.visibility)
                assertTrue(requireNotNull(findButton(root, "STOP AI")).isShown)
            }
        }
    }
}
