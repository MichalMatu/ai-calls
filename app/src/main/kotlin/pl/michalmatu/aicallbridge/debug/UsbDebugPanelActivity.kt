package pl.michalmatu.aicallbridge.debug

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.UiAccordionSection
import pl.michalmatu.aicallbridge.R
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity

/** Visible owner-facing entry for the same module used from Mac ADB. No second state store. */
class UsbDebugPanelActivity : UiChromeActivity(AppTab.SETTINGS) {
    private lateinit var state: TextView
    private lateinit var overview: TextView

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
        label(getString(R.string.usb_ui_description))
        overview = TextView(this).apply {
            textSize = 15f
            setTextColor(UiDesign.ink)
            setPadding(UiDesign.dp(this@UsbDebugPanelActivity, 14),
                UiDesign.dp(this@UsbDebugPanelActivity, 14),
                UiDesign.dp(this@UsbDebugPanelActivity, 14),
                UiDesign.dp(this@UsbDebugPanelActivity, 14))
            background = UiDesign.surfaceDrawable(this@UsbDebugPanelActivity)
            tag = "usb-status-summary"
        }
        content.addView(overview, UiDesign.spaceAfter(this, 16))
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
        button(getString(R.string.usb_ui_engines)) {
            startActivity(Intent(this, AudioEngineSettingsActivity::class.java))
        }
        button(getString(R.string.usb_ui_campaign)) {
            startActivity(Intent(this, CampaignToolActivity::class.java))
        }
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
            overview.text = getString(R.string.usb_ui_summary, phone, campaign,
                data.optString("input", "UNKNOWN"), data.optString("dialogue", "UNKNOWN"),
                data.optString("output", "UNKNOWN"), recording,
                data.optString("incoming_mode", "UNAVAILABLE"))
        }.onFailure {
            state.text = getString(R.string.usb_ui_read_failed)
            overview.text = getString(R.string.usb_ui_read_failed)
        }
    }
}
