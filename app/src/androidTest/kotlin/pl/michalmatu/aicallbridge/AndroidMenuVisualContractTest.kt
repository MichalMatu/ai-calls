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

    @Test fun categoriesAreCollapsedAndContrastingWhileSafetyActionsRemainReachable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                for (name in listOf("basic", "secretary", "skills", "laboratory", "advanced")) {
                    val section = requireNotNull(root.findViewWithTag<UiAccordionSection>("ui-settings-section:$name"))
                    val toggle = requireNotNull(root.findViewWithTag<Button>("ui-accordion-toggle:$name"))
                    val body = requireNotNull(root.findViewWithTag<View>("ui-accordion-content:$name"))
                    assertEquals("category_is_expanded:$name", View.GONE, body.visibility)
                    val fill = requireNotNull((section.background as GradientDrawable).color).defaultColor
                    assertTrue("category_contrast:$name", ratio(toggle.currentTextColor, fill) >= 4.5)
                }
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>("live-chat-scroll")).visibility)
                assertTrue(requireNotNull(findButton(root, "Rozpocznij autoryzowane zadanie AI")).isShown)
                assertTrue(requireNotNull(findButton(root, "STOP AI")).isShown)
                assertTrue(requireNotNull(findButton(root, "Kampanie")).isShown)
                assertTrue(requireNotNull(findButton(root, "Rozmowy i nagrania")).isShown)
                assertTrue(requireNotNull(findButton(root, "Laboratorium offline")).isShown)
            }
        }
    }

    @Test fun bothSpinnerValuesRemainReadableWhenBasicSettingsAreOpened() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                requireNotNull(root.findViewWithTag<Button>("ui-accordion-toggle:basic")).performClick()
                for (name in listOf("settings-audio-mode-spinner", "settings-provider-spinner")) {
                    val spinner = requireNotNull(root.findViewWithTag<Spinner>(name))
                    val text = spinner.adapter.getView(spinner.selectedItemPosition, null, spinner) as TextView
                    val fill = requireNotNull((spinner.background as GradientDrawable).color).defaultColor
                    assertTrue("value_contrast:$name", ratio(text.currentTextColor, fill) >= 4.5)
                }
                val container = requireNotNull(root.findViewWithTag<View>("main-screen-root"))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val top = container.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top
                    if (top != null && top > 0) assertTrue(container.paddingTop >= top)
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
