package pl.michalmatu.aicallbridge.session

import android.content.Context
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState

/**
 * Read-only readiness of the model file in the *separate S8 app sandbox*.
 * Does not hash, download, initialize, load, prewarm, or run the LLM.
 * Never leaks a file path or model content to UI/diagnostics.
 */
internal object S8LabModelReadiness {
    enum class State {
        PROFILE_INVALID, MISSING, INVALID, FILE_PRESENT_UNVERIFIED_INFERENCE
    }

    fun inspect(context: Context): State {
        val profile = AudioEngineProfileStore(context).load() ?: return State.PROFILE_INVALID
        return try {
            fromFileState(AndroidGemma4ModelReadiness.check(context, profile.dialogue).state)
        } catch (_: Throwable) {
            State.INVALID
        }
    }

    fun fromFileState(state: Gemma4ModelReadinessState): State = when (state) {
        Gemma4ModelReadinessState.MISSING -> State.MISSING
        Gemma4ModelReadinessState.INVALID -> State.INVALID
        Gemma4ModelReadinessState.READY -> State.FILE_PRESENT_UNVERIFIED_INFERENCE
    }
}
