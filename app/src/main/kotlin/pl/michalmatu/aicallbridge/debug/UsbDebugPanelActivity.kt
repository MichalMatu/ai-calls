package pl.michalmatu.aicallbridge.debug

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/** One read-only card for the local snapshot; the ADB diagnostics protocol is unchanged. */
class UsbDebugPanelActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.usb_ui_title)

    private lateinit var state: TextView
    private val statusValues = linkedMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@UsbDebugPanelActivity, 20),
                UiDesign.dp(this@UsbDebugPanelActivity, 20),
                UiDesign.dp(this@UsbDebugPanelActivity, 20),
                UiDesign.dp(this@UsbDebugPanelActivity, 24))
        }
        val statusCard = UiDesign.card(this).apply {
            tag = "usb-status-summary"
        }
        fun group(title: Int) {
            statusCard.addView(UiDesign.heading(this, getString(title)).apply {
                textSize = 15f
                setPadding(0, UiDesign.dp(this@UsbDebugPanelActivity, 11),
                    0, UiDesign.dp(this@UsbDebugPanelActivity, 6))
            })
        }
        fun item(key: String, label: Int) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, UiDesign.dp(this@UsbDebugPanelActivity, 6),
                    0, UiDesign.dp(this@UsbDebugPanelActivity, 6))
            }
            line.addView(TextView(this).apply {
                text = getString(label)
                textSize = 13f
                setTextColor(UiDesign.secondary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val value = TextView(this).apply {
                textSize = 14f
                setTextColor(UiDesign.ink)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
                setTextIsSelectable(true)
                tag = "usb-value:" + key
            }
            statusValues[key] = value
            line.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            statusCard.addView(line)
        }

        group(R.string.usb_ui_group_phone)
        item("phone", R.string.ux07_usb_phone)
        item("campaign_service_inactive", R.string.usb_ui_campaign_inactive_flag)
        item("pending_retry", R.string.usb_ui_pending_retry)

        group(R.string.usb_ui_group_engines)
        item("input", R.string.ux07_usb_input)
        item("dialogue", R.string.ux07_usb_dialogue)
        item("output", R.string.ux07_usb_output)
        item("tts_engine_package", R.string.ux07_usb_tts_engine)
        item("tts_voice_id", R.string.usb_ui_voice)
        item("profile_valid", R.string.usb_ui_profile_valid)

        group(R.string.usb_ui_group_incoming)
        item("incoming", R.string.ux07_usb_incoming)
        item("incoming_status", R.string.usb_ui_incoming_status)

        group(R.string.usb_ui_group_privacy)
        item("incoming_recording_enabled", R.string.ux07_usb_incoming_recording)
        item("outgoing_recording_enabled", R.string.ux07_usb_outgoing_recording)
        item("grant_present", R.string.usb_ui_grant_present)
        item("grant_revoked", R.string.usb_ui_grant_revoked)
        item("private_evidence_sessions", R.string.usb_ui_evidence_sessions)

        state = UiDesign.caption(this, "").apply {
            tag = "usb-read-error"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        statusCard.addView(state)
        content.addView(statusCard, UiDesign.spaceAfter(this, 12))
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        if (::state.isInitialized) refresh()
    }

    private fun refresh() {
        runCatching {
            val data = UsbDebugBridge.snapshot(this)
            val input = data.optString("input", "UNKNOWN")
            val dialogue = data.optString("dialogue", "UNKNOWN")
            val output = data.optString("output", "UNKNOWN")
            val labels = mapOf(
                "phone" to getString(if (data.optBoolean("call_idle")) R.string.usb_ui_idle
                    else R.string.usb_ui_not_confirmed_idle),
                "input" to (pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry.inputs
                    .firstOrNull { it.id == input }?.title ?: input),
                "dialogue" to (pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry.dialogues
                    .firstOrNull { it.id == dialogue }?.title ?: dialogue),
                "output" to (if (data.optString("tts_engine_package") ==
                        pl.michalmatu.aicallbridge.audioengines.PiperServiceContract.PACKAGE)
                    getString(R.string.ux07_usb_piper_output)
                    else pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry.outputs
                        .firstOrNull { it.id == output }?.title ?: output),
                "incoming" to when (data.optString("incoming_mode", "UNAVAILABLE")) {
                    "AI" -> getString(R.string.incoming_ui_mode_ai)
                    "MANUAL" -> getString(R.string.incoming_ui_mode_manual)
                    else -> getString(R.string.ux07_not_configured)
                },
                "incoming_recording_enabled" to getString(
                    if (data.optBoolean("incoming_recording_enabled"))
                        R.string.usb_ui_enabled else R.string.usb_ui_disabled),
                "outgoing_recording_enabled" to getString(
                    if (data.optBoolean("outgoing_recording_enabled"))
                        R.string.usb_ui_enabled else R.string.usb_ui_disabled)
            )
            for ((key, view) in statusValues) {
                view.text = labels[key] ?: when (val value = data.opt(key)) {
                    null, JSONObject.NULL -> getString(R.string.usb_ui_unknown)
                    is Boolean -> getString(if (value) R.string.usb_ui_yes else R.string.usb_ui_no)
                    else -> value.toString()
                }
            }
            state.text = ""
            state.visibility = View.GONE
        }.onFailure {
            statusValues.values.forEach { it.text = getString(R.string.usb_ui_read_failed) }
            state.text = getString(R.string.usb_ui_read_failed)
            state.visibility = View.VISIBLE
        }
    }
}
