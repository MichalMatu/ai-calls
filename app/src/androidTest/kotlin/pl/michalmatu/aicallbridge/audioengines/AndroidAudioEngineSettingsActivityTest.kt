package pl.michalmatu.aicallbridge.audioengines

import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import android.widget.RadioGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No call, no profile write, no authorization. Checks the real native Activity hierarchy. */
@RunWith(AndroidJUnit4::class)
class AndroidAudioEngineSettingsActivityTest {
    @Test fun unsavedInputChoiceSurvivesRotationWithoutChangingStore() {
        ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
            lateinit var persisted: AudioEngineProfile
            scenario.onActivity { activity ->
                persisted = checkNotNull(AudioEngineProfileStore(activity).load())
                val radios = mutableListOf<RadioButton>()
                fun collect(view: View) {
                    if (view is RadioButton) radios.add(view)
                    if (view is ViewGroup) repeat(view.childCount) {
                        collect(view.getChildAt(it))
                    }
                }
                collect(activity.findViewById(android.R.id.content))
                radios.single { it.tag == "PARAKEET_PL" }.performClick()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                fun selected(view: View): Boolean {
                    if (view is RadioButton && view.tag == "PARAKEET_PL") return view.isChecked
                    if (view is ViewGroup) repeat(view.childCount) {
                        if (selected(view.getChildAt(it))) return true
                    }
                    return false
                }
                assertTrue("unsaved_engine_draft_lost_on_rotation",
                    selected(activity.findViewById(android.R.id.content)))
                assertEquals("rotation_persisted_draft_without_save",
                    persisted, AudioEngineProfileStore(activity).load())
            }
        }
    }

    @Test fun resetRestoresVisibleRadioChoicesNotOnlyStoredValues() {
        ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                fun find(view: View, tag: String): View? {
                    if (view.tag == tag) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) {
                        find(view.getChildAt(i), tag)?.let { return it }
                    }
                    return null
                }
                val parakeet = checkNotNull(find(root, "PARAKEET_PL") as? RadioButton)
                val stable = checkNotNull(find(root, "ANDROID_STT") as? RadioButton)
                assertTrue(parakeet.isEnabled)
                (parakeet.parent as RadioGroup).check(parakeet.id)
                assertTrue(parakeet.isChecked)
                checkNotNull(find(root, "engine-reset-stable")).performClick()
                assertTrue("reset_did_not_update_visible_radio", stable.isChecked)
                assertEquals(AudioEngineProfile.STABLE, AudioEngineProfileStore(activity).load())
            }
        }
    }

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
                assertEquals(4, radios.size)
                assertTrue(radios.all { it.isEnabled })
                assertTrue(radios.any { it.isEnabled && it.text.toString().contains("Android STT") })
                assertTrue(radios.any { it.isEnabled && it.text.toString().contains("Parakeet") })
                assertTrue(radios.any { it.isEnabled && it.text.toString().contains("Android TTS") })
                assertTrue(radios.any { it.isEnabled && it.text.toString().contains("Piper") })
                val text = views.filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(text.contains(activity.getString(pl.michalmatu.aicallbridge.R.string.eng_ui_input)))
                assertTrue(text.contains(activity.getString(pl.michalmatu.aicallbridge.R.string.ux07_models_selected_heading)))
                assertTrue(text.contains(activity.getString(pl.michalmatu.aicallbridge.R.string.eng_ui_output)))
                assertTrue(text.contains(activity.getString(pl.michalmatu.aicallbridge.R.string.eng_ui_reset)))
            }
        }
    }
}
