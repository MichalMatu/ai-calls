package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.conversation.ConversationChatPanel
import pl.michalmatu.aicallbridge.conversation.ScenarioLibraryActivity

import android.Manifest
import android.app.Activity
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.media.MediaPlayer
import android.telecom.TelecomManager
import java.io.File
import org.json.JSONObject
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder
import pl.michalmatu.aicallbridge.UiAccordionSection
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import pl.michalmatu.aicallbridge.ui.UiSelect
import pl.michalmatu.aicallbridge.R
import android.view.View
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView

class CampaignToolActivity : UiChromeActivity(AppTab.HOME) {
    override fun toolbarTitle(): String = getString(R.string.camp_ui_title)
    private lateinit var authorizationStore: CampaignAuthorizationStore
    private lateinit var runtimeStatusStore: CampaignRuntimeStatusStore
    private lateinit var statusView: TextView
    private lateinit var liveStatusView: TextView
    private lateinit var diagnosticStatus: TextView
    private lateinit var lastExecutionStatus: TextView
    private lateinit var actionFeedback: TextView
    private lateinit var dialerRoleStatus: TextView
    private lateinit var disableEvidenceButton: Button
    private var evidenceSelectionTouched = false
    private var evidenceProgrammaticChange = false
    private val statusHandler = Handler(Looper.getMainLooper())
    private val lastExecutionRefresh = object : Runnable {
        override fun run() {
            if (::lastExecutionStatus.isInitialized && ::runtimeStatusStore.isInitialized) {
                refreshTaskStatus()
            }
            statusHandler.postDelayed(this, 1_000L)
        }
    }
    private lateinit var recordEvidenceChoice: CheckBox
    private var pendingAction: PendingAction? = null
    private var requestedStartMode = CampaignDialogueMode.SCRIPT_GEMMA
    private var continueStartAfterRole = false
    private var playback: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authorizationStore = CampaignAuthorizationStore(this)
        runtimeStatusStore = CampaignRuntimeStatusStore(this)
        requestedStartMode = savedInstanceState?.getString("start_mode")
            ?.let(CampaignDialogueMode::fromWire) ?: CampaignDialogueMode.SCRIPT_GEMMA
        pendingAction = savedInstanceState?.getString("pending_action")?.let {
            runCatching { PendingAction.valueOf(it) }.getOrNull()
        }
        continueStartAfterRole = savedInstanceState?.getBoolean("continue_start_after_role") ?: false
        evidenceSelectionTouched = savedInstanceState?.getBoolean("recording_edited") ?: false

        statusView = TextView(this).apply {
            textSize = 16f
            setTextColor(UiDesign.ink)
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            tag = "campaign-task-status"
        }
        liveStatusView = UiDesign.caption(this, "").apply {
            tag = "campaign-live-runtime"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        actionFeedback = UiDesign.caption(this, "").apply {
            tag = "campaign-action-feedback"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextIsSelectable(true)
        }
        dialerRoleStatus = UiDesign.caption(this, "").apply {
            tag = "campaign-dialer-role-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        diagnosticStatus = UiDesign.caption(this, "").apply {
            tag = "campaign-attempt-status"
            setTextIsSelectable(true)
        }
        lastExecutionStatus = UiDesign.caption(this, "").apply {
            tag = "campaign-execution-summary"
            textSize = 14f
            setTextColor(UiDesign.ink)
            setTextIsSelectable(true)
            setPadding(UiDesign.dp(this@CampaignToolActivity, 12),
                UiDesign.dp(this@CampaignToolActivity, 10),
                UiDesign.dp(this@CampaignToolActivity, 12),
                UiDesign.dp(this@CampaignToolActivity, 10))
            background = UiDesign.surfaceDrawable(this@CampaignToolActivity)
        }
        val dialoguePicker = Spinner(this).apply {
            tag = "campaign-dialogue-picker"
            UiSelect.setOptions(this, listOf(
                getString(R.string.campaign_ui_script_model),
                getString(R.string.campaign_ui_gemma_direct)))
            setSelection(if (requestedStartMode == CampaignDialogueMode.GEMMA_DIRECT) 1 else 0)
        }

        val accountScope = if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED) CampaignAccountScope.currentVoiceSubscription(this) else null
        val existing = authorizationStore.load()
        val accepted = if (accountScope != null && CampaignAuthorizationPolicy.matchesExactTask(
            existing, ClirCampaignSpec.enableRequest(accountScope),
        )) existing else null
        recordEvidenceChoice = CheckBox(this).apply {
            text = getString(R.string.camp_ux05_record_local)
            isChecked = if (evidenceSelectionTouched)
                savedInstanceState?.getBoolean("recording_checked") ?: false
            else accepted?.recordCallEvidence == true
            setOnCheckedChangeListener { _, _ ->
                if (!evidenceProgrammaticChange) evidenceSelectionTouched = true
            }
        }
        val callRoleButton = Button(this).apply {
            text = getString(R.string.camp_ui_role_button)
            setOnClickListener { requestCallRole() }
        }
        val startButton = Button(this).apply {
            text = getString(R.string.campaign_ui_start)
            setOnClickListener {
                // Only dialogue strategy differs; grant, SIM, permissions and STOP stay identical.
                requestedStartMode = if (dialoguePicker.selectedItemPosition == 1)
                    CampaignDialogueMode.GEMMA_DIRECT else CampaignDialogueMode.SCRIPT_GEMMA
                ensurePermissionsThen(PendingAction.START)
            }
        }
        val supervisedAudioButton = Button(this).apply {
            text = getString(R.string.camp_ux05_attach_ai)
            // The tap is the explicit request. Android permissions and actual
            // live-call/media gates are checked by the service; no redundant dialog.
            setOnClickListener {
                ensurePermissionsThen(PendingAction.SUPERVISED_AUDIO)
            }
        }
        val stopSupervisedAudioButton = Button(this).apply {
            text = getString(R.string.camp_ui_stop)
            setOnClickListener {
                startService(
                    Intent(this@CampaignToolActivity, AppOwnedCampaignService::class.java)
                        .setAction(AppOwnedCampaignService.ACTION_STOP_ALL_AI),
                )
                actionFeedback.text = getString(R.string.campaign_ui_stopping)
            }
        }
        disableEvidenceButton = Button(this).apply {
            text = getString(R.string.camp_ui_disable_record)
            setOnClickListener {
                val grant = authorizationStore.disableEvidenceRecording()
                actionFeedback.text = if (grant == null) getString(R.string.camp_ui_no_campaign) else
                    getString(R.string.camp_ux05_record_disabled)
                if (grant != null) evidenceSelectionTouched = false
                refreshTaskStatus()
            }
        }
        val playbackButton = Button(this).apply {
            text = getString(R.string.camp_ui_play_ai_short)
            setOnClickListener { playLastRecordedAudio("tx") }
        }
        val playbackRxButton = Button(this).apply {
            text = getString(R.string.camp_ui_play_rx_short)
            setOnClickListener { playLastRecordedAudio("rx") }
        }
        val transcriptButton = Button(this).apply {
            text = getString(R.string.camp_ui_transcript_short)
            setOnClickListener { showLatestTranscript() }
        }
        val revokeButton = Button(this).apply {
            text = getString(R.string.camp_ui_cancel_task)
            setOnClickListener {
                val revoked = authorizationStore.revoke(System.currentTimeMillis())
                actionFeedback.text = if (revoked == null) {
                    getString(R.string.camp_ui_no_accepted)
                } else {
                    val requested = runCatching {
                        startService(Intent(this@CampaignToolActivity,
                            AppOwnedCampaignService::class.java)
                            .setAction(AppOwnedCampaignService.ACTION_CANCEL_OUTGOING_TASK))
                    }.isSuccess
                    getString(if (requested) R.string.camp_ui_revoked
                        else R.string.camp_ux06_cancel_dispatch_failed)
                }
                refreshTaskStatus()
            }
        }

        // Give START, STOP and task revocation distinct visual hierarchy.
        UiDesign.styleAction(startButton, UiActionTone.PRIMARY)
        UiDesign.styleAction(stopSupervisedAudioButton, UiActionTone.DANGER)
        UiDesign.styleAction(revokeButton, UiActionTone.REVOCATION)

        // Configuration and supervised attach are direct, described actions rather than
        // collapsed single-button categories. Keep the multi-action evidence accordion.
        val dialerSection = UiDesign.card(this).apply {
            tag = "campaign-dialer-card"
            addView(UiDesign.heading(this@CampaignToolActivity, getString(R.string.camp_ui_dialer)),
                UiDesign.spaceAfter(this@CampaignToolActivity, 8))
            addView(UiDesign.caption(this@CampaignToolActivity, getString(R.string.camp_ux06_dialer_explanation)),
                UiDesign.spaceAfter(this@CampaignToolActivity, 10))
            addView(dialerRoleStatus, UiDesign.spaceAfter(this@CampaignToolActivity, 10))
            addView(callRoleButton)
        }
        val directSkillsSection = UiDesign.card(this).apply {
            tag = "campaign-skills-card"
            addView(UiDesign.heading(this@CampaignToolActivity, getString(R.string.camp_ui_skills)),
                UiDesign.spaceAfter(this@CampaignToolActivity, 8))
            addView(UiDesign.caption(this@CampaignToolActivity, getString(R.string.camp_ux05_prep_caption)),
                UiDesign.spaceAfter(this@CampaignToolActivity, 10))
            addView(UiDesign.action(this@CampaignToolActivity, getString(R.string.camp_ux05_prep_link)) {
                startActivity(Intent(this@CampaignToolActivity,
                    pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity::class.java))
            })
        }
        val supervisedSection = UiDesign.card(this).apply {
            tag = "campaign-supervised-card"
            addView(UiDesign.heading(this@CampaignToolActivity, getString(R.string.camp_ui_supervised)),
                UiDesign.spaceAfter(this@CampaignToolActivity, 8))
            addView(UiDesign.caption(this@CampaignToolActivity, getString(R.string.camp_ux06_supervised_explanation)),
                UiDesign.spaceAfter(this@CampaignToolActivity, 10))
            addView(supervisedAudioButton)
        }
        val evidenceSection = UiAccordionSection(this, "evidence", getString(R.string.camp_ui_evidence)).apply {
            addControl(disableEvidenceButton)
            addControl(playbackButton)
            addControl(playbackRxButton)
            addControl(transcriptButton)
            addLabel(getString(R.string.camp_ui_play_offcall))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@CampaignToolActivity, 20), UiDesign.dp(this@CampaignToolActivity, 20), UiDesign.dp(this@CampaignToolActivity, 20), UiDesign.dp(this@CampaignToolActivity, 28))
            addView(UiDesign.caption(this@CampaignToolActivity,
                getString(R.string.camp_ui_intro)
            ), UiDesign.spaceAfter(this@CampaignToolActivity, 16))
            addView(UiDesign.heading(this@CampaignToolActivity,
                getString(R.string.campaign_ui_task_status)).apply { textSize = 17f },
                UiDesign.spaceAfter(this@CampaignToolActivity, 8))
            addView(statusView, UiDesign.spaceAfter(this@CampaignToolActivity, 10))
            addView(liveStatusView, UiDesign.spaceAfter(this@CampaignToolActivity, 10))
            addView(actionFeedback, UiDesign.spaceAfter(this@CampaignToolActivity, 10))
            addView(lastExecutionStatus, UiDesign.spaceAfter(this@CampaignToolActivity, 14))
            addView(UiDesign.card(this@CampaignToolActivity).apply {
                tag = "campaign-primary-actions"
                addView(UiDesign.heading(this@CampaignToolActivity, getString(R.string.camp_ui_enable)).apply {
                    textSize = 18f
                }, UiDesign.spaceAfter(this@CampaignToolActivity, 8))
                addView(UiDesign.caption(this@CampaignToolActivity,
                    getString(R.string.camp_ux05_target, ClirCampaignSpec.TARGET)
                ), UiDesign.spaceAfter(this@CampaignToolActivity, 12))
                addView(UiDesign.caption(this@CampaignToolActivity,
                    getString(R.string.campaign_ui_dialogue_mode)),
                    UiDesign.spaceAfter(this@CampaignToolActivity, 8))
                addView(dialoguePicker, UiDesign.spaceAfter(this@CampaignToolActivity, 12))
                addView(recordEvidenceChoice, UiDesign.spaceAfter(this@CampaignToolActivity, 4))
                addView(UiDesign.caption(this@CampaignToolActivity, getString(R.string.camp_ux06_recording_scope)),
                    UiDesign.spaceAfter(this@CampaignToolActivity, 12))
                addView(startButton, UiDesign.spaceAfter(this@CampaignToolActivity, 12))
                addView(stopSupervisedAudioButton, UiDesign.spaceAfter(this@CampaignToolActivity, 12))
                addView(revokeButton, UiDesign.spaceAfter(this@CampaignToolActivity, 8))
                addView(UiDesign.caption(this@CampaignToolActivity,
                    getString(R.string.camp_ui_actions_help)))
            }, UiDesign.spaceAfter(this@CampaignToolActivity, 16))
            addView(UiDesign.action(this@CampaignToolActivity,
                getString(R.string.camp_ui_engines)) {
                startActivity(Intent(this@CampaignToolActivity, AudioEngineSettingsActivity::class.java))
            }, UiDesign.spaceAfter(this@CampaignToolActivity, 16))
            addView(UiDesign.card(this@CampaignToolActivity).apply {
                tag = "campaign-scenario-library-card"
                addView(UiDesign.heading(this@CampaignToolActivity,
                    getString(R.string.scenario_ui_title)),
                    UiDesign.spaceAfter(this@CampaignToolActivity, 8))
                addView(UiDesign.caption(this@CampaignToolActivity,
                    getString(R.string.scenario_ui_campaign_hint)),
                    UiDesign.spaceAfter(this@CampaignToolActivity, 10))
                addView(UiDesign.action(this@CampaignToolActivity,
                    getString(R.string.scenario_ui_open)) {
                    startActivity(Intent(this@CampaignToolActivity,
                        ScenarioLibraryActivity::class.java))
                })
            }, UiDesign.spaceAfter(this@CampaignToolActivity, 16))
            addView(directSkillsSection)
            addView(dialerSection)
            addView(supervisedSection)
            addView(evidenceSection)
            addView(UiDesign.card(this@CampaignToolActivity).apply {
                tag = "campaign-attempt-details"
                addView(UiDesign.heading(this@CampaignToolActivity,
                    getString(R.string.campaign_ui_attempt_details)),
                    UiDesign.spaceAfter(this@CampaignToolActivity, 8))
                addView(UiDesign.caption(this@CampaignToolActivity,
                    getString(R.string.campaign_ui_last_history)),
                    UiDesign.spaceAfter(this@CampaignToolActivity, 8))
                addView(diagnosticStatus)
            })
        }

        // Same live, read-only chat on the campaign screen; all essential
        // START and STOP controls remain available in the scrollable area.
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(this@CampaignToolActivity).apply {
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            ))
            addView(ConversationChatPanel(this@CampaignToolActivity, initiallyExpanded = false))
        })
        refreshTaskStatus()
        // Explicit primary-screen tap accepts the displayed task through the same scope,
        // permission and Android-role checks as the existing START control.
        // A configuration/activity recreation must never start a second attempt.
        if (savedInstanceState == null &&
            intent.getBooleanExtra(EXTRA_START_AUTHORIZED_CAMPAIGN, false)
        ) {
            requestedStartMode = CampaignDialogueMode.SCRIPT_GEMMA
            ensurePermissionsThen(PendingAction.START)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("start_mode", requestedStartMode.name)
        pendingAction?.let { outState.putString("pending_action", it.name) }
        outState.putBoolean("continue_start_after_role", continueStartAfterRole)
        outState.putBoolean("recording_edited", evidenceSelectionTouched)
        outState.putBoolean("recording_checked", recordEvidenceChoice.isChecked)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        refreshTaskStatus()
        statusHandler.removeCallbacks(lastExecutionRefresh)
        statusHandler.post(lastExecutionRefresh)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(lastExecutionRefresh)
        super.onPause()
    }

    override fun onDestroy() {
        playback?.release()
        playback = null
        super.onDestroy()
    }

    private fun latestEvidenceDirectory(): File? =
        File(filesDir, CallSessionEvidenceRecorder.ROOT)
            .listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }

    private fun showLatestTranscript() {
        val timeline = latestEvidenceDirectory()?.resolve("timeline.jsonl")
        if (timeline?.isFile != true) {
            actionFeedback.text = getString(R.string.camp_ux05_transcript_absent)
            return
        }
        val lines = runCatching {
            timeline.useLines { input ->
                input.mapNotNull { line ->
                    runCatching {
                        val event = JSONObject(line)
                        val type = event.optString("type")
                        if (type != "COUNTERPART_STT" && type != "AGENT_APPROVED_TEXT") {
                            null
                        } else {
                            val name = if (type == "COUNTERPART_STT") getString(R.string.camp_ux05_counterpart) else "AI"
                            val seconds = event.optLong("elapsed_ms") / 1_000L
                            getString(R.string.camp_ux05_transcript_line, seconds, event.optInt("turn"), name, event.optString("text"))
                        }
                    }.getOrNull()
                }.toList()
            }
        }.getOrElse {
            actionFeedback.text = getString(R.string.camp_ux05_transcript_read_failed)
            return
        }
        val transcript = lines.joinToString("\n\n")
            .ifEmpty { getString(R.string.camp_ux05_transcript_no_turns) }
        val view = TextView(this).apply {
            text = transcript
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(UiDesign.dp(this@CampaignToolActivity, 32), UiDesign.dp(this@CampaignToolActivity, 20), UiDesign.dp(this@CampaignToolActivity, 32), UiDesign.dp(this@CampaignToolActivity, 20))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.camp_ux05_transcript_title)
            .setView(ScrollView(this).apply { addView(view) })
            .setPositiveButton(R.string.camp_ux05_refresh) { _, _ -> showLatestTranscript() }
            .setNegativeButton(R.string.camp_ux05_close, null)
            .show()
    }

    private fun playLastRecordedAudio(direction: String) {
        require(direction == "rx" || direction == "tx")
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            actionFeedback.text = getString(R.string.camp_ux05_play_permission)
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        if (runCatching { telecom == null || telecom.isInCall }.getOrDefault(true)) {
            actionFeedback.text = getString(R.string.camp_ux05_play_off_call)
            return
        }
        val audio = latestEvidenceDirectory()?.listFiles()?.filter {
            it.name.matches(Regex("turn-[0-9]{2}-$direction\\.wav"))
        }?.maxByOrNull { it.name }
        if (audio == null) {
            actionFeedback.text = getString(R.string.camp_ux05_audio_absent)
            return
        }
        runCatching {
            playback?.release()
            val newPlayer = MediaPlayer()
            playback = newPlayer
            newPlayer.setDataSource(audio.absolutePath)
            newPlayer.setOnCompletionListener {
                it.release()
                if (playback === it) playback = null
            }
            newPlayer.prepare()
            newPlayer.start()
            actionFeedback.text = getString(R.string.camp_ux05_audio_file,
                if (direction == "rx") getString(R.string.camp_ux05_counterpart_audio) else "AI", audio.name)
        }.onFailure {
            playback?.release()
            playback = null
            actionFeedback.text = getString(R.string.camp_ux05_play_failed)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMPAIGN_PERMISSIONS) return

        val allGranted = permissions.indices.all { index ->
            grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
        }
        val action = pendingAction
        pendingAction = null
        if (!allGranted) {
            actionFeedback.text = getString(R.string.camp_ux05_permission_denied)
            return
        }
        when (action) {
            PendingAction.START -> startAuthorizedCampaign()
            PendingAction.SUPERVISED_AUDIO -> startSupervisedAudio()
            null -> Unit
        }
    }

    private fun ensurePermissionsThen(action: PendingAction) {
        // Task acceptance and execution share the same real Android permission checks.
        val required = when (action) {
            PendingAction.START -> REQUIRED_PERMISSIONS
            PendingAction.SUPERVISED_AUDIO -> listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
            )
        }
        val missing = required.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            when (action) {
                    PendingAction.START -> startAuthorizedCampaign()
                PendingAction.SUPERVISED_AUDIO -> startSupervisedAudio()
            }
            return
        }
        pendingAction = action
        requestPermissions(missing.toTypedArray(), REQUEST_CAMPAIGN_PERMISSIONS)
    }

    private fun requestCallRole(continueWithStart: Boolean = false) {
        continueStartAfterRole = continueWithStart
        // Android 13 notification permission is optional for placing a call.
        // Denial may hide our optional notification, never block the platform dialer role.
        requestPlatformCallRole()
    }

    private fun requestPlatformCallRole() {
        val manager = getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            actionFeedback.text = getString(R.string.camp_ux05_role_unavailable)
            return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            actionFeedback.text = getString(R.string.camp_ux05_role_already)
            if (continueStartAfterRole) {
                continueStartAfterRole = false
                ensurePermissionsThen(PendingAction.START)
            }
            return
        }
        // Android alone grants/revokes this role. Never change Google Phone silently.
        actionFeedback.text = getString(R.string.camp_ux05_role_request)
        startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQUEST_DIALER_ROLE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DIALER_ROLE) {
            val roleActive = AndroidTelecomOwnedCallController.canControl(this)
            actionFeedback.text = if (roleActive) {
                getString(R.string.camp_ux05_role_active)
            } else getString(R.string.camp_ux05_role_denied)
            val shouldStart = continueStartAfterRole
            continueStartAfterRole = false
            if (roleActive && shouldStart) ensurePermissionsThen(PendingAction.START)
        }
    }

    private fun startSupervisedAudio() {
        actionFeedback.text = getString(R.string.camp_ux05_supervised_start)
        startForegroundService(
            Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_START_SUPERVISED_AUDIO),
        )
    }

    private fun startAuthorizedCampaign() {
        val accountScope = CampaignAccountScope.currentVoiceSubscription(this)
        if (accountScope == null) {
            actionFeedback.text = getString(R.string.camp_ux05_sim_bind_failed)
            return
        }

        if (!AndroidTelecomOwnedCallController.canControl(this)) {
            // Android requires an explicit OS choice before exposing an exact Call handle.
            requestCallRole(continueWithStart = true)
            return
        }

        val report = CampaignAgentControl.run(
            this, requestStart = true, dialogueMode = requestedStartMode,
            recordCallEvidence = recordEvidenceChoice.isChecked,
        )
        actionFeedback.text = if (report.contains("campaign_agent_start=DISPATCHED_TO_INTERNAL_SERVICE")) {
            getString(R.string.camp_ux05_campaign_start,
                if (requestedStartMode == CampaignDialogueMode.GEMMA_DIRECT) "Gemma Direct" else getString(R.string.camp_ux05_script_model))
        } else getString(R.string.camp_ux05_campaign_failed)

    }

    private fun refreshTaskStatus() {
        val grant = authorizationStore.load()
        val taskStatus = when {
            grant == null -> getString(R.string.camp_ui_no_task)
            grant.revokedAtEpochMs != null -> getString(R.string.camp_ui_cancelled)
            else -> getString(R.string.camp_ui_accepted)
        }
        val evidenceStatus = if (grant?.recordCallEvidence == true &&
            grant.revokedAtEpochMs == null) {
            getString(R.string.camp_ui_evidence_on)
        } else getString(R.string.camp_ui_evidence_off)
        val runtimeStatus = runtimeStatusStore.load()
        statusView.text = taskStatus
        liveStatusView.text = getString(CampaignLiveUiLabels.title(AppOwnedCampaignService.liveUiPhase()))
        diagnosticStatus.text = listOfNotNull(
            getString(R.string.camp_ux06_diagnostic_recording, evidenceStatus),
            runtimeStatus?.let { getString(R.string.camp_ux06_diagnostic_event, it) },
        ).joinToString("\n\n")
        refreshEvidenceControls(grant)
        refreshDialerStatus()
        updateLastExecutionStatus()
    }

    private fun refreshEvidenceControls(grant: CampaignAuthorizationGrant?) {
        val scope = if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED) CampaignAccountScope.currentVoiceSubscription(this)
        else null
        val matched = scope != null && CampaignAuthorizationPolicy.matchesExactTask(
            grant, ClirCampaignSpec.enableRequest(scope))
        val recorded = matched && grant?.recordCallEvidence == true
        disableEvidenceButton.isEnabled = recorded
        if (!evidenceSelectionTouched) {
            evidenceProgrammaticChange = true
            try { recordEvidenceChoice.isChecked = recorded }
            finally { evidenceProgrammaticChange = false }
        }
    }

    private fun refreshDialerStatus() {
        val manager = getSystemService(RoleManager::class.java)
        dialerRoleStatus.text = getString(when {
            manager == null || !manager.isRoleAvailable(RoleManager.ROLE_DIALER) ->
                R.string.camp_ux05_role_unavailable
            AndroidTelecomOwnedCallController.canControl(this) ->
                R.string.camp_ux05_role_active
            else -> R.string.camp_ux06_role_inactive
        })
    }

    private fun updateLastExecutionStatus() {
        val record = runtimeStatusStore.loadRecord()
        val raw = record?.message
        // Pure, localizable interpretation; raw diagnostics stay on the same screen.
        val screen = CampaignExecutionStatusPresenter.present(
            raw, resources.configuration.locales[0],
        )
        val whenRecorded = record?.recordedAtEpochMs?.let {
            java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
                resources.configuration.locales[0],
            ).format(java.util.Date(it))
        } ?: getString(R.string.camp_ux06_timestamp_unknown)
        lastExecutionStatus.text = getString(R.string.camp_ui_runtime_heading) +
            "\n" + getString(R.string.campaign_ui_last_history) +
            "\n" + getString(R.string.camp_ux06_timestamp_label, whenRecorded) +
            "\n" + screen.summary
        // Raw codes remain available in the existing diagnostic accordion.
    }

    private enum class PendingAction {
        START,
        SUPERVISED_AUDIO,
    }

    companion object {
        const val EXTRA_START_AUTHORIZED_CAMPAIGN =
            "pl.michalmatu.aicallbridge.extra.START_AUTHORIZED_CAMPAIGN"
        private const val REQUEST_CAMPAIGN_PERMISSIONS = 2001
        private const val REQUEST_DIALER_ROLE = 2002
        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        )
    }
}
