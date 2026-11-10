package pl.michalmatu.aicallbridge

import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity
import java.util.Locale

/** UX-05 UI-only checks: no locale preference write, Call, model transfer or archive mutation. */
@RunWith(AndroidJUnit4::class)
class AndroidUx05LocaleAccessibilityTest {
    private fun labels(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) repeat(view.childCount) { addAll(labels(view.getChildAt(it))) }
    }

    @Test fun plEnResourcesResolveWithoutChangingTheUserLocale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = listOf(R.string.camp_ux05_record_local,
            R.string.pilot_ui_ready, R.string.models_ux05_detail_e2b,
            R.string.skills_ux05_section_style, R.string.home_one_tap_permission,
            R.string.dialer_ui_take_over)
        fun translated(tag: String, id: Int): String {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(tag))
            return context.createConfigurationContext(configuration).getString(id)
        }
        resources.forEach { id ->
            assertTrue("empty_pl:" + id, translated("pl", id).isNotBlank())
            assertTrue("empty_en:" + id, translated("en", id).isNotBlank())
            assertNotEquals("same_translation:" + id, translated("pl", id), translated("en", id))
        }
    }

    @Test fun localizedReadOnlyScreensExposeHeadingsAndNeverStartCalls() {
        ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val title = activity.getString(R.string.ui_settings_hub_title)
                assertTrue(labels(root).contains(title))
                val nav = requireNotNull(root.findViewWithTag<View>("app-tab:settings"))
                assertEquals(activity.getString(R.string.ui_tab_settings), nav.contentDescription.toString())
                assertTrue(nav.isFocusable)
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        val screens = listOf(
            AudioEngineSettingsActivity::class.java to R.string.eng_ui_title,
            LanguageModelActivity::class.java to R.string.models_ui_title,
            ConversationSkillsActivity::class.java to R.string.skills_ui_title,
        )
        for ((type, titleId) in screens) {
            ActivityScenario.launch(type).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val title = activity.getString(titleId)
                    assertTrue("missing_title:" + type.simpleName, labels(root).any { it == title })
                    assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                    if (Build.VERSION.SDK_INT >= 28) {
                        // Shared UiDesign headings become TalkBack navigation headings.
                        var found = false
                        fun visit(v: View) {
                            if (v is TextView && v.text == title && v.isAccessibilityHeading) found = true
                            if (v is ViewGroup) repeat(v.childCount) { visit(v.getChildAt(it)) }
                        }
                        visit(root)
                        assertTrue("missing_accessibility_heading:" + type.simpleName, found)
                    }
                }
            }
        }
    }
}
