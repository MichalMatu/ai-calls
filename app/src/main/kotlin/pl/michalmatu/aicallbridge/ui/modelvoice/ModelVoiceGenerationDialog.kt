package pl.michalmatu.aicallbridge.ui.modelvoice

import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.DialogueGenerationInputParser
import pl.michalmatu.aicallbridge.audioengines.DialogueGenerationSettings
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/**
 * Android-only generation settings editor.
 * The owner provides the current value and an explicit save callback; no model runtime,
 * preferences, verification, telephony or activity state is owned by this view.
 */
internal class ModelVoiceGenerationDialog(
    private val activity: UiChromeActivity,
    private val current: DialogueGenerationSettings,
    private val saveGeneration: (DialogueGenerationSettings) -> Boolean,
) {
    fun show() {
        val fields = UiDesign.content(activity)
        fields.addView(UiDesign.caption(activity, activity.getString(R.string.model_generation_hint)),
            UiDesign.spaceAfter(activity, 12))
        fun field(label: Int, initial: Number?, range: String, decimal: Boolean = false): EditText {
            fields.addView(UiDesign.caption(activity, activity.getString(label)))
            return EditText(activity).apply {
                tag = activity.resources.getResourceEntryName(label)
                contentDescription = activity.getString(label)
                inputType = InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                setSingleLine(true)
                setText(initial?.toString().orEmpty())
                hint = range
                fields.addView(this, UiDesign.spaceAfter(activity, 8))
            }
        }
        fun range(lower: Number, upper: Number) = activity.getString(
            R.string.model_generation_range, lower.toString(), upper.toString())
        fun automatic(lower: Number, upper: Number) = activity.getString(
            R.string.model_generation_auto_range, lower.toString(), upper.toString())
        val context = field(R.string.model_generation_context, current.contextTokens, automatic(2048, 8192))
        val output = field(R.string.model_generation_output, current.outputTokens, automatic(256, 1024))
        val temperature = field(R.string.model_generation_temperature, current.temperature,
            range(0, 1.5), decimal = true)
        val topK = field(R.string.model_generation_top_k, current.topK, range(1, 64))
        val topP = field(R.string.model_generation_top_p, current.topP, range(0.05, 1), decimal = true)
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.model_generation_title)
            .setView(ScrollView(activity).apply { addView(fields) })
            .setPositiveButton(R.string.prep_ui_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.model_settings_defaults, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (saveGeneration(DialogueGenerationSettings.DEFAULTS)) dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val next = DialogueGenerationInputParser.parse(
                    context.text.toString(), output.text.toString(), temperature.text.toString(),
                    topK.text.toString(), topP.text.toString())
                if (next == null) {
                    Toast.makeText(activity, R.string.model_generation_invalid, Toast.LENGTH_SHORT).show()
                } else if (saveGeneration(next)) dialog.dismiss()
            }
        }
        dialog.show()
    }
}
