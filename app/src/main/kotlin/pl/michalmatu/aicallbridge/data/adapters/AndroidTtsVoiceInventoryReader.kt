package pl.michalmatu.aicallbridge.data.adapters

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import pl.michalmatu.aicallbridge.audioengines.ModelVoiceTtsInventoryPolicy
import pl.michalmatu.aicallbridge.audioengines.PiperServiceContract

/**
 * Android TTS inventory is infrastructure, not presentation.
 *
 * The caller owns the engine lifecycle, worker and generation fence. Invoke read()
 * off the UI thread; it never starts playback, selects a voice or opens a dialog.
 * Preserve the explicit-engine availability gate before listing offline voices.
 */
internal object AndroidTtsVoiceInventoryReader {
    data class Snapshot(
        val installedEngines: List<TextToSpeech.EngineInfo>,
        val voices: List<Voice>,
        val defaultEngine: String?,
    )

    fun read(engine: TextToSpeech, selectedEngine: String?, language: String): Snapshot {
        val installed = engine.engines
        val voices = ModelVoiceTtsInventoryPolicy.availableOffline(
            installedEngines = installed.map { it.name },
            selectedEngine = selectedEngine,
            voices = engine.voices.orEmpty(),
            language = language,
            piperRequired = selectedEngine == PiperServiceContract.PACKAGE,
            languageOf = { it.locale.language },
            networkRequired = { it.isNetworkConnectionRequired },
            piperCapable = { "piperVoice" in it.features },
            nameOf = { it.name },
        )
        return Snapshot(installed, voices, engine.defaultEngine)
    }
}
