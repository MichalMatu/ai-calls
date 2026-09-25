package pl.michalmatu.aicallbridge

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

/** ADB/shell-only entry point for the off-call Gemma action-router acceptance probe. */
class GemmaActionRouterProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val statusView = TextView(this).apply {
            text = "Running Gemma action-router probe…"
            textSize = 15f
            setTextIsSelectable(true)
        }
        setContentView(statusView)
        Log.i(TAG, "gemma_action_router_probe_start=true")
        GemmaActionRouterProbe.run(this) { result ->
            runOnUiThread {
                statusView.text = result
                Log.i(TAG, "gemma_action_router_probe_result:\n$result")
                finish()
            }
        }
    }

    private companion object {
        const val TAG = "AiCallBridge"
    }
}
