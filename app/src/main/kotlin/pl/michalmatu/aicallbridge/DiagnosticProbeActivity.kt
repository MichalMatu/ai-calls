package pl.michalmatu.aicallbridge

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import pl.michalmatu.aicallbridge.shizuku.ShizukuAbortLatencyProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuEndpointCloseProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuEnduranceProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuUserServiceProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuWatchdogProbe
import rikka.shizuku.Shizuku

/** ADB/shell-only entry point for privileged Phase 2 diagnostic probes. */
class DiagnosticProbeActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            text = "Phase 2 diagnostic probe"
            textSize = 15f
            setTextIsSelectable(true)
        }
        setContentView(statusView)
        runRequestedProbe()
    }

    private fun runRequestedProbe() {
        if (!Shizuku.pingBinder()) {
            finishWithError("binder_unavailable")
            return
        }
        if (Shizuku.isPreV11()) {
            finishWithError("unsupported_pre_v11")
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            finishWithError("permission_required")
            return
        }

        when {
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_ENDPOINT_CLOSE_PROBE, false) -> {
                statusView.text = "Running Shizuku endpoint-close fail-safe probe…"
                Log.i(TAG, "shizuku_endpoint_close_probe_start=true")
                ShizukuEndpointCloseProbe.run(
                    this,
                    resultCallback("shizuku_endpoint_close_probe_result"),
                )
            }
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_ENDURANCE_PROBE, false) -> {
                statusView.text = "Running Shizuku bidirectional endurance probe…"
                Log.i(TAG, "shizuku_endurance_probe_start=true")
                ShizukuEnduranceProbe.run(this, resultCallback("shizuku_endurance_probe_result"))
            }
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_ABORT_PROBE, false) -> {
                statusView.text = "Running Shizuku explicit abort latency probe…"
                Log.i(TAG, "shizuku_abort_probe_start=true")
                ShizukuAbortLatencyProbe.run(this, resultCallback("shizuku_abort_probe_result"))
            }
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_WATCHDOG_PROBE, false) -> {
                statusView.text = "Running Shizuku heartbeat watchdog probe…"
                Log.i(TAG, "shizuku_watchdog_probe_start=true")
                ShizukuWatchdogProbe.run(this, resultCallback("shizuku_watchdog_probe_result"))
            }
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_LIVE_PROBE, false) -> {
                statusView.text = "Running Shizuku UserService live parity probe…"
                Log.i(TAG, "shizuku_live_probe_start=true")
                ShizukuUserServiceProbe.runLive(
                    this,
                    LIVE_SHIZUKU_DURATION_MS,
                    resultCallback("shizuku_probe_result"),
                )
            }
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_PROBE, false) -> {
                statusView.text = "Running Shizuku UserService off-call probe…"
                Log.i(TAG, "shizuku_probe_start=true")
                ShizukuUserServiceProbe.run(this, resultCallback("shizuku_probe_result"))
            }
            else -> finishWithError("no_probe_requested")
        }
    }

    private fun resultCallback(logKey: String) = ShizukuUserServiceProbe.Callback { result ->
        runOnUiThread {
            statusView.text = result
            Log.i(TAG, "$logKey:\n$result")
            finish()
        }
    }

    private fun finishWithError(reason: String) {
        val result = "diagnostic_probe_error=$reason"
        statusView.text = result
        Log.i(TAG, result)
        finish()
    }

    private companion object {
        const val TAG = "AiCallBridge"
        const val LIVE_SHIZUKU_DURATION_MS = 5_000
        const val EXTRA_RUN_SHIZUKU_PROBE = "run_shizuku_probe"
        const val EXTRA_RUN_SHIZUKU_LIVE_PROBE = "run_shizuku_live_probe"
        const val EXTRA_RUN_SHIZUKU_WATCHDOG_PROBE = "run_shizuku_watchdog_probe"
        const val EXTRA_RUN_SHIZUKU_ABORT_PROBE = "run_shizuku_abort_probe"
        const val EXTRA_RUN_SHIZUKU_ENDURANCE_PROBE = "run_shizuku_endurance_probe"
        const val EXTRA_RUN_SHIZUKU_ENDPOINT_CLOSE_PROBE = "run_shizuku_endpoint_close_probe"
    }
}
