package pl.michalmatu.aicallbridge.debug

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.UiAccordionSection
import pl.michalmatu.aicallbridge.R
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Visible owner-facing entry for the same module used from Mac ADB. No second state store. */
class UsbDebugPanelActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.usb_ui_title)
    private lateinit var state: TextView
    private val statusValues = linkedMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@UsbDebugPanelActivity, 32), UiDesign.dp(this@UsbDebugPanelActivity, 36), UiDesign.dp(this@UsbDebugPanelActivity, 32), UiDesign.dp(this@UsbDebugPanelActivity, 36))
        }
        fun label(value: String, size: Float = 15f) {
            content.addView(TextView(this).apply {
                text = value
                textSize = size
                setPadding(0, UiDesign.dp(this@UsbDebugPanelActivity, 8), 0, UiDesign.dp(this@UsbDebugPanelActivity, 12))
            })
        }
        fun button(value: String, action: () -> Unit) {
            content.addView(UiDesign.button(this, value, action), UiDesign.spaceAfter(this, 12))
        }

        label(getString(R.string.usb_ui_title), 22f)
        val overview = UiDesign.card(this).apply {
            tag = "usb-status-summary"
        }
        fun item(key: String, title: Int) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, UiDesign.dp(this@UsbDebugPanelActivity, 7),
                    0, UiDesign.dp(this@UsbDebugPanelActivity, 7))
            }
            line.addView(TextView(this).apply {
                text = getString(title)
                textSize = 14f
                setTextColor(UiDesign.secondary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val value = TextView(this).apply {
                textSize = 15f
                setTextColor(UiDesign.ink)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.END
                tag = "usb-value:" + key
            }
            statusValues[key] = value
            line.addView(value, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            overview.addView(line)
        }
        item("phone", R.string.ux07_usb_phone)
        item("campaign", R.string.ux07_usb_campaign)
        item("input", R.string.ux07_usb_input)
        item("dialogue", R.string.ux07_usb_dialogue)
        item("output", R.string.ux07_usb_output)
        item("recording", R.string.ux08_usb_recording_future)
        item("recording-current", R.string.ux08_usb_recording_current)
        item("incoming", R.string.ux07_usb_incoming)
        /* Original overview used one long multi-line TextView. Kept data, not the string blob. */
        content.addView(overview, UiDesign.spaceAfter(this, 16))
        content.addView(UiDesign.caption(this, getString(R.string.usb_ui_description)),
            UiDesign.spaceAfter(this, 12))
        button(getString(R.string.usb_ui_refresh)) { refresh() }
        state = TextView(this).apply {
            textSize = 13f
            setTextColor(UiDesign.secondary)
            setTextIsSelectable(true)
        }
        content.addView(UiAccordionSection(this, "usb-raw",
            getString(R.string.usb_ui_raw_details)).apply {
            addControl(state)
            addControl(UiDesign.button(this@UsbDebugPanelActivity,
                getString(R.string.usb_ui_copy_json)) {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                    ClipData.newPlainText("AI Calls USB status", state.text))
            })
        })
        label(getString(R.string.usb_ui_cli_hint))
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::state.isInitialized) refresh()
    }

    private fun refresh() {
        runCatching {
            val data = UsbDebugBridge.snapshot(this)
            state.text = data.toString(2)
            val phone = if (data.optBoolean("call_idle")) getString(R.string.usb_ui_idle)
                else getString(R.string.usb_ui_not_confirmed_idle)
            val campaign = if (data.optBoolean("campaign_service_inactive"))
                getString(R.string.usb_ui_inactive) else getString(R.string.usb_ui_running_or_unknown)
            val recording = if (data.optBoolean("recording_enabled"))
                getString(R.string.usb_ui_enabled) else getString(R.string.usb_ui_disabled)
            fun status(key: String, text: String) {
                statusValues[key]?.text = text
            }
            status("phone", phone)
            status("campaign", campaign)
            val input = data.optString("input", "UNKNOWN")
            val dialogue = data.optString("dialogue", "UNKNOWN")
            val output = data.optString("output", "UNKNOWN")
            status("input", pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry.inputs
                .firstOrNull { it.id == input }?.title ?: input)
            status("dialogue", pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry.dialogues
                .firstOrNull { it.id == dialogue }?.title ?: dialogue)
            status("output", pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry.outputs
                .firstOrNull { it.id == output }?.title ?: output)
            status("recording", recording)
            status("recording-current", getString(if (data.optBoolean("call_idle"))
                R.string.ux08_usb_no_call else R.string.ux08_usb_recording_unconfirmed))
            status("incoming", when (data.optString("incoming_mode", "UNAVAILABLE")) {
                "AI" -> getString(R.string.incoming_ui_mode_ai)
                "MANUAL" -> getString(R.string.incoming_ui_mode_manual)
                else -> getString(R.string.ux07_not_configured)
            })
        }.onFailure {
            state.text = getString(R.string.usb_ui_read_failed)
            statusValues.values.forEach {
                it.text = getString(R.string.usb_ui_read_failed)
            }
        }
    }
}
