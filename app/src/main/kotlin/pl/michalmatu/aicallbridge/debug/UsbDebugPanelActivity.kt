package pl.michalmatu.aicallbridge.debug

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.UiAccordionSection
import pl.michalmatu.aicallbridge.R
import android.view.View
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Visible owner-facing entry for the same module used from Mac ADB. No second state store. */
class UsbDebugPanelActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.usb_ui_title)
    private lateinit var state: TextView
    private val statusValues = linkedMapOf<String, TextView>()
    private lateinit var technicalFields: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@UsbDebugPanelActivity, 20), UiDesign.dp(this@UsbDebugPanelActivity, 20), UiDesign.dp(this@UsbDebugPanelActivity, 20), UiDesign.dp(this@UsbDebugPanelActivity, 24))
        }
        fun button(value: String, action: () -> Unit) {
            content.addView(UiDesign.button(this, value, action), UiDesign.spaceAfter(this, 12))
        }

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
        item("input", R.string.ux07_usb_input)
        item("dialogue", R.string.ux07_usb_dialogue)
        item("output", R.string.ux07_usb_output)
        item("recording", R.string.ux08_usb_recording_future)
        item("incoming", R.string.ux07_usb_incoming)
        /* Original overview used one long multi-line TextView. Kept data, not the string blob. */
        content.addView(overview, UiDesign.spaceAfter(this, 16))
        technicalFields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "usb-technical-fields"
        }
        button(getString(R.string.usb_ui_refresh)) { refresh() }
        content.addView(UiAccordionSection(this, "usb-technical",
            getString(R.string.usb_ui_formatted_title)).apply {
            addControl(technicalFields)
        }, UiDesign.spaceAfter(this, 12))
        content.addView(UiDesign.caption(this, getString(R.string.usb_ui_description)),
            UiDesign.spaceAfter(this, 12))
        state = TextView(this).apply {
            textSize = 13f
            setTextColor(UiDesign.secondary)
            visibility = View.GONE
        }
        content.addView(state)
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

    private fun renderTechnicalFields(data: org.json.JSONObject) {
        technicalFields.removeAllViews()
        fun group(title: Int, fields: List<Pair<Int, String>>) {
            technicalFields.addView(UiDesign.heading(this, getString(title)).apply {
                textSize = 15f
                setPadding(0, UiDesign.dp(this@UsbDebugPanelActivity, 10), 0,
                    UiDesign.dp(this@UsbDebugPanelActivity, 6))
            })
            fields.forEach { (label, key) ->
                val value = data.opt(key)
                val display = when (value) {
                    null, org.json.JSONObject.NULL -> getString(R.string.usb_ui_unknown)
                    is Boolean -> getString(if (value) R.string.usb_ui_yes else R.string.usb_ui_no)
                    is org.json.JSONObject -> value.toString(2)
                    else -> value.toString()
                }
                val compact = display.length <= 28 && !display.contains('\n')
                val row = LinearLayout(this).apply {
                    orientation = if (compact) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, UiDesign.dp(this@UsbDebugPanelActivity, 5),
                        0, UiDesign.dp(this@UsbDebugPanelActivity, 7))
                }
                val fieldLabel = TextView(this).apply {
                    text = getString(label)
                    textSize = 12f
                    setTextColor(UiDesign.secondary)
                }
                val fieldValue = TextView(this).apply {
                    text = display
                    setTextIsSelectable(true)
                    textSize = 14f
                    setTextColor(UiDesign.ink)
                    gravity = if (compact) android.view.Gravity.END else android.view.Gravity.START
                }
                if (compact) {
                    row.addView(fieldLabel, LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(fieldValue, LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                } else {
                    row.addView(fieldLabel)
                    fieldValue.setPadding(0, UiDesign.dp(this@UsbDebugPanelActivity, 3), 0, 0)
                    row.addView(fieldValue)
                }
                technicalFields.addView(row)
            }
        }
        group(R.string.usb_ui_group_phone, listOf(
            R.string.usb_ui_campaign_inactive_flag to "campaign_service_inactive",
            R.string.usb_ui_pending_retry to "pending_retry",
        ))
        group(R.string.usb_ui_group_engines, listOf(
            R.string.usb_ui_profile_valid to "profile_valid",
            R.string.usb_ui_voice to "voice",
        ))
        group(R.string.usb_ui_group_privacy, listOf(
            R.string.usb_ui_grant_present to "grant_present",
            R.string.usb_ui_grant_revoked to "grant_revoked",
            R.string.usb_ui_evidence_sessions to "private_evidence_sessions",
        ))
        group(R.string.usb_ui_group_incoming, listOf(
            R.string.usb_ui_incoming_status to "incoming_status",
        ))
    }

    private fun refresh() {
        runCatching {
            val data = UsbDebugBridge.snapshot(this)
            state.visibility = View.GONE
            state.text = ""
            renderTechnicalFields(data)
            val phone = if (data.optBoolean("call_idle")) getString(R.string.usb_ui_idle)
                else getString(R.string.usb_ui_not_confirmed_idle)
            val recording = if (data.optBoolean("recording_enabled"))
                getString(R.string.usb_ui_enabled) else getString(R.string.usb_ui_disabled)
            fun status(key: String, text: String) {
                statusValues[key]?.text = text
            }
            status("phone", phone)
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
            status("incoming", when (data.optString("incoming_mode", "UNAVAILABLE")) {
                "AI" -> getString(R.string.incoming_ui_mode_ai)
                "MANUAL" -> getString(R.string.incoming_ui_mode_manual)
                else -> getString(R.string.ux07_not_configured)
            })
        }.onFailure {
            technicalFields.removeAllViews()
            state.text = getString(R.string.usb_ui_read_failed)
            state.visibility = View.VISIBLE
            statusValues.values.forEach {
                it.text = getString(R.string.usb_ui_read_failed)
            }
        }
    }
}
