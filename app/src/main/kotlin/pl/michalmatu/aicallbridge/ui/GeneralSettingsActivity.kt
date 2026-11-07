package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.runtime.CallAudioMode
import pl.michalmatu.aicallbridge.runtime.CallRuntimePreferences
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

/** Config-only screen. UI selections delegate to the existing preference contract. */
class GeneralSettingsActivity : Activity() {
    private lateinit var preferences: CallRuntimePreferences
    private lateinit var modePicker: Spinner
    private lateinit var llmPicker: Spinner
    private lateinit var feedback: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = CallRuntimePreferences(this)
        val initial = preferences.load()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 24),
                UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 28))
        }
        content.addView(UiDesign.heading(this, getString(R.string.ui_basic)).apply { textSize = 25f })
        content.addView(TextView(this).apply {
            text = getString(R.string.ui_general_description)
            textSize = 14f
            setTextColor(UiDesign.secondary)
            setPadding(0, 0, 0, UiDesign.dp(this@GeneralSettingsActivity, 18))
        })
        fun label(value: String) = TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(UiDesign.ink)
            setPadding(0, UiDesign.dp(this@GeneralSettingsActivity, 16),
                0, UiDesign.dp(this@GeneralSettingsActivity, 8))
        }
        content.addView(label(getString(R.string.ui_audio_mode)))
        modePicker = Spinner(this).apply {
            tag = "settings-audio-mode-spinner"
            adapter = ArrayAdapter(this@GeneralSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                CallAudioMode.entries.map { it.displayName })
            setSelection(CallAudioMode.entries.indexOf(initial.audioMode))
            background = UiDesign.surfaceDrawable(this@GeneralSettingsActivity, UiDesign.surface, 10)
            minimumHeight = UiDesign.dp(this@GeneralSettingsActivity, 52)
        }
        content.addView(modePicker)
        content.addView(label(getString(R.string.ui_text_model)))
        llmPicker = Spinner(this).apply {
            tag = "settings-provider-spinner"
            adapter = ArrayAdapter(this@GeneralSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                TextLlmProvider.entries.map { it.displayName })
            setSelection(TextLlmProvider.entries.indexOf(initial.textLlmProvider))
            isEnabled = initial.audioMode.usesTextLlm
            background = UiDesign.surfaceDrawable(this@GeneralSettingsActivity, UiDesign.surface, 10)
            minimumHeight = UiDesign.dp(this@GeneralSettingsActivity, 52)
        }
        content.addView(llmPicker)
        feedback = TextView(this).apply {
            setTextColor(UiDesign.secondary)
            textSize = 13f
            setPadding(0, UiDesign.dp(this@GeneralSettingsActivity, 12),
                0, UiDesign.dp(this@GeneralSettingsActivity, 10))
        }
        content.addView(feedback)
        modePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = CallAudioMode.entries[position]
                if (preferences.load().audioMode != selected) preferences.saveAudioMode(selected)
                llmPicker.isEnabled = selected.usesTextLlm
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        llmPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = TextLlmProvider.entries[position]
                if (preferences.load().textLlmProvider != selected) preferences.saveTextLlmProvider(selected)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        val permission = UiDesign.button(this, getString(R.string.ui_microphone)) { requestMicrophone() }
        content.addView(permission, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = UiDesign.dp(this@GeneralSettingsActivity, 18) })
        AppNavigation.install(this, ScrollView(this).apply { addView(content) }, AppTab.SETTINGS)
    }
    private fun requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            feedback.text = getString(R.string.ui_microphone) + ": OK"
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) feedback.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            getString(R.string.ui_microphone) + ": OK" else getString(R.string.ui_microphone) + ": denied"
    }
    private companion object { const val REQUEST_MIC = 1001 }
}
