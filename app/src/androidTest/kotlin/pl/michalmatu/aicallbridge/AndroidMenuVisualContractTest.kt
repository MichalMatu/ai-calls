package pl.michalmatu.aicallbridge

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversation.ConversationChatFeed
import pl.michalmatu.aicallbridge.ui.GeneralSettingsActivity
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiDesign

/** Navigation, readability and synthetic layout only; run on an isolated emulator. */
@RunWith(AndroidJUnit4::class)
class AndroidMenuVisualContractTest {
    @Before fun isolatedEmulator() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
    }

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
                for (name in listOf("home", "history", "agent", "settings")) {
                    val tab = requireNotNull(root.findViewWithTag<View>("app-tab:$name"))
                    assertTrue("tab_visible:$name", tab.isShown)
                    assertTrue("tab_clickable:$name", tab.isClickable)
                }
                for (tag in listOf("conversation-floating-button", "conversation-floating-panel"))
                    assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(tag)).visibility)
                assertNull(root.findViewWithTag<View>("app-tab:lab"))
                assertNull(root.findViewWithTag<View>("app-tab:models"))
                assertNull(findButton(root, activity.getString(R.string.ui_campaigns)))
                assertTrue(requireNotNull(root.findViewWithTag<View>("home-appointment-start")).isShown)
                assertTrue(requireNotNull(root.findViewWithTag<View>("home-stop-ai")).isShown)
                assertNotNull(root.findViewWithTag<View>("app-screen-shell"))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val shell = requireNotNull(root.findViewWithTag<View>("app-screen-shell"))
                    val top = shell.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top
                    if (top != null && top > 0) assertTrue(shell.paddingTop >= top)
                }
            }
        }
    }

    @Test fun languageIsOneDirectlyVisibleDropdownWithThreeChoices() {
        ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val picker = requireNotNull(root.findViewWithTag<Spinner>("ui-language-picker"))
                assertTrue(picker.isShown)
                assertEquals(3, picker.adapter.count)
                val selected = picker.adapter.getView(picker.selectedItemPosition, null, picker) as TextView
                val fill = requireNotNull((picker.background as GradientDrawable).color).defaultColor
                assertTrue(ratio(selected.currentTextColor, fill) >= 4.5)
                assertEquals(activity.getString(R.string.ui_language_label), picker.contentDescription)
                val tab = requireNotNull(root.findViewWithTag<ViewGroup>("app-tab:settings"))
                assertEquals(activity.getString(R.string.ui_tab_settings),
                    (tab.getChildAt(1) as TextView).text.toString())
            }
        }
    }

    @Test fun menuRowsWrapAtLargeTextWithoutClippingOrExtraTouchTargets() {
        ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val configuration = Configuration(activity.resources.configuration).apply { fontScale = 1.6f }
                val context = activity.createConfigurationContext(configuration)
                val summary = UiDesign.caption(context, "Przychodzące: 4 pola · Wychodzące: 4 pola")
                var opened = 0
                val row = UiDesign.navigationRow(context, "Kontekst rozmów", summary) { opened++ }
                val width = UiDesign.dp(context, 260)
                row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                row.layout(0, 0, width, row.measuredHeight)
                assertTrue(row.height >= UiDesign.dp(context, 56))
                assertTrue(summary.lineCount > 1)
                assertTrue(summary.layout.height <= summary.height - summary.paddingTop - summary.paddingBottom)
                assertEquals(0, summary.layout.getEllipsisCount(summary.lineCount - 1))
                assertTrue(row.contentDescription.contains(summary.text))
                assertEquals(0, opened)
                row.performClick()
                assertEquals(1, opened)
                assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, row.descendantFocusability)
            }
        }
    }

    @Test fun disabledActionsRemainReadableAndVisuallyDistinct() {
        ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val buttons = UiActionTone.entries.map { tone ->
                    UiDesign.action(activity, "Akcja", tone) {}
                } + Button(activity).apply { text = "Akcja" }
                for (button in buttons) {
                    fun fill(): Int {
                        val shape = (button.background as RippleDrawable).getDrawable(0) as GradientDrawable
                        val colors = requireNotNull(shape.color)
                        return colors.getColorForState(button.drawableState, colors.defaultColor)
                    }
                    val enabledFill = fill()
                    assertTrue("enabled_contrast", ratio(button.currentTextColor, enabledFill) >= 4.5)
                    button.isEnabled = false
                    assertNotEquals("disabled_fill", enabledFill, fill())
                    assertTrue("disabled_contrast", ratio(button.currentTextColor, fill()) >= 4.5)
                }
            }
        }
    }

    @Test fun spacingPreservesCardMarginsAndIgnoresHiddenAndTrailingActions() {
        ActivityScenario.launch(GeneralSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val body = UiDesign.content(activity)
                val card = UiDesign.card(activity)
                body.addView(card, UiDesign.spaceAfter(activity, 4))
                fun action() = UiDesign.action(activity, "Akcja") {}
                val first = action()
                val hidden = action().apply { visibility = View.GONE }
                val second = action()
                val last = action()
                body.addView(first, UiDesign.spaceAfter(activity, 0))
                body.addView(hidden, UiDesign.spaceAfter(activity, 0))
                body.addView(second, UiDesign.spaceAfter(activity, 24))
                body.addView(last, UiDesign.spaceAfter(activity, 0))
                activity.setContentView(body)
                fun margin(view: View) = (view.layoutParams as LinearLayout.LayoutParams).bottomMargin
                assertEquals(UiDesign.dp(activity, 4), margin(card))
                assertEquals(UiDesign.dp(activity, 12), margin(first))
                assertEquals(UiDesign.dp(activity, 24), margin(second))
                assertEquals(0, margin(hidden))
                assertEquals(0, margin(last))
            }
        }
    }

    @Test fun compactChatCanBeExpandedAndCollapsedWithoutCalling() {
        val token = ConversationChatFeed.begin()
        try {
            ConversationChatFeed.heard(token, 1, "Syntetyczny podgląd")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val toggle = requireNotNull(root.findViewWithTag<View>("conversation-floating-button"))
                    val panel = requireNotNull(root.findViewWithTag<View>("conversation-floating-panel"))
                    assertEquals(View.VISIBLE, toggle.visibility)
                    assertEquals(View.GONE, panel.visibility)
                    toggle.performClick()
                    assertEquals(View.VISIBLE, panel.visibility)
                    requireNotNull(root.findViewWithTag<View>("conversation-floating-scrim")).performClick()
                    assertEquals(View.GONE, panel.visibility)
                    assertTrue(requireNotNull(root.findViewWithTag<View>("home-stop-ai")).isShown)
                }
            }
        } finally {
            ConversationChatFeed.finish(token, "operator_stopped")
            ConversationChatFeed.clearVisibleHistory()
        }
    }
}
