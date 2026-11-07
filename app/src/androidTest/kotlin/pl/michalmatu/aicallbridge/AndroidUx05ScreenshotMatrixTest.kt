package pl.michalmatu.aicallbridge

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity
import java.io.File
import java.io.FileOutputStream

/** Internal ActivityScenario path: no exported Activity, CALL, preference edit or private archive. */
@RunWith(AndroidJUnit4::class)
class AndroidUx05ScreenshotMatrixTest {
    private fun labels(v: View): List<String> = buildList {
        if (v is TextView) add(v.text.toString())
        if (v is ViewGroup) repeat(v.childCount) { addAll(labels(v.getChildAt(it))) }
    }

    private fun <T : Activity> capture(type: Class<T>, key: String,
                                        expectedTitle: Int, locale: String, directory: File) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(type).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue("screen_title_absent:" + key,
                    labels(activity.window.decorView).any {
                        it == activity.getString(expectedTitle)
                    })
                assertEquals("screen_locale_wrong:" + key, locale,
                    activity.resources.configuration.locales[0].language)
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(550)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull("screenshot_unavailable:" + key, bitmap)
            try {
                val file = File(directory, key + "-" + locale + ".png")
                FileOutputStream(file).use { output ->
                    assertTrue("png_compression_failed:" + key,
                        checkNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                assertTrue("png_empty:" + key, file.isFile && file.length() > 10_000)
            } finally {
                bitmap?.recycle()
            }
        }
    }

    @Test fun captureCurrentLocaleWithoutChangingPreferences() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val locale = instrumentation.arguments.getString("ux05Locale")
        assertTrue("locale_argument_required", locale == "pl" || locale == "en")
        val target = instrumentation.targetContext
        val directory = requireNotNull(target.getExternalFilesDir("ui-audit-ux05"))
        assertTrue("private_directory_missing", directory.isDirectory || directory.mkdirs())
        val verifiedLocale = checkNotNull(locale)
        capture(SettingsHubActivity::class.java, "settings",
            R.string.ui_settings_hub_title, verifiedLocale, directory)
        capture(AudioEngineSettingsActivity::class.java, "engines",
            R.string.eng_ui_title, verifiedLocale, directory)
    }
}
