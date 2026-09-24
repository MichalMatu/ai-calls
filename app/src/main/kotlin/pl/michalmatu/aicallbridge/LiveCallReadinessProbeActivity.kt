package pl.michalmatu.aicallbridge

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import java.io.File
import rikka.shizuku.Shizuku

/** ADB-only, no-call probe for prerequisites required before any live-call runner may dial. */
class LiveCallReadinessProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val statusView = TextView(this).apply {
            text = "AI Calls live-call readiness"
            textSize = 15f
            setTextIsSelectable(true)
        }
        setContentView(statusView)

        val result = evaluateReadiness()
        val report = result.renderReport()
        runCatching {
            File(filesDir, REPORT_FILENAME).writeText(report)
        }.onFailure { error ->
            Log.e(TAG, "live_call_readiness_report_write_failed", error)
        }
        statusView.text = report
        Log.i(TAG, "live_call_readiness_complete=${result.ready}")
        finish()
    }

    private fun evaluateReadiness(): LiveCallReadinessResult {
        val recordAudioGranted =
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val shizukuBinderAvailable = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuSupported = shizukuBinderAvailable &&
            runCatching { !Shizuku.isPreV11() }.getOrDefault(false)
        val shizukuPermissionGranted = shizukuSupported &&
            runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)

        return LiveCallReadinessPolicy.evaluate(
            LiveCallReadinessInputs(
                recordAudioGranted = recordAudioGranted,
                shizukuBinderAvailable = shizukuBinderAvailable,
                shizukuSupported = shizukuSupported,
                shizukuPermissionGranted = shizukuPermissionGranted,
            ),
        )
    }

    companion object {
        const val REPORT_FILENAME = "live-call-readiness-report.txt"
        private const val TAG = "AiCallBridge"
    }
}
