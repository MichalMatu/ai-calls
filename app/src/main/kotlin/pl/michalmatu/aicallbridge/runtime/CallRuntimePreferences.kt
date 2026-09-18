package pl.michalmatu.aicallbridge.runtime

import android.content.Context

class CallRuntimePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): CallRuntimeSelection = CallRuntimeSelection(
        audioMode = CallAudioMode.fromStored(preferences.getString(KEY_AUDIO_MODE, null)),
        textLlmProvider = TextLlmProvider.fromStored(preferences.getString(KEY_TEXT_LLM_PROVIDER, null)),
    )

    fun saveAudioMode(mode: CallAudioMode) {
        preferences.edit().putString(KEY_AUDIO_MODE, mode.name).apply()
    }

    fun saveTextLlmProvider(provider: TextLlmProvider) {
        preferences.edit().putString(KEY_TEXT_LLM_PROVIDER, provider.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "call_runtime_preferences"
        const val KEY_AUDIO_MODE = "audio_mode"
        const val KEY_TEXT_LLM_PROVIDER = "text_llm_provider"
    }
}
