package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.ScrollView
import pl.michalmatu.aicallbridge.R

/** Bounded diagnostic result window. Raw protocol is selectable, explicitly expanded only. */
internal class DiagnosticResultDialog(private val activity: Activity) {
    private val content = UiDesign.content(activity)
    private val message = UiDesign.heading(activity, "").apply {
        textSize = 17f
        tag = "diagnostic-result-summary"
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val checks = UiDesign.content(activity).apply { setPadding(0, 0, 0, 0) }
    private val scope = UiDesign.caption(activity, activity.getString(R.string.shizuku_scope))
    private val raw = UiDesign.caption(activity, "").apply {
        tag = "diagnostic-result-raw"
        setTextIsSelectable(true)
        visibility = View.GONE
    }
    private val details: android.widget.Button = UiDesign.action(activity, activity.getString(R.string.diag_details)) {
        raw.visibility = if (raw.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        updateDetailsLabel()
    }.apply { tag = "diagnostic-result-details"; visibility = View.GONE }
    private val dialog: AlertDialog

    init {
        content.addView(message, UiDesign.spaceAfter(activity))
        content.addView(checks, UiDesign.spaceAfter(activity))
        content.addView(scope, UiDesign.spaceAfter(activity))
        content.addView(details, UiDesign.spaceAfter(activity))
        content.addView(raw)
        dialog = AlertDialog.Builder(activity).setTitle(R.string.shizuku_result_title)
            .setView(ScrollView(activity).apply { addView(content) })
            .setPositiveButton(android.R.string.ok, null).create()
    }

    fun showStatus(text: String) {
        message.text = text
        checks.removeAllViews()
        raw.text = ""
        raw.visibility = View.GONE
        details.visibility = View.GONE
        if (!activity.isFinishing && !activity.isDestroyed) dialog.show()
    }

    fun showResult(report: String): String {
        val parsed = ShizukuProbePresentation.parse(report)
        val title = when (parsed.outcome) {
            ShizukuProbePresentation.Outcome.PASSED -> R.string.shizuku_pass
            ShizukuProbePresentation.Outcome.FAILED -> R.string.shizuku_fail
            ShizukuProbePresentation.Outcome.INCOMPLETE -> R.string.shizuku_incomplete
        }
        message.setText(title)
        checks.removeAllViews()
        listOf(R.string.shizuku_connection to parsed.connection,
            R.string.shizuku_identity to parsed.privileges,
            R.string.shizuku_prepare to parsed.lifecycle).forEach { (label, passed) ->
            checks.addView(UiDesign.caption(activity, activity.getString(label) + ": " +
                activity.getString(when (passed) {
                    true -> R.string.diag_pass
                    false -> R.string.diag_fail
                    null -> R.string.diag_unknown
                })), UiDesign.spaceAfter(activity, 8))
        }
        raw.text = report
        raw.visibility = View.GONE
        details.visibility = View.VISIBLE
        updateDetailsLabel()
        // A dismissed result window stays dismissed when a delayed callback arrives.
        return activity.getString(title)
    }

    private fun updateDetailsLabel() = details.setText(if (raw.visibility == View.VISIBLE)
        R.string.diag_hide_details else R.string.diag_details)

    fun dismiss() = dialog.dismiss()

    companion object {
        fun showDetails(activity: Activity, title: String, report: String) {
            AlertDialog.Builder(activity).setTitle(title).setView(ScrollView(activity).apply {
                addView(UiDesign.content(activity).apply {
                    addView(UiDesign.caption(activity, report).apply { setTextIsSelectable(true) })
                })
            }).setPositiveButton(android.R.string.ok, null).show()
        }
    }
}
