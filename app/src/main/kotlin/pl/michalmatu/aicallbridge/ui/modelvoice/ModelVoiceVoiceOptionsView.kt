package pl.michalmatu.aicallbridge.ui.modelvoice

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RadioButton
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceSpeechPickerPresenter
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/**
 * Renders a snapshot of voice choices only. The owner still manages TTS lifecycle,
 * engine changes, profile persistence and off-call preview safety.
 */
internal class ModelVoiceVoiceOptionsView(
    private val activity: UiChromeActivity,
    private val options: LinearLayout,
) {
    fun render(
        picker: ModelVoiceSpeechPickerPresenter.VoiceOptions,
        onChooseEngine: () -> Unit,
        onSelectVoice: (String?) -> Unit,
        onPreview: (String?) -> Unit,
    ) {
        options.removeAllViews()
        options.addView(UiDesign.action(activity,
            activity.getString(R.string.mv_engine_value, picker.engineLabel)) {
            onChooseEngine()
        }, UiDesign.spaceAfter(activity, 12))
        if (picker.loading) {
            options.addView(UiDesign.caption(activity,
                activity.getString(R.string.mv_loading_voices)))
            return
        }
        if (picker.missingVoice == ModelVoiceSpeechPickerPresenter.MissingVoice.PIPER)
            options.addView(UiDesign.caption(activity,
                activity.getString(R.string.mv_piper_voice_missing)))
        else if (picker.missingVoice == ModelVoiceSpeechPickerPresenter.MissingVoice.GENERIC)
            options.addView(UiDesign.caption(activity,
                activity.getString(R.string.mv_voice_missing)))
        picker.rows.forEach { choice ->
            val id = choice.id
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(RadioButton(activity).apply {
                text = if (choice.position == 0) activity.getString(R.string.mv_voice_auto)
                    else activity.getString(R.string.mv_local_voice, choice.position)
                isChecked = choice.selected
                minimumHeight = UiDesign.dp(activity, 52)
                setOnClickListener { onSelectVoice(id) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(UiDesign.action(activity, "▶") { onPreview(id) }.apply {
                contentDescription = activity.getString(R.string.eng_ui_preview)
                setPadding(0, 0, 0, 0)
            }, LinearLayout.LayoutParams(UiDesign.dp(activity, 48), UiDesign.dp(activity, 48)))
            options.addView(row, UiDesign.spaceAfter(activity, 8))
        }
    }
}
