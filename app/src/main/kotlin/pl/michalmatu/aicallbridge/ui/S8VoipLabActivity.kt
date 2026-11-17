package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.LocalSpeechTextPipelineProbe
import pl.michalmatu.aicallbridge.session.S8VoipSixTurnLab
import pl.michalmatu.aicallbridge.session.S8LabModelReadiness
import pl.michalmatu.aicallbridge.session.S8LocalModelFourTurnProbe
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity

/**
 * Visible, manually triggered S8 no-Shizuku demo. App-owned synthetic RTP only:
 * no carrier, microphone, SIP credentials, Gemini, STT/TTS, GSM or external host.
 * Unlike DUMP-only probes this can be used without ADB or developer options.
 */
class S8VoipLabActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.s8_voip_lab_title)

    private val worker = Executors.newSingleThreadExecutor()
    private val modelWorker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var modelTimeout: Runnable? = null
    private var lab: S8VoipSixTurnLab? = null
    private var speechProbe: AutoCloseable? = null
    private var modelProbe: S8LocalModelFourTurnProbe? = null
    private var runToken: Long = 0
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var modelStatus: TextView
    private lateinit var startButton: Button
    private lateinit var speechButton: Button
    private lateinit var modelButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiDesign.content(this)
        content.addView(UiDesign.heading(this, getString(R.string.s8_voip_lab_title)),
            UiDesign.spaceAfter(this))
        content.addView(UiDesign.caption(this, getString(R.string.s8_voip_lab_explanation)),
            UiDesign.spaceAfter(this, UiDesign.GAP_CARD))

        val card = UiDesign.card(this).apply { tag = "s8-voip-lab-card" }
        status = UiDesign.status(this, getString(R.string.s8_voip_lab_idle)).apply {
            tag = "s8-voip-lab-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        details = UiDesign.caption(this, getString(R.string.s8_voip_lab_no_calls)).apply {
            tag = "s8-voip-lab-results"
        }
        card.addView(status, UiDesign.spaceAfter(this))
        card.addView(details, UiDesign.spaceAfter(this, UiDesign.GAP_CARD))

        startButton = UiDesign.action(
            this, getString(R.string.s8_voip_lab_start), UiActionTone.PRIMARY
        ) { beginSyntheticSession() }.apply { tag = "s8-voip-lab-start" }
        speechButton = UiDesign.action(
            this, getString(R.string.s8_voip_speech_start), UiActionTone.PRIMARY
        ) { beginLocalSpeechProbe() }.apply { tag = "s8-voip-lab-local-speech" }
        stopButton = UiDesign.action(
            this, getString(R.string.s8_voip_lab_stop), UiActionTone.REVOCATION
        ) { stopSyntheticSession() }.apply {
            tag = "s8-voip-lab-stop"
            isEnabled = false
        }
        card.addView(startButton, UiDesign.spaceAfter(this))
        card.addView(speechButton, UiDesign.spaceAfter(this))
        card.addView(stopButton)
        content.addView(card, UiDesign.spaceAfter(this, UiDesign.GAP_CARD))
        val readiness = UiDesign.card(this)
        modelStatus = UiDesign.caption(this, getString(R.string.s8_lab_model_not_checked)).apply {
            tag = "s8-lab-model-readiness"
        }
        readiness.addView(modelStatus, UiDesign.spaceAfter(this))
        modelButton = UiDesign.action(
            this, getString(R.string.s8_lab_model_four_turn_start), UiActionTone.PRIMARY
        ) { beginLocalModelFourTurns() }.apply {
            tag = "s8-lab-model-four-turn-start"
            isEnabled = false
        }
        readiness.addView(modelButton, UiDesign.spaceAfter(this))
        readiness.addView(UiDesign.action(
            this, getString(R.string.s8_lab_manage_model)
        ) {
            startActivity(Intent(this, LanguageModelActivity::class.java))
        }.apply { tag = "s8-lab-model-manage" })
        content.addView(readiness)
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        if (::modelStatus.isInitialized) {
            val readiness = S8LabModelReadiness.inspectPinnedE2B(this)
            modelStatus.text = getString(when (readiness) {
                S8LabModelReadiness.State.PROFILE_INVALID -> R.string.s8_lab_model_invalid_profile
                S8LabModelReadiness.State.MISSING -> R.string.s8_lab_model_missing
                S8LabModelReadiness.State.INVALID -> R.string.s8_lab_model_invalid
                S8LabModelReadiness.State.FILE_PRESENT_UNVERIFIED_INFERENCE ->
                    R.string.s8_lab_model_file_present
            })
            modelButton.isEnabled = readiness ==
                S8LabModelReadiness.State.FILE_PRESENT_UNVERIFIED_INFERENCE &&
                lab == null && speechProbe == null && modelProbe == null
        }
    }

    private fun beginSyntheticSession() {
        if (lab != null || speechProbe != null || modelProbe != null) return
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) {
            status.text = getString(R.string.s8_voip_lab_permission)
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 8108)
            return
        }
        if (!isPhoneIdle()) {
            status.text = getString(R.string.s8_voip_lab_blocked)
            return
        }
        val created = S8VoipSixTurnLab()
        lab = created
        val token = ++runToken
        startButton.isEnabled = false
        speechButton.isEnabled = false
        modelButton.isEnabled = false
        stopButton.isEnabled = true
        status.text = getString(R.string.s8_voip_lab_running)
        details.text = getString(R.string.s8_voip_lab_no_calls)

        worker.execute {
            val outcome = runCatching { created.run() }
            created.close()
            runOnUiThread {
                if (isDestroyed || isFinishing || lab !== created || runToken != token) {
                    return@runOnUiThread
                }
                lab = null
                startButton.isEnabled = true
                speechButton.isEnabled = true
                modelButton.isEnabled = S8LabModelReadiness.inspectPinnedE2B(this) ==
                    S8LabModelReadiness.State.FILE_PRESENT_UNVERIFIED_INFERENCE
                stopButton.isEnabled = false
                status.text = outcome.fold(
                    onSuccess = {
                        getString(if (it.passed()) R.string.s8_voip_lab_pass
                                  else R.string.s8_voip_lab_stopped)
                    },
                    onFailure = { getString(R.string.s8_voip_lab_failed) },
                )
                details.text = outcome.fold(
                    onSuccess = {
                        getString(R.string.s8_voip_lab_counters,
                            it.phasesCompleted(), it.rxFrames(), it.txFrames(), it.stopped())
                    },
                    onFailure = { getString(R.string.s8_voip_lab_failed_details) },
                )
            }
        }
    }

    /**
     * Manual off-call test with actual Android on-device STT and local TTS.
     * The synthetic text backend is intentionally NOT a real LLM. The source
     * audio is generated by TTS and piped to the recognizer, not recorded.
     */
    private fun beginLocalSpeechProbe() {
        if (lab != null || speechProbe != null || modelProbe != null) return
        val missing = listOf(
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            status.text = getString(R.string.s8_voip_speech_permission)
            requestPermissions(missing.toTypedArray(), 8110)
            return
        }
        if (!isPhoneIdle()) {
            status.text = getString(R.string.s8_voip_lab_blocked)
            return
        }
        startButton.isEnabled = false
        speechButton.isEnabled = false
        modelButton.isEnabled = false
        stopButton.isEnabled = true
        status.text = getString(R.string.s8_voip_speech_running)
        details.text = getString(R.string.s8_voip_speech_explanation)
        val token = ++runToken
        speechProbe = try {
            LocalSpeechTextPipelineProbe.run(this) { report ->
                runOnUiThread {
                if (isDestroyed || isFinishing || runToken != token || speechProbe == null) {
                    return@runOnUiThread
                }
                val result = report.lines().any { it == "text_pipeline_success=true" }
                speechProbe?.close()
                speechProbe = null
                startButton.isEnabled = true
                speechButton.isEnabled = true
                modelButton.isEnabled = S8LabModelReadiness.inspectPinnedE2B(this) ==
                    S8LabModelReadiness.State.FILE_PRESENT_UNVERIFIED_INFERENCE
                stopButton.isEnabled = false
                status.text = getString(
                    if (result) R.string.s8_voip_speech_pass else R.string.s8_voip_speech_fail
                )
                // Never display recognized text, approved phrases or raw error
                // strings from any backend on the transport demo screen.
                details.text = if (result) getString(R.string.s8_voip_speech_success_detail)
                    else getString(R.string.s8_voip_speech_failure_detail)
                }
            }
        } catch (_: Throwable) {
            startButton.isEnabled = true
            speechButton.isEnabled = true
            modelButton.isEnabled = S8LabModelReadiness.inspectPinnedE2B(this) ==
                S8LabModelReadiness.State.FILE_PRESENT_UNVERIFIED_INFERENCE
            stopButton.isEnabled = false
            status.text = getString(R.string.s8_voip_speech_fail)
            details.text = getString(R.string.s8_voip_speech_failure_detail)
            null
        }
    }

    /** Explicit local-only 4-turn native text test; no call or model download. */
    private fun beginLocalModelFourTurns() {
        if (lab != null || speechProbe != null || modelProbe != null) return
        if (S8LabModelReadiness.inspectPinnedE2B(this) !=
            S8LabModelReadiness.State.FILE_PRESENT_UNVERIFIED_INFERENCE) {
            status.text = getString(R.string.s8_lab_model_missing)
            return
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) {
            status.text = getString(R.string.s8_voip_lab_permission)
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 8111)
            return
        }
        if (!isPhoneIdle()) {
            status.text = getString(R.string.s8_voip_lab_blocked)
            return
        }
        val token = ++runToken
        startButton.isEnabled = false
        speechButton.isEnabled = false
        modelButton.isEnabled = false
        stopButton.isEnabled = true
        status.text = getString(R.string.s8_lab_model_four_turn_running)
        details.text = getString(R.string.s8_lab_model_four_turn_scope)
        val candidate = S8LocalModelFourTurnProbe(
            factory = {
                Gemma4LiteRtTextBackendFactory.create(
                    applicationContext,
                    systemInstruction = "Lokalny test czterech tur, zwięzłe odpowiedzi po polsku, " +
                        "bez połączeń telefonicznych i bez narzędzi.",
                    modelId = DialogueModelId.GEMMA_4_E2B,
                )
            },
            worker = modelWorker,
            onDone = { result ->
                runOnUiThread {
                    if (isFinishing || isDestroyed || modelProbe !== candidateRef ||
                        runToken != token) return@runOnUiThread
                    finishLocalModelTest(
                        if (result.status == S8LocalModelFourTurnProbe.Status.PASS)
                            R.string.s8_lab_model_four_turn_pass
                        else R.string.s8_lab_model_four_turn_failed,
                        result.completedTurns,
                    )
                }
            },
        )
        candidateRef = candidate
        modelProbe = candidate
        val timeout = Runnable {
            if (modelProbe === candidate && runToken == token) {
                candidate.close()
                finishLocalModelTest(R.string.s8_lab_model_four_turn_timeout, 0)
            }
        }
        modelTimeout = timeout
        handler.postDelayed(timeout, 300_000L)
        try { candidate.start() } catch (_: Throwable) {
            finishLocalModelTest(R.string.s8_lab_model_four_turn_failed, 0)
        }
    }

    // The listener above may run immediately for a short/error response.
    private var candidateRef: S8LocalModelFourTurnProbe? = null

    private fun finishLocalModelTest(message: Int, turns: Int) {
        modelTimeout?.let { handler.removeCallbacks(it) }
        modelTimeout = null
        modelProbe?.close()
        modelProbe = null
        candidateRef = null
        startButton.isEnabled = true
        speechButton.isEnabled = true
        modelButton.isEnabled = S8LabModelReadiness.inspectPinnedE2B(this) ==
            S8LabModelReadiness.State.FILE_PRESENT_UNVERIFIED_INFERENCE
        stopButton.isEnabled = false
        status.text = getString(message)
        details.text = getString(R.string.s8_lab_model_four_turn_count, turns)
    }

    private fun stopSyntheticSession() {
        val active = lab
        val speech = speechProbe
        val model = modelProbe
        if (active == null && speech == null && model == null) return
        lab = null
        speechProbe = null
        modelProbe = null
        candidateRef = null
        modelTimeout?.let { handler.removeCallbacks(it) }
        modelTimeout = null
        ++runToken
        model?.close()
        active?.close() // Closes app-owned UDP and coordinator; no Telecom action.
        speech?.close() // Cancels Android STT / local TTS, no background probe.
        startButton.isEnabled = true
        speechButton.isEnabled = true
        modelButton.isEnabled = S8LabModelReadiness.inspectPinnedE2B(this) ==
            S8LabModelReadiness.State.FILE_PRESENT_UNVERIFIED_INFERENCE
        stopButton.isEnabled = false
        status.text = getString(R.string.s8_voip_lab_stopped)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 8111 && !isFinishing && !isDestroyed) {
            status.text = getString(R.string.s8_lab_model_four_turn_permission)
            return
        }
        if (requestCode == 8110 && !isFinishing && !isDestroyed) {
            // Permission grant is not approval to start. Explicit second tap.
            status.text = getString(R.string.s8_voip_speech_permission_result)
            return
        }
        if (requestCode == 8108 && !isFinishing && !isDestroyed) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                beginSyntheticSession()
            } else {
                status.text = getString(R.string.s8_voip_lab_blocked)
            }
        }
    }

    private fun isPhoneIdle(): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val telecom = getSystemService(TelecomManager::class.java)
            val audio = getSystemService(AudioManager::class.java)
            telecom != null && !telecom.isInCall && !telecom.isInManagedCall &&
                audio != null && audio.mode != AudioManager.MODE_IN_CALL &&
                audio.mode != AudioManager.MODE_IN_COMMUNICATION
        }.getOrDefault(false)
    }

    override fun onStop() {
        // Navigating away is TAKE OVER: leave no media/background lab running.
        stopSyntheticSession()
        super.onStop()
    }

    override fun onDestroy() {
        stopSyntheticSession()
        worker.shutdownNow()
        modelWorker.shutdown()
        super.onDestroy()
    }
}
