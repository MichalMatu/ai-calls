package pl.michalmatu.aicallbridge.ui.modelvoice

import android.app.AlertDialog
import android.widget.RadioButton
import android.widget.RadioGroup
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.SpeechInputId
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceSpeechPickerPresenter
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/** Presentation-only choices; the owner retains profile validation and persistence. */
internal class ModelVoiceLanguageDialog(
    private val activity: UiChromeActivity,
    private val selectedLanguage: String,
    private val onSelected: (String) -> Boolean,
) {
    fun show() {
        val values = arrayOf("pl-PL", "en-US")
        val labels = arrayOf(activity.getString(R.string.mv_speech_language_pl),
            activity.getString(R.string.mv_speech_language_en))
        val selected = values.indexOf(selectedLanguage)
        AlertDialog.Builder(activity).setTitle(R.string.mv_speech_language)
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                if (onSelected(values[index])) dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
}

/** The Android radio group only renders typed presenter choices and forwards explicit taps. */
internal class ModelVoiceInputDialog(
    private val activity: UiChromeActivity,
    private val choices: List<ModelVoiceSpeechPickerPresenter.InputChoice>,
    private val titleFor: (SpeechInputId) -> String,
    private val onSelected: (SpeechInputId) -> Boolean,
) {
    fun show() {
        val group = RadioGroup(activity).apply {
            val horizontal = UiDesign.dp(activity, 16)
            val vertical = UiDesign.dp(activity, 8)
            setPadding(horizontal, vertical, horizontal, vertical)
        }
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.mv_input)
            .setView(group).setNegativeButton(android.R.string.cancel, null).create()
        for (choice in choices) {
            if (choice.showAdvancedHeading)
                group.addView(UiDesign.caption(activity, activity.getString(R.string.mv_additional)))
            group.addView(RadioButton(activity).apply {
                text = titleFor(choice.input); tag = choice.input.name
                minimumHeight = UiDesign.dp(activity, 52)
                isChecked = choice.selected
                isEnabled = choice.enabled
                setOnClickListener { if (onSelected(choice.input)) dialog.dismiss() }
            })
        }
        dialog.show()
    }
}
