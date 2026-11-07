package pl.michalmatu.aicallbridge.ui

import android.content.Context
import pl.michalmatu.aicallbridge.runtime.CallAudioMode
import pl.michalmatu.aicallbridge.runtime.CallRuntimePreferences
import pl.michalmatu.aicallbridge.runtime.CallRuntimeSelection
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

/**
 * Configuration adapter: widgets never own a persistence key or call runtime.
 * The original store remains the source of truth; writes affect future sessions only.
 */
internal class GeneralSettingsController(context: Context) {
    private val store = CallRuntimePreferences(context.applicationContext)

    fun current(): CallRuntimeSelection = store.load()

    fun selectMode(value: CallAudioMode) {
        if (current().audioMode != value) store.saveAudioMode(value)
    }

    fun selectTextModel(value: TextLlmProvider) {
        if (current().textLlmProvider != value) store.saveTextLlmProvider(value)
    }
}
