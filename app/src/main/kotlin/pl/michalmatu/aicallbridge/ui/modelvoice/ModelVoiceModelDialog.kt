package pl.michalmatu.aicallbridge.ui.modelvoice

import android.app.AlertDialog
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceModelPickerPresenter
import pl.michalmatu.aicallbridge.ui.UiChromeActivity

/** Android-only model choice list. No native probe, preferences or model transfer here. */
internal class ModelVoiceModelDialog(
    private val activity: UiChromeActivity,
    private val picker: ModelVoiceModelPickerPresenter.Options,
    private val selectModel: (DialogueModelId, String?) -> Boolean,
    private val manageFiles: () -> Unit,
) {
    fun show() {
        AlertDialog.Builder(activity).setTitle(R.string.models_ui_dialogue_heading)
            .setSingleChoiceItems(picker.rows.map { it.title }.toTypedArray(), picker.selected) { dialog, position ->
                val choice = picker.rows[position]
                val saved = selectModel(choice.dialogue, choice.customId)
                if (saved) dialog.dismiss()
                else (dialog as AlertDialog).listView.setItemChecked(picker.selected, true)
            }
            .setNeutralButton(R.string.mv_manage_files) { _, _ -> manageFiles() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
}
