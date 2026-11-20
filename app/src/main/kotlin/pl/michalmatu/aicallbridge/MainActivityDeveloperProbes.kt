package pl.michalmatu.aicallbridge

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.ui.CapabilityDiagnosticsActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/** Existing owner-only diagnostic entrypoints. Never requests privileged transport access. */
internal class MainActivityDeveloperProbes(
    private val activity: Activity,
    @Suppress("UNUSED_PARAMETER") private val statusView: TextView? = null,
) : AutoCloseable {
    fun addControls(content: LinearLayout) {
        content.addView(UiDesign.heading(activity, activity.getString(R.string.dev_ui_title)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(activity, 12))
        content.addView(UiDesign.action(activity, activity.getString(R.string.dev_ui_usb)) {
            activity.startActivity(Intent(activity, UsbDebugPanelActivity::class.java))
        }, UiDesign.spaceAfter(activity, 8))
        content.addView(UiDesign.action(activity, activity.getString(R.string.dev_ui_capability)) {
            runCapabilityProbe()
        }, UiDesign.spaceAfter(activity, 8))
    }

    /** Kept as a lifecycle-compatible no-op for existing callers. */
    fun attach() = Unit

    fun runCapabilityProbe() {
        activity.startActivity(Intent(activity, CapabilityDiagnosticsActivity::class.java))
    }

    override fun close() = Unit
}
