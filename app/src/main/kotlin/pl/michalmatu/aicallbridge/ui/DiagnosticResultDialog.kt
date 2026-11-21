package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.ScrollView

/** Static, read-only diagnostic details with selectable contents. */
internal class DiagnosticResultDialog private constructor() {
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
