package pl.michalmatu.aicallbridge.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.CapabilityProbe
import pl.michalmatu.aicallbridge.R

/** Read-only, single-card view. The legacy probe still writes its private ADB report. */
class CapabilityDiagnosticsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.dev_ui_capability)
    private val worker = Executors.newSingleThreadExecutor()
    private val values = linkedMapOf<String, TextView>()
    private lateinit var state: TextView
    private lateinit var routes: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = UiDesign.dp(this@CapabilityDiagnosticsActivity, 20)
            setPadding(inset, inset, inset, inset)
        }
        val card = UiDesign.card(this).apply { tag = "capabilities-card" }
        fun group(title: Int) {
            card.addView(UiDesign.heading(this, getString(title)).apply {
                textSize = 16f
                setPadding(0, UiDesign.dp(this@CapabilityDiagnosticsActivity, 12),
                    0, UiDesign.dp(this@CapabilityDiagnosticsActivity, 4))
            })
        }
        fun row(key: String, label: String) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, UiDesign.dp(this@CapabilityDiagnosticsActivity, 6),
                    0, UiDesign.dp(this@CapabilityDiagnosticsActivity, 6))
            }
            line.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(UiDesign.secondary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val value = TextView(this).apply {
                textSize = 14f
                setTextColor(UiDesign.ink)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
                setTextIsSelectable(true)
                tag = "capabilities-value:" + key
                text = getString(R.string.cap_ui_unknown)
            }
            values[key] = value
            line.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(line)
        }
        group(R.string.cap_ui_device)
        row("model", getString(R.string.cap_ui_model))
        row("android", getString(R.string.cap_ui_system))

        group(R.string.cap_ui_audio)
        row("permission_record_audio", getString(R.string.cap_ui_mic))
        row("audio_mode", getString(R.string.cap_ui_mode))
        row("microphone_muted", getString(R.string.cap_ui_muted))
        row("speakerphone_on", getString(R.string.cap_ui_speaker))
        row("output_sample_rate", getString(R.string.cap_ui_rate))
        row("type_telephony_present", getString(R.string.cap_ui_telephony))
        row("communication_device", getString(R.string.cap_ui_communication))

        group(R.string.cap_ui_sources)
        for (source in listOf("MIC", "VOICE_COMMUNICATION", "VOICE_RECOGNITION",
            "VOICE_CALL", "VOICE_DOWNLINK", "VOICE_UPLINK")) {
            row("audio_source_" + source, source.replace('_', ' '))
        }

        group(R.string.cap_ui_routes)
        routes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "capabilities-routes"
        }
        card.addView(routes)
        state = UiDesign.caption(this, getString(R.string.cap_ui_running)).apply {
            tag = "capabilities-probe-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        card.addView(state)
        content.addView(card)
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })

        worker.execute {
            val result = runCatching { CapabilityProbe(applicationContext).run() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = { render(CapabilityReportParser.read(it)) },
                    onFailure = { state.setText(R.string.cap_ui_failed) },
                )
            }
        }
    }

    private fun render(data: Map<String, String>) {
        fun display(key: String) = data[key] ?: getString(R.string.cap_ui_unknown)
        fun yesNo(key: String) = when (data[key]) {
            "true" -> getString(R.string.cap_ui_yes)
            "false" -> getString(R.string.cap_ui_no)
            else -> getString(R.string.cap_ui_unknown)
        }
        fun grant(key: String) = when (data[key]) {
            "granted" -> getString(R.string.cap_ui_granted)
            "denied" -> getString(R.string.cap_ui_denied)
            else -> getString(R.string.cap_ui_unknown)
        }
        val formatted = mapOf(
            "model" to listOf(data["manufacturer"], data["model"])
                .filterNot { it.isNullOrBlank() }.joinToString(" "),
            "android" to (display("android_release") + " · API " + display("sdk")),
            "permission_record_audio" to grant("permission_record_audio"),
            "audio_mode" to display("audio_mode").substringBefore('('),
            "microphone_muted" to yesNo("microphone_muted"),
            "speakerphone_on" to yesNo("speakerphone_on"),
            "output_sample_rate" to display("output_sample_rate") + " Hz",
            "type_telephony_present" to yesNo("type_telephony_present"),
            "communication_device" to data["communication_device"]?.let {
                if (it == "none") getString(R.string.cap_ui_unavailable)
                else CapabilityReportParser.deviceType(it)
            }.orEmpty(),
        )
        for ((key, view) in values) {
            val parsed = if (key.startsWith("audio_source_")) {
                when (val state = CapabilityReportParser.audioSource(data[key])) {
                    "initialized" -> getString(R.string.cap_ui_initialized)
                    "uninitialized" -> getString(R.string.cap_ui_uninitialized)
                    "unknown" -> getString(R.string.cap_ui_unknown)
                    else -> state
                }
            } else formatted[key].orEmpty().ifBlank { getString(R.string.cap_ui_unknown) }
            view.text = parsed
        }
        routes.removeAllViews()
        val devices = data.entries.filter { it.key.startsWith("audio_device[") }
            .sortedBy { it.key.substringAfter('[').substringBefore(']').toIntOrNull() ?: Int.MAX_VALUE }
        for (device in devices) {
            val line = TextView(this).apply {
                text = CapabilityReportParser.deviceType(device.value) + " · " +
                    CapabilityReportParser.deviceProduct(device.value)
                textSize = 13f
                setTextColor(UiDesign.ink)
                setPadding(0, UiDesign.dp(this@CapabilityDiagnosticsActivity, 5), 0,
                    UiDesign.dp(this@CapabilityDiagnosticsActivity, 5))
                tag = "capabilities-route"
            }
            routes.addView(line)
        }
        if (devices.isEmpty()) routes.addView(UiDesign.caption(this, getString(R.string.cap_ui_unavailable)))
        state.visibility = View.GONE
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
