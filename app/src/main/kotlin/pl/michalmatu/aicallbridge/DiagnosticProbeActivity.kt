package pl.michalmatu.aicallbridge

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import pl.michalmatu.aicallbridge.session.CallMediaOffCallSmokeProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuAbortLatencyProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuCallEndProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuCycleProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuEndpointCloseProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuEnduranceProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuUserServiceProbe
import pl.michalmatu.aicallbridge.shizuku.ShizukuWatchdogProbe
import rikka.shizuku.Shizuku

/** ADB/shell-only entry point for privileged diagnostic probes. */
class DiagnosticProbeActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            text = "AI Call Bridge diagnostic probe"
            textSize = 15f
            setTextIsSelectable(true)
        }
        setContentView(statusView)
        runRequestedProbe()
    }

    private fun runRequestedProbe() {
        if (intent.getBooleanExtra(EXTRA_RUN_LOCAL_PHONE_LLM_SPEECH_PIPELINE_PROBE, false)) {
            statusView.text = "Running local phone LLM speech pipeline probe…"
            Log.i(TAG, "local_phone_llm_speech_pipeline_probe_start=true")
            LocalPhoneLlmSpeechPipelineProbe.run(this) { result ->
                runOnUiThread {
                    statusView.text = result
                    Log.i(TAG, "local_phone_llm_speech_pipeline_probe_result:\n$result")
                    finish()
                }
            }
            return
        }

        if (intent.getBooleanExtra(EXTRA_RUN_LOCAL_MAC_TEXT_BACKEND_PROBE, false)) {
            val baseUrl = intent.getStringExtra(EXTRA_LOCAL_TEXT_BASE_URL).orEmpty()
            val model = intent.getStringExtra(EXTRA_LOCAL_TEXT_MODEL).orEmpty()
            if (baseUrl.isBlank() || model.isBlank()) {
                finishWithError("local_text_backend_config_missing")
                return
            }
            statusView.text = "Running local Mac text backend probe…"
            Log.i(TAG, "local_mac_text_backend_probe_start=true")
            LocalMacTextBackendProbe.run(this, baseUrl, model) { result ->
                runOnUiThread {
                    statusView.text = result
                    Log.i(TAG, "local_mac_text_backend_probe_result:\n$result")
                    finish()
                }
            }
            return
        }

        if (intent.getBooleanExtra(EXTRA_RUN_LOCAL_SPEECH_TEXT_PIPELINE_PROBE, false)) {
            statusView.text = "Running local speech + text pipeline probe…"
            Log.i(TAG, "local_speech_text_pipeline_probe_start=true")
            LocalSpeechTextPipelineProbe.run(this) { result ->
                runOnUiThread {
                    statusView.text = result
                    Log.i(TAG, "local_speech_text_pipeline_probe_result:\n$result")
                    finish()
                }
            }
            return
        }

        if (intent.getBooleanExtra(EXTRA_RUN_LOCAL_SPEECH_PRODUCTION_PROBE, false)) {
            statusView.text = "Running production local speech adapter probe…"
            Log.i(TAG, "local_speech_production_probe_start=true")
            LocalSpeechProductionProbe.run(this) { result ->
                runOnUiThread {
                    statusView.text = result
                    Log.i(TAG, "local_speech_production_probe_result:\n$result")
                    finish()
                }
            }
            return
        }

        if (intent.getBooleanExtra(EXTRA_RUN_LOCAL_SPEECH_PFD_LOOPBACK_PROBE, false)) {
            statusView.text = "Running local speech PFD loopback probe…"
            Log.i(TAG, "local_speech_pfd_loopback_probe_start=true")
            LocalSpeechPfdLoopbackProbe.run(this) { result ->
                runOnUiThread {
                    statusView.text = result
                    Log.i(TAG, "local_speech_pfd_loopback_probe_result:\n$result")
                    finish()
                }
            }
            return
        }

        if (intent.getBooleanExtra(EXTRA_RUN_LOCAL_SPEECH_CAPABILITY_PROBE, false)) {
            statusView.text = "Running local STT/TTS capability probe…"
            Log.i(TAG, "local_speech_capability_probe_start=true")
            LocalSpeechCapabilityProbe.run(this) { result ->
                runOnUiThread {
                    statusView.text = result
                    Log.i(TAG, "local_speech_capability_probe_result:\n$result")
                    finish()
                }
            }
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

        when {
            intent.getBooleanExtra(EXTRA_RUN_LOCAL_PHONE_LLM_LIVE_CALL_PROBE, false) -> {
                statusView.text = "Running local phone LLM live-call probe…"
                Log.i(TAG, "local_phone_llm_live_call_probe_start=true")
                LocalPhoneLlmLiveCallProbe.run(this) { result ->
                    runOnUiThread {
                        statusView.text = result
                        Log.i(TAG, "local_phone_llm_live_call_probe_result:\n$result")
                        finish()
                    }
                }
            }
            intent.getBooleanExtra(EXTRA_RUN_REALTIME_LIVE_CALL_SMOKE, false) -> {
                statusView.text = "Running controlled Realtime live-call smoke…"
                Log.i(TAG, "realtime_live_call_smoke_start=true")
                val durationMs = intent.getLongExtra(
                    EXTRA_REALTIME_LIVE_DURATION_MS,
                    RealtimeLiveCallSmokeSpec.DEFAULT_DURATION_MS,
                )
                RealtimeLiveCallSmokeProbe.run(this, durationMs) { result ->
                    runOnUiThread {
                        statusView.text = result
                        Log.i(TAG, "realtime_live_call_smoke_result:\n$result")
                        finish()
                    }
                }
            }
            intent.getBooleanExtra(EXTRA_RUN_REALTIME_NETWORK_OFF_CALL_SMOKE, false) -> {
                statusView.text = "Running Realtime network + production media off-call smoke…"
                Log.i(TAG, "realtime_network_off_call_smoke_start=true")
                RealtimeNetworkOffCallSmokeProbe.run(this) { result ->
                    runOnUiThread {
                        statusView.text = result
                        Log.i(TAG, "realtime_network_off_call_smoke_result:\n$result")
                        finish()
                    }
                }
            }
            intent.getBooleanExtra(EXTRA_RUN_PRODUCTION_MEDIA_OFF_CALL_SMOKE, false) -> {
                statusView.text = "Running production coordinator off-call smoke…"
                Log.i(TAG, "production_media_off_call_smoke_start=true")
                CallMediaOffCallSmokeProbe.run(this) { result ->
                    runOnUiThread {
                        statusView.text = result
                        Log.i(TAG, "production_media_off_call_smoke_result:\n$result")
                        finish()
                    }
                }
            }
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_ENDPOINT_CLOSE_PROBE, false) -> {
                statusView.text = "Running Shizuku endpoint-close fail-safe probe…"
                Log.i(TAG, "shizuku_endpoint_close_probe_start=true")
                ShizukuEndpointCloseProbe.run(
                    this,
                    resultCallback("shizuku_endpoint_close_probe_result"),
                )
            }
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_CALL_END_PROBE, false) -> {
                statusView.text = "Running Shizuku natural call-end fail-safe probe…"
                Log.i(TAG, "shizuku_call_end_probe_start=true")
                val callback = resultCallback("shizuku_call_end_probe_result")
                if (intent.hasExtra(EXTRA_CALL_END_WAIT_MS)) {
                    val waitMs = intent.getLongExtra(EXTRA_CALL_END_WAIT_MS, -1L)
                    try {
                        ShizukuCallEndProbe.run(this, waitMs, callback)
                    } catch (_: IllegalArgumentException) {
                        finishWithError("invalid_call_end_wait")
                    }
                } else {
                    ShizukuCallEndProbe.run(this, callback)
                }
            }
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_CYCLE_PROBE, false) -> {
                statusView.text = "Running Shizuku repeated start/abort cycle probe…"
                Log.i(TAG, "shizuku_cycle_probe_start=true")
                val callback = resultCallback("shizuku_cycle_probe_result")
                if (intent.hasExtra(EXTRA_CYCLE_COUNT)) {
                    val cycleCount = intent.getIntExtra(EXTRA_CYCLE_COUNT, -1)
                    try {
                        ShizukuCycleProbe.run(this, cycleCount, callback)
                    } catch (_: IllegalArgumentException) {
                        finishWithError("invalid_cycle_count")
                    }
                } else {
                    ShizukuCycleProbe.run(this, callback)
                }
            }
            intent.getBooleanExtra(EXTRA_RUN_SHIZUKU_ENDURANCE_PROBE, false) -> {
                statusView.text = "Running Shizuku bidirectional endurance probe…"
                Log.i(TAG, "shizuku_endurance_probe_start=true")
                val callback = resultCallback("shizuku_endurance_probe_result")
                if (intent.hasExtra(EXTRA_ENDURANCE_DURATION_MS)) {
                    val durationMs = intent.getLongExtra(EXTRA_ENDURANCE_DURATION_MS, -1L)
                    try {
                        ShizukuEnduranceProbe.run(this, durationMs, callback)
                    } catch (_: IllegalArgumentException) {
                        finishWithError("invalid_endurance_duration")
                    }
                } else {
                    ShizukuEnduranceProbe.run(this, callback)
                }
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
        const val EXTRA_RUN_LOCAL_PHONE_LLM_SPEECH_PIPELINE_PROBE = "run_local_phone_llm_speech_pipeline_probe"
        const val EXTRA_RUN_LOCAL_MAC_TEXT_BACKEND_PROBE = "run_local_mac_text_backend_probe"
        const val EXTRA_LOCAL_TEXT_BASE_URL = "local_text_base_url"
        const val EXTRA_LOCAL_TEXT_MODEL = "local_text_model"
        const val EXTRA_RUN_LOCAL_SPEECH_CAPABILITY_PROBE = "run_local_speech_capability_probe"
        const val EXTRA_RUN_LOCAL_SPEECH_TEXT_PIPELINE_PROBE = "run_local_speech_text_pipeline_probe"
        const val EXTRA_RUN_LOCAL_SPEECH_PRODUCTION_PROBE = "run_local_speech_production_probe"
        const val EXTRA_RUN_LOCAL_SPEECH_PFD_LOOPBACK_PROBE = "run_local_speech_pfd_loopback_probe"
        const val EXTRA_RUN_LOCAL_PHONE_LLM_LIVE_CALL_PROBE = "run_local_phone_llm_live_call_probe"
        const val EXTRA_RUN_REALTIME_LIVE_CALL_SMOKE = "run_realtime_live_call_smoke"
        const val EXTRA_REALTIME_LIVE_DURATION_MS = "realtime_live_duration_ms"
        const val EXTRA_RUN_REALTIME_NETWORK_OFF_CALL_SMOKE = "run_realtime_network_off_call_smoke"
        const val EXTRA_RUN_PRODUCTION_MEDIA_OFF_CALL_SMOKE = "run_production_media_off_call_smoke"
        const val EXTRA_RUN_SHIZUKU_PROBE = "run_shizuku_probe"
        const val EXTRA_RUN_SHIZUKU_LIVE_PROBE = "run_shizuku_live_probe"
        const val EXTRA_RUN_SHIZUKU_WATCHDOG_PROBE = "run_shizuku_watchdog_probe"
        const val EXTRA_RUN_SHIZUKU_ABORT_PROBE = "run_shizuku_abort_probe"
        const val EXTRA_RUN_SHIZUKU_ENDURANCE_PROBE = "run_shizuku_endurance_probe"
        const val EXTRA_RUN_SHIZUKU_ENDPOINT_CLOSE_PROBE = "run_shizuku_endpoint_close_probe"
        const val EXTRA_RUN_SHIZUKU_CALL_END_PROBE = "run_shizuku_call_end_probe"
        const val EXTRA_RUN_SHIZUKU_CYCLE_PROBE = "run_shizuku_cycle_probe"
        const val EXTRA_ENDURANCE_DURATION_MS = "endurance_duration_ms"
        const val EXTRA_CALL_END_WAIT_MS = "call_end_wait_ms"
        const val EXTRA_CYCLE_COUNT = "cycle_count"
    }
}
