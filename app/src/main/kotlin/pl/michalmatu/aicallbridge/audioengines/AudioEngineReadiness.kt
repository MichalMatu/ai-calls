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
                when (ParakeetModelFile.integrity(ParakeetModelFile.resolve(context))) {
                    ParakeetModelIntegrity.MISSING, ParakeetModelIntegrity.INCOMPLETE ->
                        return "parakeet_model_missing_or_incomplete"
                    ParakeetModelIntegrity.INVALID -> return "parakeet_model_sha256_mismatch"
                    // Native initialization verifies an uncached file on its transcription worker.
                    ParakeetModelIntegrity.UNVERIFIED, ParakeetModelIntegrity.VERIFIED -> Unit
                }
            }
            SpeechInputId.GEMMA_AUDIO_DIRECT -> Unit
        }
        val spec = if (profile.dialogue == DialogueModelId.USER_LITERT) {
            val library = pl.michalmatu.aicallbridge.textagent.UserLiteRtModelStore(context)
            val imported = library.find(profile.customModelId)
                ?: return "user_model_missing"
            pl.michalmatu.aicallbridge.textagent.Gemma4ModelSpec(
                imported.title, imported.filename, imported.sha256, imported.bytes)
        } else pl.michalmatu.aicallbridge.textagent.LanguageModelCatalog[profile.dialogue].spec
        val directory = if (profile.dialogue == DialogueModelId.USER_LITERT) {
            val imported = pl.michalmatu.aicallbridge.textagent.UserLiteRtModelStore(context)
                .find(profile.customModelId) ?: return "user_model_missing"
            checkNotNull(pl.michalmatu.aicallbridge.textagent.UserLiteRtModelStore(context)
                .file(imported).parentFile)
        } else checkNotNull(Gemma4LiteRtTextBackendFactory.modelFile(context, profile.dialogue).parentFile)
        if (Gemma4ModelReadinessProbe(directory, spec).check().state !=
            Gemma4ModelReadinessState.READY
        ) return "dialogue_model_missing_or_invalid"
        val file = Gemma4LiteRtTextBackendFactory.modelFile(context, profile)
        if (profile.dialogue == DialogueModelId.USER_LITERT &&
            !pl.michalmatu.aicallbridge.textagent.LiteRtModelContainer.hasHeader(file)) return "model_container_invalid"
        if (profile.dialogue == DialogueModelId.USER_LITERT &&
            !ModelCompatibilityStore(context).verified(file, false)) return "model_compatibility_required"
        if (profile.input == SpeechInputId.GEMMA_AUDIO_DIRECT &&
            !ModelCompatibilityStore(context).verified(file, true)) return "model_audio_check_required"
        return null
    }

}
