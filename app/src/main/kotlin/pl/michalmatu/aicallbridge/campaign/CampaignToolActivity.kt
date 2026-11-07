package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.conversation.ConversationChatPanel

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
    private lateinit var authorizationStore: CampaignAuthorizationStore
    private lateinit var runtimeStatusStore: CampaignRuntimeStatusStore
    private lateinit var statusView: TextView
    private lateinit var diagnosticStatus: TextView
    private lateinit var lastExecutionStatus: TextView
    private val statusHandler = Handler(Looper.getMainLooper())
    private val lastExecutionRefresh = object : Runnable {
        override fun run() {
            if (::lastExecutionStatus.isInitialized && ::runtimeStatusStore.isInitialized) {
                updateLastExecutionStatus()
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


        statusView = TextView(this).apply {
            textSize = 16f
            setTextColor(UiDesign.ink)
            setTextIsSelectable(true)
            tag = "campaign-task-status"
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
            text = "Zapisuj RX/TX i transkrypcje tego zadania (lokalnie)"
            isChecked = accepted?.recordCallEvidence == true
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
            text = "Uruchom AI w trwającej rozmowie"
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
                statusView.text = getString(R.string.campaign_ui_stopping)
            }
        }
        val disableEvidenceButton = Button(this).apply {
            text = getString(R.string.camp_ui_disable_record)
            setOnClickListener {
                val grant = authorizationStore.disableEvidenceRecording()
                statusView.text = if (grant == null) getString(R.string.camp_ui_no_campaign) else
                    "Nagrywanie wyłączone dla kolejnych rozmów. Zadanie pozostaje autoryzowane."
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
                CampaignAgentControl.stopOwnedCampaign(this@CampaignToolActivity)
                statusView.text = if (revoked == null) {
                    getString(R.string.camp_ui_no_accepted)
                } else {
                    getString(R.string.camp_ui_revoked)
                }
            }
        }

        // Give START, STOP and task revocation distinct visual hierarchy.
        UiDesign.styleAction(startButton, UiActionTone.PRIMARY)
        UiDesign.styleAction(stopSupervisedAudioButton, UiActionTone.DANGER)
        UiDesign.styleAction(revokeButton, UiActionTone.REVOCATION)

        // Keep campaign scope, start, STOP and revocation on the primary surface.
        // All previously available setup and diagnostic actions remain one expansion away.
        val dialerSection = UiAccordionSection(this, "dialer", getString(R.string.camp_ui_dialer)).apply {
            addControl(callRoleButton)
        }
        // Optional prompt skills: model instructions, NOT privileged tool permissions.
        // The exact authorized CLIR task, role and STOP remain app-owned.
        val directSkillsSection = UiAccordionSection(this, "direct-prompt-skills", getString(R.string.camp_ui_skills)).apply {
            addLabel("Profile przygotowania są niezależne od zadania i jego uprawnień. Zmiany obowiązują w kolejnej próbie.")
            addControl(Button(this@CampaignToolActivity).apply {
                text = "Skills i dane — wychodzące / przychodzące"
                setOnClickListener { startActivity(Intent(this@CampaignToolActivity,
                    pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity::class.java)) }
            })
        }
        val supervisedSection = UiAccordionSection(this, "supervised", getString(R.string.camp_ui_supervised)).apply {
            addControl(supervisedAudioButton)
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
            addView(TextView(this@CampaignToolActivity).apply {
                text = getString(R.string.camp_ui_title)
                textSize = 22f
            })
            addView(UiDesign.caption(this@CampaignToolActivity,
                getString(R.string.camp_ui_intro)
            ), UiDesign.spaceAfter(this@CampaignToolActivity, 16))
            addView(UiDesign.heading(this@CampaignToolActivity,
                getString(R.string.campaign_ui_task_status)).apply { textSize = 17f },
                UiDesign.spaceAfter(this@CampaignToolActivity, 8))
            addView(statusView, UiDesign.spaceAfter(this@CampaignToolActivity, 10))
            addView(lastExecutionStatus, UiDesign.spaceAfter(this@CampaignToolActivity, 14))
            addView(UiDesign.card(this@CampaignToolActivity).apply {
                tag = "campaign-primary-actions"
                addView(UiDesign.heading(this@CampaignToolActivity, getString(R.string.camp_ui_enable)).apply {
                    textSize = 18f
                }, UiDesign.spaceAfter(this@CampaignToolActivity, 8))
                addView(UiDesign.caption(this@CampaignToolActivity,
                    "Cel: ${ClirCampaignSpec.TARGET} · bieżąca SIM"
                ), UiDesign.spaceAfter(this@CampaignToolActivity, 12))
                addView(UiDesign.caption(this@CampaignToolActivity,
                    getString(R.string.campaign_ui_dialogue_mode)),
                    UiDesign.spaceAfter(this@CampaignToolActivity, 8))
                addView(dialoguePicker, UiDesign.spaceAfter(this@CampaignToolActivity, 12))
                addView(recordEvidenceChoice, UiDesign.spaceAfter(this@CampaignToolActivity, 12))
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
            addView(directSkillsSection)
            addView(dialerSection)
            addView(supervisedSection)
            addView(evidenceSection)
            addView(UiAccordionSection(this@CampaignToolActivity, "campaign-attempt",
                getString(R.string.campaign_ui_attempt_details)).apply {
                addControl(diagnosticStatus)
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
            statusView.text = "Nie ma jeszcze prywatnego transkryptu. Uzbrój zapis przed rozmową."
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
                            val name = if (type == "COUNTERPART_STT") "Rozmówca" else "AI"
                            val seconds = event.optLong("elapsed_ms") / 1_000L
                            "[$seconds s, tura ${event.optInt("turn")}] $name: " +
                                event.optString("text")
                        }
                    }.getOrNull()
                }.toList()
            }
        }.getOrElse {
            statusView.text = "Nie udało się odczytać prywatnego transkryptu."
            return
        }
        val transcript = lines.joinToString("\n\n")
            .ifEmpty { "Brak ukończonych wypowiedzi STT/AI. Odśwież po kolejnej turze." }
        val view = TextView(this).apply {
            text = transcript
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(UiDesign.dp(this@CampaignToolActivity, 32), UiDesign.dp(this@CampaignToolActivity, 20), UiDesign.dp(this@CampaignToolActivity, 32), UiDesign.dp(this@CampaignToolActivity, 20))
        }
        AlertDialog.Builder(this)
            .setTitle("Prywatny transkrypt RX / AI")
            .setView(ScrollView(this).apply { addView(view) })
            .setPositiveButton("Odśwież") { _, _ -> showLatestTranscript() }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    private fun playLastRecordedAudio(direction: String) {
        require(direction == "rx" || direction == "tx")
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            statusView.text = "Odsłuch wymaga uprawnienia do sprawdzenia stanu telefonu."
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        if (runCatching { telecom == null || telecom.isInCall }.getOrDefault(true)) {
            statusView.text = "Odsłuch tylko poza rozmową; bez ryzyka sprzężenia audio."
            return
        }
        val audio = latestEvidenceDirectory()?.listFiles()?.filter {
            it.name.matches(Regex("turn-[0-9]{2}-$direction\\.wav"))
        }?.maxByOrNull { it.name }
        if (audio == null) {
            statusView.text = "Brak zapisanego audio AI. Najpierw uzbrój prywatną diagnostykę."
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
            statusView.text = "Odtwarzanie prywatnego pliku " +
                (if (direction == "rx") "rozmówcy" else "AI") + ": " + audio.name
        }.onFailure {
            playback?.release()
            playback = null
            statusView.text = "Nie udało się odtworzyć ostatniego pliku WAV."
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
            statusView.text = "Campaign permissions denied"
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
            statusView.text = "System nie udostępnia roli domyślnego dialera"
            return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            statusView.text = "AI Calls ma już rolę dialera"
            if (continueStartAfterRole) {
                continueStartAfterRole = false
                ensurePermissionsThen(PendingAction.START)
            }
            return
        }
        // Android alone grants/revokes this role. Never change Google Phone silently.
        statusView.text = "Android poprosi o świadomy wybór: AI Calls zastąpi Google Phone jako domyślny dialer."
        startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQUEST_DIALER_ROLE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DIALER_ROLE) {
            val roleActive = AndroidTelecomOwnedCallController.canControl(this)
            statusView.text = if (roleActive) {
                "Kontroler połączeń Android aktywny — można wykonać autoryzowane zadanie"
            } else "Rola dialera nie została przyznana; Google Phone pozostaje aktywny"
            val shouldStart = continueStartAfterRole
            continueStartAfterRole = false
            if (roleActive && shouldStart) ensurePermissionsThen(PendingAction.START)
        }
    }

    private fun startSupervisedAudio() {
        statusView.text = "Attaching read-only audio test to existing user-controlled call"
        startForegroundService(
            Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_START_SUPERVISED_AUDIO),
        )
    }

    private fun startAuthorizedCampaign() {
        val accountScope = CampaignAccountScope.currentVoiceSubscription(this)
        if (accountScope == null) {
            statusView.text = "Could not bind execution to the current voice subscription"
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
        statusView.text = if (report.contains("campaign_agent_start=DISPATCHED_TO_INTERNAL_SERVICE")) {
            "Uruchamiam zadanie: włączenie CLIR. Tryb: " +
                if (requestedStartMode == CampaignDialogueMode.GEMMA_DIRECT) "Gemma Direct" else "skrypt + model"
        } else "Nie udało się uruchomić zadania. Szczegóły są w diagnostyce."

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
        diagnosticStatus.text = listOfNotNull(evidenceStatus, runtimeStatus)
            .joinToString("\n\n")
        updateLastExecutionStatus()
    }

    private fun updateLastExecutionStatus() {
        val raw = runtimeStatusStore.load()
        // Pure, localizable interpretation; raw diagnostics stay in the existing accordion.
        val screen = CampaignExecutionStatusPresenter.present(
            raw, resources.configuration.locales[0],
        )
        lastExecutionStatus.text = getString(R.string.camp_ui_runtime_heading) +
            "\n" + getString(R.string.campaign_ui_last_history) +
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
