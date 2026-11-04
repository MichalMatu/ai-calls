package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer
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
        AudioEngineRegistry.incompatibility(profile)?.let {
            return AudioEngineReadiness(false, it)
        }
        if (Build.VERSION.SDK_INT < 34 ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) return AudioEngineReadiness(false, "android_offline_stt_unavailable")
        val model = Gemma4LiteRtTextBackendFactory.modelFile(context)
        if (Gemma4ModelReadinessProbe(checkNotNull(model.parentFile)).check().state !=
            Gemma4ModelReadinessState.READY
        ) return AudioEngineReadiness(false, "e2b_model_missing_or_invalid")
        val voices = probedOfflineVoiceNames
            ?: return AudioEngineReadiness(false, "tts_offline_voice_probe_required")
        if (voices.isEmpty()) return AudioEngineReadiness(false, "polish_offline_voice_missing")
        if (profile.voiceId != null && profile.voiceId !in voices) {
            return AudioEngineReadiness(false, "selected_voice_missing")
        }
        return AudioEngineReadiness(true, null, voices)
    }
}
