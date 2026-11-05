package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer
import java.io.File
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessProbe
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState

internal data class AudioEngineReadiness(
    val available: Boolean,
    val reason: String?,
    val offlineVoiceNames: Set<String> = emptySet(),
)

/** No network calls/downloads, no active Call manipulation and no speculative READY. */
internal object AudioEngineReadinessProbe {
    fun check(
        context: Context,
        profile: AudioEngineProfile,
        probedOfflineVoiceNames: Set<String>? = null,
    ): AudioEngineReadiness {
        inputAndModelFailure(context, profile)?.let { return AudioEngineReadiness(false, it) }
        val voices = probedOfflineVoiceNames
            ?: return AudioEngineReadiness(false, "tts_offline_voice_probe_required")
        if (voices.isEmpty()) return AudioEngineReadiness(false, "polish_offline_voice_missing")
        if (profile.voiceId != null && profile.voiceId !in voices) {
            return AudioEngineReadiness(false, "selected_voice_missing")
        }
        return AudioEngineReadiness(true, null, voices)
    }
    /** Shared capability check; callers supply their own voice-probe lifecycle. */
    fun inputAndModelFailure(context: Context, profile: AudioEngineProfile): String? {
        AudioEngineRegistry.incompatibility(profile)?.let {
            return it
        }
        when (profile.input) {
            SpeechInputId.ANDROID_STT -> {
                if (Build.VERSION.SDK_INT < 34 ||
                    !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) return "android_offline_stt_unavailable"
            }
            SpeechInputId.PARAKEET_PL -> {
                if (!ParakeetModelFile.resolve(context).isFile ||
                    ParakeetModelFile.resolve(context).length() < ParakeetModelFile.MIN_SIZE_BYTES
                ) return "parakeet_model_missing_or_incomplete"
            }
            SpeechInputId.GEMMA_AUDIO_DIRECT ->
                return "audio_direct_not_integrated"
        }
        val model = Gemma4LiteRtTextBackendFactory.modelFile(context, profile.dialogue)
        if (Gemma4ModelReadinessProbe(checkNotNull(model.parentFile), pl.michalmatu.aicallbridge.textagent.LanguageModelCatalog[profile.dialogue].spec).check().state !=
            Gemma4ModelReadinessState.READY
        ) return "dialogue_model_missing_or_invalid"
        return null
    }

}
