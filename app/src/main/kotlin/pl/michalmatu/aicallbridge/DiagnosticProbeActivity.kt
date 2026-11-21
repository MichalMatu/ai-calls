package pl.michalmatu.aicallbridge

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import java.io.File
import pl.michalmatu.aicallbridge.campaign.CampaignAgentControl
import pl.michalmatu.aicallbridge.campaign.AppointmentMvpActivity
import pl.michalmatu.aicallbridge.debug.UsbDebugBridge
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.session.GsmMediaTransportReadiness
import pl.michalmatu.aicallbridge.session.CallMediaOffCallSmokeProbe

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
        if (intent.getBooleanExtra("open_appointment_editor", false)) {
            // DUMP-only and debug build; cannot dial or bypass consent.
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
                finishWithError("debug_build_required")
                return
            }
            startActivity(Intent(this, AppointmentMvpActivity::class.java))
            finish()
            return
        }
        if (intent.hasExtra(UsbDebugBridge.EXTRA_ACTION)) {
            UsbDebugBridge.handle(this, intent) { result ->
                statusView.text = result
                finish()
            }
            return
        }
        if (intent.getBooleanExtra(EXTRA_RUN_CAMPAIGN_AGENT_STOP, false)) {
            val report = CampaignAgentControl.stopOwnedCampaign(this)
            File(filesDir, CampaignAgentControl.REPORT_FILE_NAME).writeText(report)
            statusView.text = report
            Log.i(TAG, "campaign_agent_stop_status=" + report.trim())
            finish()
            return
        }
        // Existing shell/DUMP-only activity can invoke an internal service without UI taps.
        // An explicit owner task is accepted on device; runtime keeps exact scope and Call checks.
        val requestCampaignStart = intent.getBooleanExtra(EXTRA_RUN_CAMPAIGN_AGENT_START, false)
        if (requestCampaignStart ||
            intent.getBooleanExtra(EXTRA_RUN_CAMPAIGN_AGENT_PREFLIGHT, false)
        ) {
            val report = CampaignAgentControl.run(this, requestCampaignStart,
                pl.michalmatu.aicallbridge.campaign.CampaignDialogueMode.explicitStart(
                    intent.getStringExtra(pl.michalmatu.aicallbridge.campaign.AppOwnedCampaignService.EXTRA_DIALOGUE_MODE),
                ), recordCallEvidence = if (intent.hasExtra("record_call_evidence"))
                    intent.getBooleanExtra("record_call_evidence", false) else null)
            File(filesDir, CampaignAgentControl.REPORT_FILE_NAME).writeText(report)
            statusView.text = report
            Log.i(TAG, "campaign_agent_report:\n" + report)
            finish()
            return
        }
        if (intent.getBooleanExtra(EXTRA_RUN_OPENAI_TEXT_SPEECH_PIPELINE_PROBE, false)) {
            val config = try {
                OpenAiTextSmokeConfig.loadAndDelete(File(filesDir, OpenAiTextSmokeConfig.FILE_NAME))
            } catch (error: Throwable) {
                finishWithError("openai_text_config_${error.javaClass.simpleName}")
                return
            }
            statusView.text = "Running OpenAI text speech pipeline probe…"
            Log.i(TAG, "openai_text_speech_pipeline_probe_start=true")
            OpenAiTextSpeechPipelineProbe.run(this, config) { result ->
                runOnUiThread {
                    statusView.text = result
                    Log.i(TAG, "openai_text_speech_pipeline_probe_complete=true")
                    finish()
                }
            }
            return
        }

        if (intent.getBooleanExtra(EXTRA_RUN_LOCAL_PHONE_LLM_SPEECH_PIPELINE_PROBE, false)) {
            statusView.text = "Running local phone LLM speech pipeline probe…"
            Log.i(TAG, "local_phone_llm_speech_pipeline_probe_start=true")
            LocalPhoneLlmSpeechPipelineProbe.run(this) { result ->
                runOnUiThread {
                    statusView.text = result
                    Log.i(TAG, "local_phone_llm_speech_pipeline_probe_complete=true")
                    finish()
                }
            }
            return
        }

        if (intent.getBooleanExtra(EXTRA_RUN_GENERIC_PHONE_TASK_ACCEPTANCE_PROBE, false)) {
            statusView.text = "Running generic phone-task acceptance probe…"
            Log.i(TAG, "generic_phone_task_acceptance_probe_start=true")
            GenericPhoneTaskAcceptanceProbe.run(this) { result ->
                runOnUiThread {
                    statusView.text = result
                    Log.i(TAG, "generic_phone_task_acceptance_probe_complete=true")
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
                    Log.i(TAG, "local_mac_text_backend_probe_complete=true")
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
                    Log.i(TAG, "local_speech_text_pipeline_probe_complete=true")
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
                    Log.i(TAG, "local_speech_production_probe_complete=true")
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

        if (!GsmMediaTransportReadiness.isAvailable(this)) {
            finishWithError("gsm_shell_host_unavailable")
            return
        }

        when {
            intent.getBooleanExtra(EXTRA_RUN_LOCAL_PHONE_LLM_LIVE_CALL_PROBE, false) -> {
                val providerName = intent.getStringExtra(EXTRA_TEXT_LLM_PROVIDER)
                    ?: TextLlmProvider.LOCAL_PHONE_LLM.name
                val provider = TextLlmProvider.fromStored(providerName)
                if (
                    provider != TextLlmProvider.LOCAL_PHONE_LLM &&
                    provider != TextLlmProvider.LOCAL_GEMMA_4
                ) {
                    finishWithError("local_live_text_provider_not_supported_${provider.name.lowercase()}")
                    return
                }
                val gateCFastPath = intent.getBooleanExtra(EXTRA_GATE_C_FAST_PATH, false)
                val liveCallTarget = intent.getStringExtra(EXTRA_LIVE_CALL_TARGET)
                val orangeLiveActionId = intent.getStringExtra(EXTRA_ORANGE_LIVE_ACTION)
                val gateCRelaySessionId = intent.getStringExtra(EXTRA_GATE_C_RELAY_SESSION_ID)
                val request = try {
                    LocalPhoneLlmLiveCallProbeRequest.create(
                        provider = provider,
                        gateCFastPath = gateCFastPath,
                        liveCallTarget = liveCallTarget,
                        orangeLiveActionId = orangeLiveActionId,
                        gateCRelaySessionId = gateCRelaySessionId,
                    )
                } catch (error: IllegalArgumentException) {
                    finishWithError("local_live_request_${error.message.orEmpty().take(120)}")
                    return
                }
                statusView.text = "Running local text LLM live-call probe: ${provider.displayName}…"
                Log.i(
                    TAG,
                    "local_phone_llm_live_call_probe_start=true,provider=${provider.name}," +
                        "gate_c_fast_path=$gateCFastPath," +
                        "gate_c_hybrid_dialogue=${request.gateCHybridDialogue}," +
                        "orange_live_action=${request.orangeLiveAction?.wireId ?: "none"}",
                )
                LocalPhoneLlmLiveCallProbe.run(this, request) { result ->
                    runOnUiThread {
                        statusView.text = result
                        Log.i(TAG, "local_phone_llm_live_call_probe_complete=true")
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
            else -> finishWithError("no_probe_requested")
        }
    }

    private fun finishWithError(reason: String) {
        val result = "diagnostic_probe_error=$reason"
        statusView.text = result
        Log.i(TAG, "diagnostic_probe_failed=true")
        finish()
    }

    private companion object {
        const val TAG = "AiCallBridge"
        const val EXTRA_RUN_CAMPAIGN_AGENT_PREFLIGHT = "run_campaign_agent_preflight"
        const val EXTRA_RUN_CAMPAIGN_AGENT_START = "run_campaign_agent_start"
        const val EXTRA_RUN_CAMPAIGN_AGENT_STOP = "run_campaign_agent_stop"
        const val EXTRA_RUN_OPENAI_TEXT_SPEECH_PIPELINE_PROBE = "run_openai_text_speech_pipeline_probe"
        const val EXTRA_RUN_LOCAL_PHONE_LLM_SPEECH_PIPELINE_PROBE = "run_local_phone_llm_speech_pipeline_probe"
        const val EXTRA_RUN_GENERIC_PHONE_TASK_ACCEPTANCE_PROBE = "run_generic_phone_task_acceptance_probe"
        const val EXTRA_RUN_LOCAL_MAC_TEXT_BACKEND_PROBE = "run_local_mac_text_backend_probe"
        const val EXTRA_LOCAL_TEXT_BASE_URL = "local_text_base_url"
        const val EXTRA_LOCAL_TEXT_MODEL = "local_text_model"
        const val EXTRA_RUN_LOCAL_SPEECH_CAPABILITY_PROBE = "run_local_speech_capability_probe"
        const val EXTRA_RUN_LOCAL_SPEECH_TEXT_PIPELINE_PROBE = "run_local_speech_text_pipeline_probe"
        const val EXTRA_RUN_LOCAL_SPEECH_PRODUCTION_PROBE = "run_local_speech_production_probe"
        const val EXTRA_RUN_LOCAL_SPEECH_PFD_LOOPBACK_PROBE = "run_local_speech_pfd_loopback_probe"
        const val EXTRA_RUN_LOCAL_PHONE_LLM_LIVE_CALL_PROBE = "run_local_phone_llm_live_call_probe"
        const val EXTRA_TEXT_LLM_PROVIDER = "text_llm_provider"
        const val EXTRA_GATE_C_FAST_PATH = "gate_c_fast_path"
        const val EXTRA_LIVE_CALL_TARGET = "live_call_target"
        const val EXTRA_ORANGE_LIVE_ACTION = "orange_live_action"
        const val EXTRA_GATE_C_RELAY_SESSION_ID = "gate_c_relay_session_id"
        const val EXTRA_RUN_REALTIME_LIVE_CALL_SMOKE = "run_realtime_live_call_smoke"
        const val EXTRA_REALTIME_LIVE_DURATION_MS = "realtime_live_duration_ms"
        const val EXTRA_RUN_REALTIME_NETWORK_OFF_CALL_SMOKE = "run_realtime_network_off_call_smoke"
        const val EXTRA_RUN_PRODUCTION_MEDIA_OFF_CALL_SMOKE = "run_production_media_off_call_smoke"
    }
}
