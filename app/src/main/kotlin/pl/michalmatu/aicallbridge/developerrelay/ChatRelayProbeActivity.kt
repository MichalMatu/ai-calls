package pl.michalmatu.aicallbridge.developerrelay

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import rikka.shizuku.Shizuku

/** Dedicated ADB/shell-only entry point for the interactive developer relay. */
class ChatRelayProbeActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            text = "AI Call Bridge ChatGPT relay"
            textSize = 15f
            setTextIsSelectable(true)
        }
        setContentView(statusView)
        runRelay()
    }

    private fun runRelay() {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val maxTurns = intent.getIntExtra(EXTRA_MAX_TURNS, 1)
        try {
            ChatRelayEnvelope(sessionId, 1, "probe").validate()
            require(maxTurns in 1..ChatRelayLiveCallProbe.MAX_TURNS)
        } catch (_: IllegalArgumentException) {
            finishWithError("invalid_relay_config")
            return
        }

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

        statusView.text = "Running interactive ChatGPT relay…"
        Log.i(TAG, "chat_relay_probe_start=true,turns=$maxTurns")
        ChatRelayLiveCallProbe.run(this, sessionId, maxTurns) { result ->
            runOnUiThread {
                statusView.text = result
                Log.i(TAG, "chat_relay_probe_complete=true")
                finish()
            }
        }
    }

    private fun finishWithError(reason: String) {
        val result = "chat_relay_probe_error=$reason"
        statusView.text = result
        Log.i(TAG, result)
        finish()
    }

    private companion object {
        const val TAG = "AiCallBridge"
        const val EXTRA_SESSION_ID = "relay_session_id"
        const val EXTRA_MAX_TURNS = "relay_max_turns"
    }
}
