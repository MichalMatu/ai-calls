package pl.michalmatu.aicallbridge

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversation.ConversationHistoryActivity

/** Memory-only screenshot smoke; never saves or uploads a private image. */
@RunWith(AndroidJUnit4::class)
class AndroidScreenshotAllowedTest {
    @Test fun historyIsCaptureableWithoutSavingAnyImage() {
        ActivityScenario.launch(ConversationHistoryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            }
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            assertNotNull("platform_screenshot_unavailable", bitmap)
            assertTrue("invalid_screenshot_width", checkNotNull(bitmap).width > 0)
            assertTrue("invalid_screenshot_height", bitmap.height > 0)
            bitmap.recycle()
        }
    }
}
