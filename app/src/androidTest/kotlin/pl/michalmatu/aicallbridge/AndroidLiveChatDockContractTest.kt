package pl.michalmatu.aicallbridge

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AndroidLiveChatDockContractTest {
    @Test fun lightChatDockHasCenteredChevronAndRealTouchArea() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val dock = requireNotNull(root.findViewWithTag<View>("live-chat-dock"))
                val toggle = requireNotNull(root.findViewWithTag<ImageButton>("live-chat-toggle"))
                val fill = requireNotNull((dock.background as GradientDrawable).color).defaultColor
                assertEquals(Color.WHITE, fill)
                assertTrue(toggle.width / activity.resources.displayMetrics.density >= 47f)
                assertTrue(toggle.height / activity.resources.displayMetrics.density >= 47f)
                assertEquals(ImageView.ScaleType.CENTER_INSIDE, toggle.scaleType)
                val scroll = requireNotNull(root.findViewWithTag<View>("live-chat-scroll"))
                assertEquals(View.GONE, scroll.visibility)
                toggle.performClick()
                assertEquals(View.VISIBLE, scroll.visibility)
                toggle.performClick()
                assertEquals(View.GONE, scroll.visibility)
            }
        }
    }
    @Test fun languageResourcesHaveTwoIndependentVersions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val english = Configuration(activity.resources.configuration).apply { setLocale(Locale.ENGLISH) }
                val polish = Configuration(activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("pl")) }
                assertEquals("Live conversation",activity.createConfigurationContext(english).getString(R.string.live_chat_title))
                assertEquals("Rozmowa na żywo",activity.createConfigurationContext(polish).getString(R.string.live_chat_title))
            }
        }
    }
}
