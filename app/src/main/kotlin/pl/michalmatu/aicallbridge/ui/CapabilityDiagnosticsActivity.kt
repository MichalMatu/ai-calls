package pl.michalmatu.aicallbridge.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.CapabilityProbe
import pl.michalmatu.aicallbridge.R

/** App-process initialization facts, scoped separately from Shizuku and live call audio. */
class CapabilityDiagnosticsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.dev_ui_capability)
    private val worker = Executors.newSingleThreadExecutor()
    private val values = linkedMapOf<String, TextView>()
    private lateinit var state: TextView
    private lateinit var routes: LinearLayout
    private lateinit var details: android.widget.Button
    private var technicalReport = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiDesign.content(this)
        content.addView(UiDesign.caption(this, getString(R.string.cap_scope)), UiDesign.spaceAfter(this))
        var card = UiDesign.card(this).apply { tag = "capabilities-card" }
        content.addView(card)
        fun group(title: Int) {
            if (card.childCount > 0) {
                card = UiDesign.card(this)
                content.addView(card)
            }
            card.addView(UiDesign.heading(this, getString(title)).apply {
                textSize = 16f
                setPadding(0, UiDesign.dp(this@CapabilityDiagnosticsActivity, 12),
                    0, UiDesign.dp(this@CapabilityDiagnosticsActivity, 4))
            })
        }
        fun row(key: String, label: String) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, UiDesign.dp(this@CapabilityDiagnosticsActivity, 6),
                    0, UiDesign.dp(this@CapabilityDiagnosticsActivity, 6))
            }
            line.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(UiDesign.secondary)
            })
            val value = TextView(this).apply {
                textSize = 14f
                setTextColor(UiDesign.ink)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextIsSelectable(true)
                tag = "capabilities-value:" + key
                text = getString(R.string.cap_ui_unknown)
            }
            values[key] = value
            line.addView(value)
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
        card.addView(UiDesign.caption(this, getString(R.string.cap_source_limit)), UiDesign.spaceAfter(this, 8))
        for ((source, label) in CapabilityLabels.sources) row("audio_source_" + source, getString(label))

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
        details = UiDesign.action(this, getString(R.string.diag_details)) {
            DiagnosticResultDialog.showDetails(this, getString(R.string.diag_details), technicalReport)
        }.apply { tag = "capabilities-details"; isEnabled = false }
        content.addView(details)
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })

        worker.execute {
            val result = runCatching { CapabilityProbe(applicationContext).run() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = {
                        technicalReport = it
                        details.isEnabled = true
                        render(CapabilityReportParser.read(it))
                    },
                    onFailure = {
                        technicalReport = it.javaClass.simpleName + ": " + it.message.orEmpty()
                        details.isEnabled = true
                        state.setText(R.string.cap_ui_failed)
                    },
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
            "audio_mode" to getString(CapabilityLabels.mode(data["audio_mode"])),
            "microphone_muted" to yesNo("microphone_muted"),
            "speakerphone_on" to yesNo("speakerphone_on"),
            "output_sample_rate" to data["output_sample_rate"]?.takeIf { it.toIntOrNull()?.let { rate -> rate > 0 } == true }
                ?.let { "$it Hz" }.orEmpty(),
            "type_telephony_present" to yesNo("type_telephony_present"),
            "communication_device" to data["communication_device"]?.let {
                if (it == "none") getString(R.string.cap_ui_unavailable)
                else getString(CapabilityLabels.device(CapabilityReportParser.device(it).type))
            }.orEmpty(),
        )
        for ((key, view) in values) {
            val parsed = if (key.startsWith("audio_source_")) {
                getString(when (CapabilityReportParser.audioSource(data[key])) {
                    CapabilityReportParser.AudioSourceState.INITIALIZED -> R.string.cap_ui_initialized
                    CapabilityReportParser.AudioSourceState.UNINITIALIZED -> R.string.cap_ui_uninitialized
                    CapabilityReportParser.AudioSourceState.ERROR -> R.string.cap_source_error
                    CapabilityReportParser.AudioSourceState.UNKNOWN -> R.string.cap_ui_unknown
                })
            } else formatted[key].orEmpty().ifBlank { getString(R.string.cap_ui_unknown) }
            view.text = parsed
        }
        routes.removeAllViews()
        val devices = data.entries.filter { it.key.startsWith("audio_device[") }
            .sortedBy { it.key.substringAfter('[').substringBefore(']').toIntOrNull() ?: Int.MAX_VALUE }
        val parsedDevices = devices.map { CapabilityReportParser.device(it.value) }
        val groups = listOf(R.string.cap_inputs to parsedDevices.filter { it.direction in setOf("source", "source+sink") },
            R.string.cap_outputs to parsedDevices.filter { it.direction in setOf("sink", "source+sink") },
            R.string.cap_other_devices to parsedDevices.filter { it.direction !in setOf("source", "sink", "source+sink") })
        for ((title, entries) in groups) {
            if (entries.isEmpty()) continue
            routes.addView(UiDesign.heading(this, getString(title)).apply { textSize = 15f },
                UiDesign.spaceAfter(this, 6))
            entries.groupBy { it.type }.forEach { (type, matching) ->
                val label = getString(CapabilityLabels.device(type))
                routes.addView(UiDesign.caption(this, if (matching.size == 1) label
                    else getString(R.string.cap_device_count, label, matching.size)).apply {
                    tag = "capabilities-route"
                }, UiDesign.spaceAfter(this, 8))
            }
        }
        if (devices.isEmpty()) routes.addView(UiDesign.caption(this, getString(R.string.cap_ui_unavailable)))
        state.visibility = View.GONE
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
