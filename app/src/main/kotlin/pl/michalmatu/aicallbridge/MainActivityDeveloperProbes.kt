package pl.michalmatu.aicallbridge

import android.app.Activity
import android.content.Intent
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.ui.CapabilityDiagnosticsActivity
import pl.michalmatu.aicallbridge.ui.DiagnosticResultDialog
import pl.michalmatu.aicallbridge.ui.UiDesign
import android.content.pm.PackageManager
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import pl.michalmatu.aicallbridge.shizuku.ShizukuUserServiceProbe
import rikka.shizuku.Shizuku

/**
 * Owns developer/probe controls and their Shizuku lifecycle for [MainActivity].
 *
 * Keeping this owner separate prevents diagnostic state/listeners from being mixed with product
 * runtime/model settings while preserving the existing launcher behavior and controls.
 */
internal class MainActivityDeveloperProbes(
    private val activity: Activity,
    private val statusView: TextView? = null,
) : AutoCloseable {
    private var pendingShizukuProbe = false
    private var pendingShizukuLiveProbe = false
    private var attached = false
    private var closed = false
    private var running = false
    private var probeButton: android.widget.Button? = null
    private var resultDialog: DiagnosticResultDialog? = null

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (pendingShizukuProbe) {
            runShizukuProbe(pendingShizukuLiveProbe)
        }
    }

    private val shizukuPermissionResultListener = Shizuku.OnRequestPermissionResultListener {
            requestCode,
            grantResult,
        ->
        if (requestCode != REQUEST_SHIZUKU) {
            return@OnRequestPermissionResultListener
        }

        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            runShizukuProbe(pendingShizukuLiveProbe)
        } else {
            pendingShizukuProbe = false
            pendingShizukuLiveProbe = false
            publishStatus(activity.getString(R.string.dev_ui_shizuku_denied))
            Log.i(TAG, "shizuku_probe_permission=denied")
        }
    }

    fun addControls(content: LinearLayout) {
        content.addView(UiDesign.heading(activity, activity.getString(R.string.dev_ui_title)).apply { textSize = 17f }, UiDesign.spaceAfter(activity, 12))
        content.addView(UiDesign.action(activity,
            activity.getString(R.string.dev_ui_usb)) {
            activity.startActivity(Intent(activity, UsbDebugPanelActivity::class.java))
        }, UiDesign.spaceAfter(activity, 8))
        content.addView(UiDesign.action(activity,
            activity.getString(R.string.dev_ui_capability)) {
            runCapabilityProbe()
        }, UiDesign.spaceAfter(activity, 8))
        probeButton = UiDesign.action(activity, activity.getString(R.string.dev_ui_shizuku)) {
            if (!running) runShizukuProbe(false)
        }.apply { tag = "developer-shizuku-test" }
        content.addView(probeButton, UiDesign.spaceAfter(activity, 16))
    }

    fun attach() {
        if (attached) return
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        attached = true
    }

    /** Old Home shortcut and developer tools now share the same destination. */
    fun runCapabilityProbe() {
        activity.startActivity(Intent(activity, CapabilityDiagnosticsActivity::class.java))
    }

    private fun runShizukuProbe(live: Boolean) {
        if (closed || running || activity.isFinishing || activity.isDestroyed) return
        pendingShizukuProbe = true
        pendingShizukuLiveProbe = live

        if (!Shizuku.pingBinder()) {
            publishStatus(activity.getString(R.string.dev_ui_shizuku_missing))
            Log.i(TAG, "shizuku_probe_binder=unavailable")
            return
        }

        if (Shizuku.isPreV11()) {
            pendingShizukuProbe = false
            pendingShizukuLiveProbe = false
            publishStatus(activity.getString(R.string.dev_ui_shizuku_unsupported))
            Log.i(TAG, "shizuku_probe_version=unsupported_pre_v11")
            return
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                pendingShizukuProbe = false
                pendingShizukuLiveProbe = false
                publishStatus(activity.getString(R.string.dev_ui_shizuku_permission))
                Log.i(TAG, "shizuku_probe_permission=rationale_required")
                return
            }
            publishStatus(activity.getString(R.string.dev_ui_shizuku_request))
            Log.i(TAG, "shizuku_probe_permission=requested")
            Shizuku.requestPermission(REQUEST_SHIZUKU)
            return
        }

        pendingShizukuProbe = false
        pendingShizukuLiveProbe = false
        running = true
        probeButton?.isEnabled = false
        publishStatus(activity.getString(R.string.shizuku_running))
        Log.i(TAG, if (live) "shizuku_live_probe_start=true" else "shizuku_probe_start=true")

        val callback = ShizukuUserServiceProbe.Callback { result ->
            activity.runOnUiThread {
                if (closed || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                running = false
                probeButton?.isEnabled = true
                val summary = resultDialog?.showResult(result)
                statusView?.text = summary
                Log.i(TAG, "shizuku_probe_result:\n$result")
            }
        }
        if (live) {
            ShizukuUserServiceProbe.runLive(activity, LIVE_SHIZUKU_DURATION_MS, callback)
        } else {
            ShizukuUserServiceProbe.run(activity, callback)
        }
    }

    override fun close() {
        closed = true
        resultDialog?.dismiss()
        resultDialog = null
        if (!attached) return
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        attached = false
        pendingShizukuProbe = false
        pendingShizukuLiveProbe = false
    }

    private fun publishStatus(text: String) {
        if (closed || activity.isFinishing || activity.isDestroyed) return
        statusView?.text = text
        val window = resultDialog ?: DiagnosticResultDialog(activity).also { resultDialog = it }
        window.showStatus(text)
    }

    private companion object {
        const val TAG = "AiCallBridge"
        const val REQUEST_SHIZUKU = 1002
        const val LIVE_SHIZUKU_DURATION_MS = 5_000
    }
}
