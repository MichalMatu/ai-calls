package pl.michalmatu.aicallbridge

import android.app.Activity
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
            instrumentation.waitForIdleSync()
            var rendered: Bitmap? = null
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue("screen_title_absent:" + key,
                    labels(root).any { it == activity.getString(expectedTitle) })
                assertEquals("screen_locale_wrong:" + key, locale,
                    activity.resources.configuration.locales[0].language)
                assertTrue("view_not_laid_out:" + key, root.width > 500 && root.height > 500)
                rendered = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                    root.draw(android.graphics.Canvas(it))
                }
            }
            val bitmap = checkNotNull(rendered)
            try {
                val file = File(directory, key + "-" + locale + ".png")
                FileOutputStream(file).use { output ->
                    assertTrue("png_compression_failed:" + key,
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                assertTrue("png_empty:" + key, file.isFile && file.length() > 200)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun captureCurrentLocaleWithoutChangingPreferences() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val locale = InstrumentationRegistry.getArguments().getString("ux05Locale")
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
