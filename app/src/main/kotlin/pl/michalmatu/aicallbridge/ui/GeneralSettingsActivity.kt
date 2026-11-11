package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.runtime.CallAudioMode
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

/** Config-only screen. UI selections delegate to the existing preference contract. */
class GeneralSettingsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.ui_basic)
    private lateinit var controller: GeneralSettingsController
    private lateinit var modePicker: Spinner
    private lateinit var llmPicker: Spinner
    private lateinit var feedback: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = GeneralSettingsController(this)
        val initial = controller.current()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 24),
                UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 28))
        }
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
            contentDescription = getString(R.string.ui_audio_mode)
            UiSelect.setOptions(this, CallAudioMode.entries.map { it.displayName })
            setSelection(CallAudioMode.entries.indexOf(initial.audioMode))
        }
        content.addView(modePicker)
        content.addView(label(getString(R.string.ui_text_model)))
        llmPicker = Spinner(this).apply {
            tag = "settings-provider-spinner"
            contentDescription = getString(R.string.ui_text_model)
            UiSelect.setOptions(this, TextLlmProvider.entries.map { it.displayName })
            setSelection(TextLlmProvider.entries.indexOf(initial.textLlmProvider))
            isEnabled = initial.audioMode.usesTextLlm
        }
        content.addView(llmPicker)
        feedback = TextView(this).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextColor(UiDesign.secondary)
            textSize = 13f
            setPadding(0, UiDesign.dp(this@GeneralSettingsActivity, 12),
                0, UiDesign.dp(this@GeneralSettingsActivity, 10))
        }
        content.addView(feedback)
        content.addView(UiDesign.caption(this, getString(R.string.ui_general_autosave)), UiDesign.spaceAfter(this, 12))
        modePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = CallAudioMode.entries[position]
                controller.selectMode(selected)
                llmPicker.isEnabled = selected.usesTextLlm
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        llmPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = TextLlmProvider.entries[position]
                controller.selectTextModel(selected)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        val permission = UiDesign.button(this, getString(R.string.ui_microphone)) { requestMicrophone() }
        content.addView(permission, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = UiDesign.dp(this@GeneralSettingsActivity, 18) })
        updateMicrophoneStatus()
        setContentView(ScrollView(this).apply { addView(content) })
    }
    override fun onResume() {
        super.onResume()
        if (::feedback.isInitialized) updateMicrophoneStatus()
    }
    private fun updateMicrophoneStatus() {
        feedback.text = getString(if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) R.string.ui_general_mic_granted
            else R.string.ui_general_mic_missing)
    }
    private fun requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            updateMicrophoneStatus()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) feedback.text = getString(
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                R.string.ui_general_mic_granted else R.string.ui_general_mic_denied)
    }
    private companion object { const val REQUEST_MIC = 1001 }
}
