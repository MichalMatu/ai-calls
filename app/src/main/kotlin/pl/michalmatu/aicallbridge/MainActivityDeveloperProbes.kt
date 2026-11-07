package pl.michalmatu.aicallbridge

import android.app.Activity
import android.content.Intent
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
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
            publishStatus("Shizuku permission denied")
            Log.i(TAG, "shizuku_probe_permission=denied")
        }
    }

    fun addControls(content: LinearLayout) {
        content.addView(UiDesign.heading(activity, "Diagnostyka urządzenia").apply { textSize = 17f }, UiDesign.spaceAfter(activity, 12))
        content.addView(Button(activity).apply {
            text = "USB / ADB — diagnostyka"
            setOnClickListener {
                activity.startActivity(Intent(activity, UsbDebugPanelActivity::class.java))
            }
        })
        content.addView(Button(activity).apply {
            text = "Run device capability probe"
            setOnClickListener { runCapabilityProbe() }
        })
        content.addView(Button(activity).apply {
            text = "Run Shizuku UserService probe"
            setOnClickListener { runShizukuProbe(false) }
        })
        // These legacy buttons only published status text and never controlled audio.
        // Present the truth as diagnostic information, not as misleading action buttons.
        content.addView(UiDesign.heading(activity, "Audio RX / TX — status").apply { textSize = 17f },
            UiDesign.spaceAfter(activity, 8))
        content.addView(UiDesign.caption(activity,
            "Sondy downlink/uplink wymagają testów fazy 2. Ten panel nie włącza przejęcia ani STOP audio."),
            UiDesign.spaceAfter(activity, 12))
        content.addView(UiDesign.caption(activity,
            "Do zatrzymania AI użyj STOP AI w Kampaniach lub na ekranie Start."),
            UiDesign.spaceAfter(activity, 12))
    }

    fun attach() {
        if (attached) return
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        attached = true
    }

    fun runCapabilityProbe() {
        publishStatus(
            try {
                CapabilityProbe(activity).run()
            } catch (error: Throwable) {
                "Capability probe failed: ${error.javaClass.simpleName}: ${error.message}"
            },
        )
    }

    private fun runShizukuProbe(live: Boolean) {
        pendingShizukuProbe = true
        pendingShizukuLiveProbe = live

        if (!Shizuku.pingBinder()) {
            publishStatus("Shizuku binder unavailable; start Shizuku first")
            Log.i(TAG, "shizuku_probe_binder=unavailable")
            return
        }

        if (Shizuku.isPreV11()) {
            pendingShizukuProbe = false
            pendingShizukuLiveProbe = false
            publishStatus("Shizuku pre-v11 is unsupported")
            Log.i(TAG, "shizuku_probe_version=unsupported_pre_v11")
            return
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                pendingShizukuProbe = false
                pendingShizukuLiveProbe = false
                publishStatus("Shizuku permission denied; enable it in Shizuku")
                Log.i(TAG, "shizuku_probe_permission=rationale_required")
                return
            }
            publishStatus("Requesting Shizuku permission…")
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
