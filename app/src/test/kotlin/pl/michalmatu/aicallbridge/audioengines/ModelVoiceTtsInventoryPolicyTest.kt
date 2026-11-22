package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ModelVoiceTtsInventoryPolicyTest {
    private data class VoiceInfo(
        val name: String,
        val language: String = "pl",
        val network: Boolean = false,
        val piper: Boolean = false,
    )

    private val installed = listOf("com.example.tts", PiperServiceContract.PACKAGE)

    private fun available(
        voices: Collection<VoiceInfo>,
        requested: String? = null,
        language: String = "pl",
    ) = ModelVoiceTtsInventoryPolicy.availableOffline(
        installedEngines = installed,
        selectedEngine = requested,
        voices = voices,
        language = language,
        piperRequired = requested == PiperServiceContract.PACKAGE,
        languageOf = { it.language },
        networkRequired = { it.network },
        piperCapable = { it.piper },
        nameOf = { it.name },
    )

    @Test fun filtersOtherLanguagesAndNetworkVoices() {
        val results = available(listOf(
            VoiceInfo("polish-z"),
            VoiceInfo("english", language = "en"),
            VoiceInfo("online", network = true),
            VoiceInfo("polish-a"),
        ))
        assertEquals(listOf("polish-a", "polish-z"), results.map { it.name })
    }

    @Test fun piperRequiresFeatureWithoutChangingOtherEngines() {
        val voices = listOf(VoiceInfo("no-feature"), VoiceInfo("piper-voice", piper = true))
        assertEquals(listOf("piper-voice"),
            available(voices, PiperServiceContract.PACKAGE).map { it.name })
        assertEquals(listOf("no-feature", "piper-voice"),
            available(voices, "com.example.tts").map { it.name })
    }

    @Test fun englishIsAllowedOnlyForMatchingOfflineVoices() {
        val results = available(listOf(VoiceInfo("pl"), VoiceInfo("en-b", "en"),
            VoiceInfo("en-a", "en"), VoiceInfo("en-web", "en", network = true)),
            language = "en")
        assertEquals(listOf("en-a", "en-b"), results.map { it.name })
    }

    @Test fun unavailableSelectedEngineIsRejectedRatherThanFallingBack() {
        try {
            available(listOf(VoiceInfo("some-voice")), requested = "org.example.missing")
            fail("must reject missing explicit engine")
        } catch (_: IllegalArgumentException) {
            // A failed inventory leaves the selected engine unchanged in the saved profile.
        }
    }

    @Test fun emptyInventoryAndSystemEngineAreValid() {
        assertEquals(emptyList<VoiceInfo>(), available(emptyList()))
        assertEquals(listOf("pl"), available(listOf(VoiceInfo("pl"))).map { it.name })
    }

    @Test fun sortingDoesNotMutateTheProvidedCollection() {
        val voices = mutableListOf(VoiceInfo("z"), VoiceInfo("a"))
        assertEquals(listOf("a", "z"), available(voices).map { it.name })
        assertEquals(listOf("z", "a"), voices.map { it.name })
    }

    @Test fun familiarEngineLabelsKeepTheirPreviousSpelling() {
        assertEquals("Google", ModelVoiceTtsInventoryPolicy.displayEngineName(
            "com.google.android.tts", "Android"))
        assertEquals("Samsung", ModelVoiceTtsInventoryPolicy.displayEngineName(
            "com.samsung.SMT", "Samsung TTS Engine"))
        assertEquals("Piper", ModelVoiceTtsInventoryPolicy.displayEngineName(
            PiperServiceContract.PACKAGE, "Piper Service"))
        assertEquals("Supplied", ModelVoiceTtsInventoryPolicy.displayEngineName(
            "org.example.tts", "Supplied"))
    }
}
