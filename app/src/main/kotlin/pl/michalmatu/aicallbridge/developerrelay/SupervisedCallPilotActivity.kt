package pl.michalmatu.aicallbridge.developerrelay

import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason

import pl.michalmatu.aicallbridge.conversation.ConversationPreparation

import pl.michalmatu.aicallbridge.conversation.SupervisedConversationScenario

import pl.michalmatu.aicallbridge.conversation.AndroidConversationRuntime

import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import pl.michalmatu.aicallbridge.R
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState

/**
 * Minimum operator-supervised call product. NEVER dials, changes the default
 * dialer, changes an account, discloses identity, or disconnects a cellular call.
 * It only attaches the frozen Shizuku RX/TX path to a call the operator already
 * started and executes up to three reviewed, effect-free dialogue turns.
 */
class SupervisedCallPilotActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var gemmaMode: CheckBox
    private lateinit var modelStatus: TextView
    private var returnedFromDialer = false
    private val allowed = AtomicBoolean(false)
    private var session: AndroidConversationRuntime.SessionControl? = null
    private val routeHandler = Handler(Looper.getMainLooper())
    private var pendingRouteProbe: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = getString(R.string.pilot_ui_ready)
        }
        startButton = Button(this).apply {
            text = getString(R.string.pilot_ui_start)
            setOnClickListener { confirmAttach() }
        }
        stopButton = Button(this).apply {
            text = getString(R.string.pilot_ui_stop)
            isEnabled = false
            setOnClickListener { stopAudio(ConversationStopReason.OPERATOR_STOP) }
        }

        gemmaMode = CheckBox(this).apply {
            text = getString(R.string.pilot_ui_gemma_mode)
            // The audio script works without the 2.59 GB model; prefer it until ready.
            isChecked = AndroidGemma4ModelReadiness.check(this@SupervisedCallPilotActivity).state ==
                Gemma4ModelReadinessState.READY
        }
        modelStatus = TextView(this).apply { textSize = 14f }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(TextView(this@SupervisedCallPilotActivity).apply {
                text = getString(R.string.pilot_ui_title)
                textSize = 22f
            })
            addView(TextView(this@SupervisedCallPilotActivity).apply {
                text = getString(R.string.pilot_ui_instructions)
            })
            addView(Button(this@SupervisedCallPilotActivity).apply {
                text = getString(R.string.pilot_ui_open_dialer)
                setOnClickListener {
                    returnedFromDialer = true
                    runCatching { startActivity(Intent(Intent.ACTION_DIAL)) }
                        .onFailure {
                            returnedFromDialer = false
                            status.text = getString(R.string.pilot_ui_dialer_failed)
                        }
                }
            })
            addView(gemmaMode)
            addView(modelStatus)
            addView(Button(this@SupervisedCallPilotActivity).apply {
                text = getString(R.string.pilot_ui_gemma_download)
                setOnClickListener { finish() }
            })
            addView(startButton)
            addView(stopButton)
            addView(status)
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        if (!::modelStatus.isInitialized) return
        refreshModelStatus()
        if (returnedFromDialer && !allowed.get()) {
            returnedFromDialer = false
            when (preflight()) {
                null -> status.text =
                    getString(R.string.pilot_ui_dialer_return_ready)
                SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY -> status.text =
                    getString(R.string.pilot_ui_dialer_return_route)
                else -> status.text = getString(R.string.pilot_ui_dialer_return_unknown)
            }
        }
    }

    private fun refreshModelStatus() {
        val readiness = AndroidGemma4ModelReadiness.check(this)
        modelStatus.text = when (readiness.state) {
            Gemma4ModelReadinessState.READY -> getString(R.string.pilot_ui_model_ready)
            Gemma4ModelReadinessState.MISSING ->
                getString(R.string.pilot_ui_model_missing)
            Gemma4ModelReadinessState.INVALID ->
                getString(R.string.pilot_ui_model_invalid, readiness.reason ?: "unknown")
        }
    }

    private fun preflight(): SupervisedCallPilotFailure? =
        SupervisedCallReadinessDiagnostics.capture(this).failure

    private fun cancelRouteProbe() {
        pendingRouteProbe?.let(routeHandler::removeCallbacks)
        pendingRouteProbe = null
    }

    /** Retry an Android routing transition only; never override the frozen media gate. */
    private fun awaitAudioRoute() {
        if (pendingRouteProbe != null) return
        status.text = getString(R.string.pilot_ui_checking_route)
        val start = SystemClock.elapsedRealtime()
        val probe = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) {
                    pendingRouteProbe = null
                    return
                }
                val snapshot = SupervisedCallReadinessDiagnostics.capture(this@SupervisedCallPilotActivity)
                val failure = snapshot.failure
                if (failure == null) {
                    pendingRouteProbe = null
                    SupervisedCallReadinessDiagnostics.write(this@SupervisedCallPilotActivity, snapshot)
                    confirmAttach() // Reuse the original START tap after the OS route stabilizes.
                } else if (SupervisedCallPilotPolicy.shouldAwaitAudioRoute(
                        failure, SystemClock.elapsedRealtime() - start)) {
                    routeHandler.postDelayed(this, 300L)
                } else {
                    pendingRouteProbe = null
                    val saved = SupervisedCallReadinessDiagnostics.write(
                        this@SupervisedCallPilotActivity, snapshot)
                    if (failure == SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY) {
                        status.text = getString(R.string.pilot_ui_route_failed, snapshot.audioModeLabel,
                            getString(if (saved) R.string.pilot_ui_diagnostic_saved else R.string.pilot_ui_diagnostic_failed))
                    } else {
                        confirmAttach() // Displays the current permission/call error without a dial.
                    }
                }
            }
        }
        pendingRouteProbe = probe
        routeHandler.postDelayed(probe, 250L)
    }

    private fun confirmAttach() {
        if (allowed.get()) {
            status.text = getString(R.string.pilot_ui_already_running)
            return
        }
        when (preflight()) {
            SupervisedCallPilotFailure.PERMISSION_REQUIRED -> {
                status.text = getString(R.string.pilot_ui_permission_required)
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE),
                    REQUEST_PERMISSIONS,
                )
                return
            }
            SupervisedCallPilotFailure.SHIZUKU_NOT_READY -> {
                status.text = getString(R.string.pilot_ui_shizuku_missing)
                return
            }
            SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL -> {
                status.text = getString(R.string.pilot_ui_call_missing)
                return
            }
            SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY -> {
                awaitAudioRoute()
                return
            }
            null -> Unit
        }
        if (gemmaMode.isChecked &&
            AndroidGemma4ModelReadiness.check(this).state != Gemma4ModelReadinessState.READY
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.pilot_ui_model_dialog_title)
                .setMessage(R.string.pilot_ui_model_dialog_help)
                .setPositiveButton(R.string.pilot_ui_model_dialog_download) { _, _ -> finish() }
                .setNeutralButton(R.string.pilot_ui_model_dialog_script) { _, _ ->
                    gemmaMode.isChecked = false
                    confirmAttach()
                }
                .setNegativeButton(R.string.skills_ui_cancel, null)
                .show()
            return
        }
        // The user's START tap is the approval for this effect-free pilot.
        startPilot(gemmaMode.isChecked)
    }

    private fun startPilot(approvedGemmaMode: Boolean) {
        cancelRouteProbe()
        if (approvedGemmaMode &&
            AndroidGemma4ModelReadiness.check(this).state != Gemma4ModelReadinessState.READY
        ) {
            status.text = getString(R.string.pilot_ui_model_not_ready)
            return
        }
        if (preflight() != null || !allowed.compareAndSet(false, true)) {
            status.text = getString(R.string.pilot_ui_conditions_changed)
            return
        }
        startButton.isEnabled = false
        stopButton.isEnabled = true
        status.text = getString(R.string.pilot_ui_listening,
            if (approvedGemmaMode) "Gemma 4" else getString(R.string.pilot_ui_script_label))
        val sessionId = "pilot-" + UUID.randomUUID().toString()
        try {
            session = AndroidConversationRuntime.run(
                context = this,
                sessionId = sessionId,
                runConfiguration = ConversationRunConfiguration.ScriptAssisted(AudioEngineProfile.STABLE, ConversationPreparation.supervised()),
                maxTurns = 3,
                scenario = SupervisedConversationScenario(this, approvedGemmaMode),
                liveAuthorization = {
                    allowed.get() && preflight() == null
                },
            ) { result ->
                val report = result.report
                allowed.set(false)
                session = null
                startButton.isEnabled = true
                stopButton.isEnabled = false
                val success = report.lineSequence().any {
                    it == "chat_relay_live_call_success=true"
                }
                val reason = report.lineSequence()
                    .firstOrNull { it.startsWith("failure_reason=") }
                    ?.substringAfter('=')
                status.text = if (success) {
                    getString(R.string.pilot_ui_test_finished)
                } else {
                    getString(R.string.pilot_ui_test_stopped, reason ?: getString(R.string.pilot_ui_report_hint))
                }
            }
        } catch (error: Throwable) {
            allowed.set(false)
            session = null
            startButton.isEnabled = true
            stopButton.isEnabled = false
            status.text = getString(R.string.pilot_ui_start_failed, error.javaClass.simpleName)
        }
    }

    private fun stopAudio(reason: ConversationStopReason) {
        allowed.set(false)
        session?.stopAudio(reason)
        stopButton.isEnabled = false
        status.text = getString(R.string.pilot_ui_stopping)
    }

    override fun onStop() {
        cancelRouteProbe()
        // A foreground UI is our operator supervision; no unattended audio on Home/dialer.
        if (allowed.get()) stopAudio(ConversationStopReason.SESSION_CANCELLED)
        super.onStop()
    }

    override fun onDestroy() {
        cancelRouteProbe()
        if (allowed.get() || session != null) stopAudio(ConversationStopReason.SERVICE_DESTROYED)
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 7003
    }
}
