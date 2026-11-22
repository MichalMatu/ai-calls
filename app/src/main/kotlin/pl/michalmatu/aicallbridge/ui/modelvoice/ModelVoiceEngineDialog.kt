package pl.michalmatu.aicallbridge.ui.modelvoice

import android.app.AlertDialog
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceEnginePickerPresenter
import pl.michalmatu.aicallbridge.ui.UiChromeActivity

/** Only renders the available engine choices; profile validation and TTS remain with its owner. */
internal class ModelVoiceEngineDialog(
    private val activity: UiChromeActivity,
    private val options: ModelVoiceEnginePickerPresenter.Options,
    private val onSelectEngine: (String?) -> Boolean,
    private val onReturnToVoices: () -> Unit,
) {
    fun show() {
        AlertDialog.Builder(activity).setTitle(R.string.mv_engine)
            .setSingleChoiceItems(options.labels.toTypedArray(), options.selected) { dialog, position ->
                if (onSelectEngine(options.ids[position])) {
                    dialog.dismiss()
                    onReturnToVoices()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onReturnToVoices() }
            .show()
    }
}
