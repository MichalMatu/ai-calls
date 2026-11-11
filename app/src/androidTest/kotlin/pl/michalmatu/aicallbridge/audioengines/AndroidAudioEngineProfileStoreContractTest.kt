package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated preference namespace: never alters the real user's profile, grant, or prompt skills. */
@RunWith(AndroidJUnit4::class)
class AndroidAudioEngineProfileStoreContractTest {
    private val name = "audio_engine_profile_instrumentation_test"
    private lateinit var ctx: Context
    private lateinit var store: AudioEngineProfileStore

    @Before fun setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        store = AudioEngineProfileStore(ctx, name)
    }

    @After fun tearDown() {
        ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun missingPreferenceMigratesToStableShippedProfile() {
        assertEquals(AudioEngineProfile.STABLE, store.load())
        assertTrue(store.save(AudioEngineProfile.STABLE))
        assertEquals(AudioEngineProfile.STABLE, AudioEngineProfileStore(ctx, name).load())
    }

    @Test fun exactOfflineVoiceSurvivesRestart() {
        val expected = AudioEngineProfile(voiceId = "pl-local-test-voice")
        assertTrue(store.save(expected))
        assertEquals(expected, AudioEngineProfileStore(ctx, name).load())
        assertNull(AudioEngineRegistry.incompatibility(expected))
    }

    @Test fun unknownAndPartiallyWrittenProfilesAreRejectedNotDowngraded() {
        val prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().putString("input", "UNKNOWN_ENGINE").commit()
        assertNull(store.load())
        prefs.edit().putInt("version", 1).putString("dialogue", "GEMMA_4_E2B")
            .putString("output", "ANDROID_TTS_PL").putString("language", "pl-PL").commit()
        assertNull(store.load())
    }

    @Test fun legacyPiperMigrationPreservesTheExactEngineAndVoice() {
        val prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().putInt("version", 1).putString("input", "PARAKEET_PL")
            .putString("dialogue", "GEMMA_4_E2B").putString("output", "PIPER_PL")
            .putString("language", "pl-PL").putString("voice", "pl-local-test").commit()
        val expected = AudioEngineProfile(input = SpeechInputId.PARAKEET_PL,
            voiceId = "pl-local-test", ttsEnginePackage = PiperServiceContract.PACKAGE)
        assertEquals(expected, store.load())
        assertEquals(2, prefs.getInt("version", -1))
        assertEquals("ANDROID_TTS_PL", prefs.getString("output", null))
        assertEquals(expected, AudioEngineProfileStore(ctx, name).load())
    }

    @Test fun customModelAndInstalledEnginePersistWithoutAFileReadinessClaim() {
        val expected = AudioEngineProfile(dialogue = DialogueModelId.USER_LITERT,
            customModelId = "user-" + "b".repeat(64), voiceId = "pl-local-test",
            ttsEnginePackage = "example.local.tts")
        assertTrue(store.save(expected))
        assertEquals(expected, store.load())
        assertFalse(AudioEngineReadinessProbe.check(ctx, expected).available)
    }

    @Test fun integratedParakeetPreferencePersistsButDoesNotClaimInstalledModel() {
        val requested = AudioEngineProfile(input = SpeechInputId.PARAKEET_PL)
        assertTrue(store.save(requested))
        assertEquals(requested, AudioEngineProfileStore(ctx, name).load())
        // Persistence is a technical selection, not proof that 615MB of model data exists.
    }

    @Test fun savedAudioChoiceDoesNotClaimNativeReadiness() {
        val requested = AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT)
        assertTrue(store.save(requested))
        assertEquals(requested, store.load())
        assertFalse(AudioEngineReadinessProbe.check(ctx, requested).available)
    }
}
