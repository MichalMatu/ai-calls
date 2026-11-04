package pl.michalmatu.aicallbridge.audioengines

import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No call, no profile write, no authorization. Checks the real native Activity hierarchy. */
@RunWith(AndroidJUnit4::class)
class AndroidAudioEngineSettingsActivityTest {
    @Test fun showsThreeSeparatedSectionsWithUnavailableChoicesDisabled() {
        ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val views = mutableListOf<View>()
                fun traverse(view: View) {
                    views.add(view)
                    if (view is ViewGroup) {
                        for (i in 0 until view.childCount) traverse(view.getChildAt(i))
                    }
                }
                traverse(activity.findViewById(android.R.id.content))
                val radios = views.filterIsInstance<RadioButton>()
                assertEquals(9, radios.size)
                assertEquals(3, radios.count { it.isEnabled })
                assertTrue(radios.any { it.isEnabled && it.text.toString().contains("Android STT") })
                assertTrue(radios.any { it.isEnabled && it.text.toString().contains("Gemma 4 E2B") })
                assertTrue(radios.any { it.isEnabled && it.text.toString().contains("Android TTS") })
                assertTrue(radios.filterNot { it.isEnabled }.all {
                    it.text.toString().contains("NIEDOSTĘPNY")
                })
                val text = views.filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(text.any { it.contains("1. Wejście") })
                assertTrue(text.any { it.contains("2. Model rozmowy") })
                assertTrue(text.any { it.contains("3. Wyjście") })
                assertTrue(text.any { it.contains("Przywróć stabilny wariant") })
            }
        }
    }
}
