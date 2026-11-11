package pl.michalmatu.aicallbridge.audioengines

import android.Manifest
import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.Button
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

/** Disposable emulator only. Never loads a model, downloads bytes, captures audio or starts a Call. */
@RunWith(AndroidJUnit4::class)
class AndroidModelVoiceSettingsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var previous: AudioEngineProfile

    @Before fun isolatedEmulator() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_PHONE_STATE)
        previous = checkNotNull(AudioEngineProfileStore(context).load())
        assertTrue(AudioEngineProfileStore(context).save(AudioEngineProfile.STABLE))
    }
    @After fun restorePreference() {
        if (::previous.isInitialized) assertTrue(AudioEngineProfileStore(context).save(previous))
    }

    @Test fun mainEditorHasOnlySupportedInputAndKeepsParakeetInAdvanced() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertEquals(activity.getString(R.string.mv_title), activity.toolbarTitle())
                assertEquals(View.GONE, root.findViewWithTag<View>("model-voice-advanced").visibility)
                assertNotNull(root.findViewWithTag<View>("ANDROID_STT"))
                assertNotNull(root.findViewWithTag<View>("GEMMA_AUDIO_DIRECT"))
                for (tag in listOf("PARAKEET_PL", "PIPER_PL", "KOKORO", "POCKET_TTS"))
                    assertNull(root.findViewWithTag<View>(tag))
                assertNull(root.findViewWithTag<View>("engine-unavailable"))
                root.findViewWithTag<Button>("model-voice-advanced-toggle").performClick()
                assertEquals(View.VISIBLE, root.findViewWithTag<View>("model-voice-advanced").visibility)
                assertNotNull(root.findViewWithTag<View>("parakeet-transfer-card"))
                assertNotNull(root.findViewWithTag<View>("model-import-user"))
            }
        }
    }

    @Test fun missingGemmaDoesNotPreventSavingAChoiceOrClaimReadiness() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                root.findViewWithTag<RadioButton>("GEMMA_AUDIO_DIRECT").performClick()
                assertEquals(AudioEngineProfile.STABLE, AudioEngineProfileStore(context).load())
                root.findViewWithTag<Button>("engine-save").performClick()
                assertEquals(SpeechInputId.GEMMA_AUDIO_DIRECT, AudioEngineProfileStore(context).load()?.input)
                assertTrue(root.findViewWithTag<TextView>("engine-feedback").text.toString()
                    .contains(activity.getString(R.string.mv_saved)))
                assertFalse(AudioEngineReadinessProbe.check(context, AudioEngineProfileStore(context).load()!!).available)
                assertTrue(root.findViewWithTag<TextView>("model-state:GEMMA_4_E2B").text.toString()
                    .contains(activity.getString(R.string.models_ui_not_installed)))
            }
        }
    }

    @Test fun existingParakeetChoiceStaysVisibleWithoutAddingAMainOption() {
        val requested = AudioEngineProfile(input = SpeechInputId.PARAKEET_PL)
        assertTrue(AudioEngineProfileStore(context).save(requested))
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNull(root.findViewWithTag<View>("PARAKEET_PL"))
                assertEquals(View.VISIBLE, root.findViewWithTag<View>("model-voice-advanced-input").visibility)
                assertEquals(View.GONE, root.findViewWithTag<View>("model-voice-advanced").visibility)
                assertEquals(requested, AudioEngineProfileStore(context).load())
            }
        }
    }

    @Test fun draftAndAdvancedExpansionSurviveRotationWithoutWritingTheProfile() {
        ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                root.findViewWithTag<RadioButton>("GEMMA_AUDIO_DIRECT").performClick()
                root.findViewWithTag<Button>("model-voice-advanced-toggle").performClick()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(root.findViewWithTag<RadioButton>("GEMMA_AUDIO_DIRECT").isChecked)
                assertEquals(View.VISIBLE, root.findViewWithTag<View>("model-voice-advanced").visibility)
                assertEquals(AudioEngineProfile.STABLE, AudioEngineProfileStore(context).load())
            }
        }
    }

    @Test fun backOffersStayAndDiscardForTheSameUnsavedDraft() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<RadioButton>("GEMMA_AUDIO_DIRECT").performClick()
                activity.onBackPressedDispatcher.onBackPressed()
                val dialog = WindowInspector.getGlobalWindowViews().last { it.findViewById<View>(android.R.id.button1) != null }
                dialog.findViewById<Button>(android.R.id.button2).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.findViewWithTag<RadioButton>("GEMMA_AUDIO_DIRECT").isChecked)
                activity.onBackPressedDispatcher.onBackPressed()
                val dialog = WindowInspector.getGlobalWindowViews().last { it.findViewById<View>(android.R.id.button1) != null }
                dialog.findViewById<Button>(android.R.id.button1).performClick()
            }
            instrumentation.waitForIdleSync()
            assertEquals(AudioEngineProfile.STABLE, AudioEngineProfileStore(context).load())
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
