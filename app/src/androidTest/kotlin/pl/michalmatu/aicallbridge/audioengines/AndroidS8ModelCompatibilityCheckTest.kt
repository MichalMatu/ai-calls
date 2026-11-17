package pl.michalmatu.aicallbridge.audioengines

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelCatalog
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity

/**
 * Samsung-only real private LiteRT model check. Never starts a phone call,
 * reads microphone PCM, changes profile, or prints model output.
 * Permission grant (if needed) is a separate explicit host test action.
 */
@RunWith(AndroidJUnit4::class)
class AndroidS8ModelCompatibilityCheckTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun requireS22WithModel() {
        assumeTrue(Build.MODEL == "SM-S906B")
        val file = Gemma4LiteRtTextBackendFactory.modelFile(context, DialogueModelId.GEMMA_4_E2B)
        assertTrue("e2b_file_missing", file.isFile)
        assertEquals("e2b_size_mismatch",
            Gemma4ModelCatalog.GEMMA_4_E2B_IT.expectedBytes, file.length())
    }

    @Test fun missingPhonePermissionExplainsWhyCheckNeverStarted() {
        requireS22WithModel()
        assumeTrue(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED)
        assumeTrue(AudioEngineProfileStore(context).load()?.dialogue ==
            DialogueModelId.GEMMA_4_E2B)

        val file = Gemma4LiteRtTextBackendFactory.modelFile(context, DialogueModelId.GEMMA_4_E2B)
        val before = ModelCompatibilityStore(context).verified(file, false)
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val button = root.findViewWithTag<Button>("model-voice-compatibility-check")
                val message = root.findViewWithTag<TextView>("model-voice-readiness")
                assertNotNull(button)
                assertTrue(button.isEnabled)
                button.performClick()
                assertEquals(activity.getString(R.string.mv_compatibility_phone_permission),
                    message.text.toString())
            }
        }
        // Blocking on missing permissions must never create or invalidate proof.
        assertEquals(before, ModelCompatibilityStore(context).verified(file, false))
    }

    @Test fun savedCompatibilityRemainsVisibleAfterPhonePermissionRestored() {
        requireS22WithModel()
        assumeTrue(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED)
        assumeTrue(AudioEngineProfileStore(context).load()?.dialogue ==
            DialogueModelId.GEMMA_4_E2B)
        assumeTrue(AudioEngineProfileStore(context).load()?.input !=
            SpeechInputId.GEMMA_AUDIO_DIRECT)
        val file = Gemma4LiteRtTextBackendFactory.modelFile(context, DialogueModelId.GEMMA_4_E2B)
        assertTrue("saved_model_proof_missing",
            ModelCompatibilityStore(context).verified(file, false))

        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.mv_verified_text),
                    activity.window.decorView.findViewWithTag<TextView>(
                        "model-voice-readiness").text.toString())
            }
        }
        // Read-only: no test, permission grant or phone call.
        assertTrue(ModelCompatibilityStore(context).verified(file, false))
    }

    @Test fun actualLocalE2bCompatibilityPassPersistsAndAppearsInUi() {
        requireS22WithModel()
        assumeTrue(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED)
        val current = checkNotNull(AudioEngineProfileStore(context).load())
        val candidate = current.copy(dialogue = DialogueModelId.GEMMA_4_E2B,
            input = SpeechInputId.ANDROID_STT)
        val file = Gemma4LiteRtTextBackendFactory.modelFile(context, DialogueModelId.GEMMA_4_E2B)
        val probe = ModelCompatibilityProbe(context)
        val done = CountDownLatch(1)
        val result = AtomicReference<String?>("model_test_not_completed")
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                probe.check(file, candidate, audio = false) { failure ->
                    result.set(failure)
                    done.countDown()
                }
            }
            assertTrue("e2b_native_test_timeout", done.await(420L, TimeUnit.SECONDS))
            assertNull("e2b_native_compat_failure:" + result.get(), result.get())
            assertTrue("e2b_compat_stamp_missing",
                ModelCompatibilityStore(context).verified(file, false))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { probe.close() }
        }

        // The same persisted result must be rendered after reopening the UI.
        if (current.dialogue == DialogueModelId.GEMMA_4_E2B &&
            current.input != SpeechInputId.GEMMA_AUDIO_DIRECT) {
            ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val text = activity.window.decorView
                        .findViewWithTag<TextView>("model-voice-readiness")
                        .text.toString()
                    assertEquals(activity.getString(R.string.mv_verified_text), text)
                }
            }
        }
        assertEquals("profile_changed", current, AudioEngineProfileStore(context).load())
    }
}
