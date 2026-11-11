package pl.michalmatu.aicallbridge.audioengines

import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.ui.GeneralSettingsActivity
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity

/** Isolated emulator only. No model load, download, phone call or S22 tests. */
@RunWith(AndroidJUnit4::class)
class AndroidModelVoiceSettingsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var previous: AudioEngineProfile

    @Before fun isolatedEmulator() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        previous = checkNotNull(AudioEngineProfileStore(context).load())
        assertTrue(AudioEngineProfileStore(context).save(AudioEngineProfile.STABLE))
    }

    @After fun restorePreference() {
        if (::previous.isInitialized) assertTrue(AudioEngineProfileStore(context).save(previous))
    }

    private fun visibleDialogWith(tag: String): View =
        WindowInspector.getGlobalWindowViews().last { window ->
            window.isShown && window.findViewWithTag<View>(tag) != null
        }

    @Test fun mainEditorHasThreeCardsAndNoOldSaveOrAdvancedPage() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertEquals(activity.getString(R.string.mv_title), activity.toolbarTitle())
                for (tag in listOf("model-voice-model", "model-voice-inputs", "model-voice-voice",
                    "model-manage-files", "model-voice-readiness"))
                    assertNotNull("missing $tag", root.findViewWithTag<View>(tag))
                for (old in listOf("engine-save", "model-voice-advanced",
                    "model-voice-advanced-toggle", "settings-provider-spinner"))
                    assertNull("obsolete $old", root.findViewWithTag<View>(old))
            }
        }
    }

    @Test fun inputModalShowsParakeetOnlyInAdditionalOptions() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNull(root.findViewWithTag<View>("PARAKEET_PL"))
                root.findViewWithTag<View>("model-voice-inputs").performClick()
                val dialog = visibleDialogWith("PARAKEET_PL")
                assertNotNull(dialog.findViewWithTag<RadioButton>("ANDROID_STT"))
                assertNotNull(dialog.findViewWithTag<RadioButton>("GEMMA_AUDIO_DIRECT"))
                assertNotNull(dialog.findViewWithTag<RadioButton>("PARAKEET_PL"))
                assertEquals(AudioEngineProfile.STABLE, AudioEngineProfileStore(context).load())
            }
        }
    }

    @Test fun selectingGemmaInputAutoSavesButDoesNotClaimReadiness() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                root.findViewWithTag<View>("model-voice-inputs").performClick()
                visibleDialogWith("GEMMA_AUDIO_DIRECT")
                    .findViewWithTag<RadioButton>("GEMMA_AUDIO_DIRECT").performClick()
                val saved = checkNotNull(AudioEngineProfileStore(context).load())
                assertEquals(SpeechInputId.GEMMA_AUDIO_DIRECT, saved.input)
                assertEquals(activity.getString(R.string.mv_gemma_short),
                    root.findViewWithTag<TextView>("model-voice-input-choice").text.toString())
                assertFalse(AudioEngineReadinessProbe.check(context, saved).available)
            }
        }
    }

    @Test fun savedInputSurvivesRotationAndAliasUsesSameEditor() {
        ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>("model-voice-inputs").performClick()
                visibleDialogWith("GEMMA_AUDIO_DIRECT")
                    .findViewWithTag<RadioButton>("GEMMA_AUDIO_DIRECT").performClick()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(SpeechInputId.GEMMA_AUDIO_DIRECT,
                    AudioEngineProfileStore(context).load()?.input)
                assertEquals(activity.getString(R.string.mv_gemma_short),
                    activity.window.decorView.findViewWithTag<TextView>("model-voice-input-choice")
                        .text.toString())
            }
        }
    }

    @Test fun preselectedParakeetRemainsAvailableInInputModal() {
        val requested = AudioEngineProfile(input = SpeechInputId.PARAKEET_PL)
        assertTrue(AudioEngineProfileStore(context).save(requested))
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>("model-voice-inputs").performClick()
                assertTrue(visibleDialogWith("PARAKEET_PL")
                    .findViewWithTag<RadioButton>("PARAKEET_PL").isChecked)
                assertEquals(requested, AudioEngineProfileStore(context).load())
            }
        }
    }

    @Test fun settingsHubHasOneEditorAndPermissionsHaveNoLegacyProviderPickers() {
        ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("settings-destination:LanguageModelActivity"))
                assertNull(root.findViewWithTag<View>("settings-destination:AudioEngineSettingsActivity"))
            }
        }
        ActivityScenario.launch(GeneralSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNull(root.findViewWithTag<View>("settings-audio-mode-spinner"))
                assertNull(root.findViewWithTag<View>("settings-provider-spinner"))
                assertNotNull(root.findViewWithTag<View>("dialer-role-card"))
            }
        }
    }
}
