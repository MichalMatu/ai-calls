package pl.michalmatu.aicallbridge

import android.app.Activity
import android.content.Intent
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.ui.CapabilityDiagnosticsActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Button
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
    private val statusView: TextView,
) : AutoCloseable {
    private var pendingShizukuProbe = false
    private var pendingShizukuLiveProbe = false
    private var attached = false

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
            activity.startActivity(Intent(activity, CapabilityDiagnosticsActivity::class.java))
        }, UiDesign.spaceAfter(activity, 8))
        content.addView(UiDesign.action(activity,
            activity.getString(R.string.dev_ui_shizuku)) {
            runShizukuProbe(false)
        }, UiDesign.spaceAfter(activity, 16))
    }

    fun attach() {
        if (attached) return
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        attached = true
    }

    private fun runShizukuProbe(live: Boolean) {
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
        publishStatus(
            if (live) {
                "Running Shizuku UserService live parity probe…"
            } else {
                "Running Shizuku UserService off-call probe…"
            },
        )
        Log.i(TAG, if (live) "shizuku_live_probe_start=true" else "shizuku_probe_start=true")

        val callback = ShizukuUserServiceProbe.Callback { result ->
            activity.runOnUiThread {
                publishStatus(result)
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
        if (!attached) return
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        attached = false
        pendingShizukuProbe = false
        pendingShizukuLiveProbe = false
    }

    private fun publishStatus(text: String) {
        statusView.text = text
    }

    private companion object {
        const val TAG = "AiCallBridge"
        const val REQUEST_SHIZUKU = 1002
        const val LIVE_SHIZUKU_DURATION_MS = 5_000
    }
}
