package pl.michalmatu.aicallbridge

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import java.io.File

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

        val result = AndroidLiveCallReadiness.evaluate(this)
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

    companion object {
        const val REPORT_FILENAME = "live-call-readiness-report.txt"
        private const val TAG = "AiCallBridge"
    }
}
