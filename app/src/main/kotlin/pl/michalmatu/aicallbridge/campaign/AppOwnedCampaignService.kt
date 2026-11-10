package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason

import android.Manifest
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry
import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.telecom.TelecomManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.conversation.AndroidConversationRuntime
import pl.michalmatu.aicallbridge.conversation.*
import rikka.shizuku.Shizuku
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Handler
import android.os.Looper

class AppOwnedCampaignService : Service() {
    private lateinit var authorizationStore: CampaignAuthorizationStore
    private lateinit var statusStore: CampaignRuntimeStatusStore
    private lateinit var continuationStore: CampaignContinuationStore
    private lateinit var dialogueModeStore: CampaignDialogueModeStore
    private val preparationStore by lazy { AndroidConversationPreparationStore(this) }
    private var selectedDialogueMode = CampaignDialogueMode.SCRIPT_GEMMA
    private var selectedAudioProfile = AudioEngineProfile.STABLE
    private val continuationHandler = Handler(Looper.getMainLooper())
    private var scheduledAttempt: Runnable? = null
    private var campaignRequested = false
    private var attemptOutcome: Pair<Boolean, String?>? = null
    private var awaitingPhoneIdle = false
    private var preparingLocalModel = false
    private var continuationGeneration = 0L
    private lateinit var notificationManager: NotificationManager
    private val supervisedAudioActive = AtomicBoolean(false)
    private var supervisedAudioSession: AndroidConversationRuntime.SessionControl? = null

    override fun onCreate() {
        super.onCreate()
        authorizationStore = CampaignAuthorizationStore(this)
        statusStore = CampaignRuntimeStatusStore(this)
        continuationStore = CampaignContinuationStore(this)
        dialogueModeStore = CampaignDialogueModeStore(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AI Calls campaign",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        currentService = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SUPERVISED_AUDIO -> {
                startSupervisedAudio()
                return START_NOT_STICKY
            }
            ACTION_START_BOOK_APPOINTMENT -> {
                startApprovedAppointment()
                return START_NOT_STICKY // Never resurrect a dial from an Android service restart.
            }
            ACTION_START_SCENARIO_INQUIRY -> {
                startApprovedScenarioInquiry()
                return START_NOT_STICKY // Never auto-resume an enquiry after restart.
            }
            ACTION_CANCEL_OUTGOING_TASK -> {
                // Cancel only the outgoing task; never disable an incoming secretary
                // or interrupt an unrelated supervised audio session.
                authorizationStore.revoke(System.currentTimeMillis())
                campaignRequested = false
                continuationGeneration += 1
                continuationStore.clear()
                dialogueModeStore.clear()
                scheduledAttempt?.let { continuationHandler.removeCallbacks(it) }
                scheduledAttempt = null
                preparingLocalModel = false
                awaitingPhoneIdle = false
                CampaignGemmaPreflight.discardUnused()
                CampaignGemmaDirectPreflight.discardUnused()
                var cleanup: String? = null
                AppOwnedClirCampaignExecutor.stop(ConversationStopReason.OPERATOR_STOP) {
                    cleanup = it
                }
                val status = "outgoing_task_cancelled; incoming assistant unchanged" +
                    (cleanup?.let { "; $it" } ?: "")
                if (supervisedAudioActive.get()) {
                    // The same service may currently host an unrelated manual pilot.
                    statusStore.save(status)
                } else finish(status)
                return START_NOT_STICKY
            }
            ACTION_STOP_SUPERVISED_AUDIO, ACTION_STOP_ALL_AI, ACTION_PAUSE_AFTER_TAKEOVER -> {
                val incomingStopped = intent.action == ACTION_PAUSE_AFTER_TAKEOVER || AndroidOwnedInCallService.stopIncomingAi(this)
                campaignRequested = false
                continuationGeneration += 1
                continuationStore.clear()
                dialogueModeStore.clear()
                CampaignGemmaDirectPreflight.discardUnused()
                scheduledAttempt?.let { continuationHandler.removeCallbacks(it) }
                scheduledAttempt = null
                supervisedAudioActive.set(false)
                supervisedAudioSession?.stopAudio(ConversationStopReason.OPERATOR_STOP)
                supervisedAudioSession = null
                var ownedStatus: String? = null
                AppOwnedClirCampaignExecutor.stop(ConversationStopReason.OPERATOR_STOP) { ownedStatus = it }
                val status = if (intent.action == ACTION_PAUSE_AFTER_TAKEOVER)
                    "Rozmowę przekazano użytkownikowi; automatyczne ponowienia wstrzymane"
                else ownedStatus ?: "STOP AI: app audio stopped; external calls remain untouched"
                finish(status + if (incomingStopped) "" else "; nie utrwalono wyłączenia sekretarki — błąd zapisu ustawień")
                return START_NOT_STICKY
            }
        }
        // A service restarted by Android may resume only a persisted, previously started
        // exact-grant campaign. An ordinary null/unknown command never grants new authority.
        val explicitStart = intent?.action == ACTION_START_CLIR_ENABLE
        val restoring = intent == null && continuationStore.load() != null
        if (!explicitStart && !restoring) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (supervisedAudioActive.get()) {
            statusStore.save("Cannot start a campaign during the supervised audio pilot")
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            notification("Preparing authorized campaign"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        val accountScope = CampaignAccountScope.currentVoiceSubscription(this)
        val grant = authorizationStore.load()
        val now = System.currentTimeMillis()
        val authorized = grant != null && accountScope != null &&
            CampaignAuthorizationPolicy.authorize(
                grant, ClirCampaignSpec.enableRequest(accountScope),
            ) is CampaignAuthorizationDecision.Authorized
        if (!authorized || grant == null || accountScope == null) {
            finish("Campaign authorization or voice subscription unavailable")
            return START_NOT_STICKY
        }

        val saved = continuationStore.load()
        if (!explicitStart && (saved == null || saved.grantId != grant.grantId ||
                saved.accountScope != accountScope)
        ) {
            finish("Saved campaign scope does not match the current approved grant")
            return START_NOT_STICKY
        }
        val disposition = CampaignStartReconciliationPolicy.decide(
            saved = saved,
            requestedGrantId = grant.grantId,
            requestedAccountScope = accountScope,
            ownedCallRunning = AppOwnedClirCampaignExecutor.isRunning(),
        )
        when (disposition) {
            CampaignStartDisposition.BEGIN_NEW,
            CampaignStartDisposition.QUEUE_REPLACEMENT -> {
                // A changed, separately authorized grant supersedes the previous
                // pending campaign. The previous exact owned call must finish
                // before the replacement is allowed to start.
                continuationGeneration += 1
                continuationStore.begin(grant.grantId, accountScope, now)
            }
            CampaignStartDisposition.REUSE_SAVED -> Unit // Keep existing due time.
            CampaignStartDisposition.ALREADY_RUNNING -> {
                if (campaignRequested) {
                    statusStore.save("Autoryzowana rozmowa już trwa; bez ponownego wybierania numeru")
                    return START_STICKY
                }
                // A recreated Service may not own the old executor's callbacks.
                // Reconcile from phone IDLE instead of stranding the saved task.
            }
        }
        val selectedMode = if (explicitStart) {
            CampaignDialogueMode.explicitStart(intent?.getStringExtra(EXTRA_DIALOGUE_MODE))
        } else {
            dialogueModeStore.forGrant(grant.grantId)
        }
        if (selectedMode == null) {
            finish("Unknown dialogue mode requested; no calls")
            return START_NOT_STICKY
        }
        // Freeze a single profile for all prewarm/owned-call work started by this campaign.
        // Preference changes affect only the next campaign, never a live turn or grant.
        val profile = AudioEngineProfileStore(this).load()
        if (profile == null || AudioEngineRegistry.incompatibility(profile) != null) {
            finish("Stored audio engine configuration unavailable; no calls")
            return START_NOT_STICKY
        }
        selectedAudioProfile = profile
        selectedDialogueMode = selectedMode
        if (explicitStart) dialogueModeStore.bind(grant.grantId, selectedMode)
        campaignRequested = true
        scheduleAuthorizedAttempt()
        return START_STICKY
    }

    /**
     * One UI-authorized appointment attempt. No fallback to CLIR, no automatic redial,
     * no unattended reboot resume, no external booking effect.
     */
    private fun startApprovedAppointment() {
        if (campaignRequested || supervisedAudioActive.get() ||
            AppOwnedClirCampaignExecutor.isRunning() || continuationStore.load() != null
        ) {
            statusStore.save("Inne zadanie jest aktywne; najpierw je zakończ")
            return
        }
        val task = AppointmentMvpStore(this).load() ?: run {
            finish("Nie zapisano zadania wizyty")
            return
        }
        val grant = authorizationStore.load()
        if (grant == null || grant.attemptsUsed != 0 ||
            grant.grantId != task.grantId ||
            !CampaignAuthorizationPolicy.matchesExactTask(grant, task.request()) ||
            CampaignAccountScope.forSubscription(this, task.subscriptionId) != task.accountScope
        ) {
            finish("Brak dokładnej autoryzacji numeru i SIM; połączenie zablokowane")
            return
        }
        val profile = AudioEngineProfileStore(this).load()
        if (profile == null || AudioEngineRegistry.incompatibility(profile) != null) {
            finish("Wybrany Gemma/STT/TTS jest niedostępny")
            return
        }
        val preparation = ConversationPreparation(
            "outgoing-appointment", "Umawianie wizyty", ConversationDirection.OUTGOING,
            "", setOf(ConversationSkill.EXPLAIN_GOAL, ConversationSkill.CLARIFY,
                ConversationSkill.ASK_PROCEDURE),
            task.disclosureFields,
        )
        val goal = "Cel użytkownika: " + task.goal +
            ". Dozwolony przedział wizyty: " + task.notBefore.toString() + " do " +
            task.notAfter.toString() + ". Warunki użytkownika: " +
            task.conditions.ifBlank { "Brak dodatkowych warunków." } +
            ". Zbierz tylko propozycję i nie potwierdzaj ani nie rezerwuj wizyty. " +
            "Każda propozycja musi trafić do użytkownika jako REVIEW_REQUIRED."
        val instruction = ConversationInstructionBuilder.build(
            preparation, goal, ConversationResponseFormat.SPOKEN_TEXT,
        )
        val configuration = ConversationRunConfiguration.ModelDirect(
            profile, instruction, preparation,
        )
        campaignRequested = true
        val generation = ++continuationGeneration
        preparingLocalModel = true
        statusStore.save("Przygotowanie Gemma do autoryzowanej wizyty")
        startForeground(
            NOTIFICATION_ID, notification("Przygotowanie rozmowy BOOK_APPOINTMENT"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        CampaignGemmaDirectPreflight.prepare(
            this, systemInstruction = instruction, profile = profile,
            onReady = { ready ->
                continuationHandler.post {
                    preparingLocalModel = false
                    if (!campaignRequested || continuationGeneration != generation) {
                        CampaignGemmaDirectPreflight.discardUnused()
                        return@post
                    }
                    val live = authorizationStore.load()
                    if (!ready || live == null || live.attemptsUsed != 0 ||
                        live.grantId != task.grantId ||
                        AppointmentMvpStore(this).load() != task ||
                        !CampaignAuthorizationPolicy.matchesExactTask(live, task.request()) ||
                        CampaignAccountScope.forSubscription(
                            this, task.subscriptionId) != task.accountScope
                    ) {
                        finish("Model lub autoryzacja zmieniły się przed wybraniem numeru")
                        return@post
                    }
                    if (isPhoneBusy()) {
                        finish("Trwa inna rozmowa. Bez automatycznej ponownej próby")
                        return@post
                    }
                    AppOwnedClirCampaignExecutor.start(
                        context = this,
                        authorizationStore = authorizationStore,
                        accountScope = task.accountScope,
                        runConfiguration = configuration,
                        appointmentTask = task,
                        onAttemptFinished = { _, _ -> },
                    ) { status ->
                        continuationHandler.post {
                            if (!campaignRequested || continuationGeneration != generation)
                                return@post
                            statusStore.save(status)
                            if (!AppOwnedClirCampaignExecutor.isRunning()) finish(status)
                        }
                    }
                }
            },
        )
    }

    /**
     * One human-authorized scenario inquiry. This path has no external effect
     * commitment or automatic redial, and never reuses CLIR's authorization.
     */
    private fun startApprovedScenarioInquiry() {
        if (campaignRequested || supervisedAudioActive.get() ||
            AppOwnedClirCampaignExecutor.isRunning() || continuationStore.load() != null
        ) {
            statusStore.save("An outgoing task is already active; no scenario call started")
            return
        }
        val task = preparationStore.loadOutgoingScenarioTask() ?: run {
            finish("No approved scenario enquiry was saved")
            return
        }
        val grant = authorizationStore.load()
        if (grant == null || grant.attemptsUsed != 0 ||
            grant.grantId != task.grantId ||
            !CampaignAuthorizationPolicy.matchesExactTask(grant, task.request()) ||
            CampaignAccountScope.forSubscription(this, task.subscriptionId) != task.accountScope
        ) {
            finish("Scenario number/SIM/goal authorization mismatch; dial blocked")
            return
        }
        val profile = AudioEngineProfileStore(this).load()
        if (profile == null || AudioEngineRegistry.incompatibility(profile) != null) {
            finish("Selected on-device speech/model stack is unavailable; no dial")
            return
        }
        val preparation = ConversationPreparation(
            "outgoing-scenario-" + task.scenarioId,
            task.title, ConversationDirection.OUTGOING,
            "", setOf(ConversationSkill.EXPLAIN_GOAL, ConversationSkill.CLARIFY,
                ConversationSkill.ASK_PROCEDURE), emptySet(),
        )
        val scopedGoal = "Cel rozmowy: " + task.goal + ". Szczegóły podane przez użytkownika: " +
            task.brief + ". Rozmowa jest tylko zapytaniem informacyjnym. " +
            "Zbierz możliwe terminy, ceny i warunki oraz zapowiedz, że użytkownik je rozważy. " +
            "Nie potwierdzaj rezerwacji, nie zamawiaj usługi, nie zgadzaj się na opłaty, " +
            "nie zatwierdzaj napraw, zakupów ani zmian umów. " +
            "Nie deklaruj osiągnięcia celu bez niezależnej weryfikacji; odpowiedź rozmówcy " +
            "ma zostać do sprawdzenia w prywatnej historii rozmowy."
        val instruction = ConversationInstructionBuilder.build(
            preparation, scopedGoal, ConversationResponseFormat.SPOKEN_TEXT,
        )
        val configuration = ConversationRunConfiguration.ModelDirect(
            profile, instruction, preparation,
        )
        campaignRequested = true
        val generation = ++continuationGeneration
        preparingLocalModel = true
        statusStore.save("Preparing on-device model for the approved scenario enquiry")
        startForeground(
            NOTIFICATION_ID, notification("Preparing authorized scenario call"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        CampaignGemmaDirectPreflight.prepare(
            this, systemInstruction = instruction, profile = profile,
            onReady = { ready ->
                continuationHandler.post {
                    preparingLocalModel = false
                    if (!campaignRequested || continuationGeneration != generation) {
                        CampaignGemmaDirectPreflight.discardUnused()
                        return@post
                    }
                    val live = authorizationStore.load()
                    if (!ready || live == null || live.attemptsUsed != 0 ||
                        live.grantId != task.grantId ||
                        preparationStore.loadOutgoingScenarioTask() != task ||
                        !CampaignAuthorizationPolicy.matchesExactTask(live, task.request()) ||
                        CampaignAccountScope.forSubscription(
                            this, task.subscriptionId) != task.accountScope
                    ) {
                        finish("Scenario model/task authorization changed before dialing")
                        return@post
                    }
                    if (isPhoneBusy()) {
                        finish("Another phone call is active. No automatic scenario retry")
                        return@post
                    }
                    AppOwnedClirCampaignExecutor.start(
                        context = this,
                        authorizationStore = authorizationStore,
                        accountScope = task.accountScope,
                        runConfiguration = configuration,
                        scenarioTask = task,
                        onAttemptFinished = { _, _ -> },
                    ) { status ->
                        continuationHandler.post {
                            if (!campaignRequested || continuationGeneration != generation)
                                return@post
                            statusStore.save(status)
                            if (!AppOwnedClirCampaignExecutor.isRunning()) finish(status)
                        }
                    }
                }
            },
        )
    }

    private fun scheduleAuthorizedAttempt() {
        scheduledAttempt?.let { continuationHandler.removeCallbacks(it) }
        val state = continuationStore.load()
        if (!campaignRequested || state == null || !isContinuationAuthorized(state)) {
            finish("Campaign no longer authorized; no further calls")
            return
        }
        val delay = (state.nextAttemptAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        statusStore.save("Autoryzowana kontynuacja: następna próba za ${delay / 1_000L} s")
        startForeground(
            NOTIFICATION_ID,
            notification(statusStore.load().orEmpty()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        val runnable = Runnable { startAuthorizedAttempt() }
        scheduledAttempt = runnable
        continuationHandler.postDelayed(runnable, delay)
    }

    private fun isContinuationAuthorized(state: CampaignContinuationState): Boolean {
        val grant = authorizationStore.load() ?: return false
        if (grant.grantId != state.grantId ||
            CampaignAccountScope.currentVoiceSubscription(this) != state.accountScope
        ) return false
        return CampaignAuthorizationPolicy.authorize(
            grant,
            ClirCampaignSpec.enableRequest(state.accountScope),
        ) is CampaignAuthorizationDecision.Authorized
    }

    private fun startAuthorizedAttempt() {
        scheduledAttempt = null
        val state = continuationStore.load()
        if (!campaignRequested || state == null || !isContinuationAuthorized(state)) {
            finish("Campaign stopped or task scope unavailable")
            return
        }
        if (AppOwnedClirCampaignExecutor.isRunning() || isPhoneBusy()) {
            awaitPhoneIdle()
            return
        }
        awaitingPhoneIdle = false
        if (preparingLocalModel) return

        val preparation = runCatching { preparationStore.load(ConversationPreparation.OUTGOING_ID) }.getOrElse {
            finish("Conversation preparation unavailable")
            return
        }
        val runConfiguration = when (selectedDialogueMode) {
            CampaignDialogueMode.SCRIPT_GEMMA ->
                ConversationRunConfiguration.ScriptAssisted(selectedAudioProfile, preparation)
            CampaignDialogueMode.GEMMA_DIRECT -> {
                val instruction = buildDirectBrief(state, preparation) ?: run {
                    finish("Direct task brief unavailable or not authorized")
                    return
                }
                ConversationRunConfiguration.ModelDirect(selectedAudioProfile, instruction, preparation)
            }
        }
        // The first local LiteRT/Gemma generation can take tens of seconds on S22.
        // Warm the exact authorized model context before placing an external call, not while
        // the remote IVR is waiting for the first spoken response.
        preparingLocalModel = true
        statusStore.save("Preparing local Gemma before authorized Telecom dial")
        val generation = continuationGeneration
        val onModelPrepared: (Boolean) -> Unit = { ready ->
            continuationHandler.post {
                preparingLocalModel = false
                if (!campaignRequested || generation != continuationGeneration) {
                    CampaignGemmaPreflight.discardUnused()
                    CampaignGemmaDirectPreflight.discardUnused()
                    return@post
                }
                if (!ready) {
                    finish("Local Gemma failed to prepare before dialing")
                    return@post
                }
                val latest = continuationStore.load()
                if (latest == null || latest.grantId != state.grantId ||
                    !isContinuationAuthorized(latest)
                ) {
                    finish("Campaign scope changed while preparing local model")
                    return@post
                }
                // This attempt keeps its captured instructions. Preference edits apply
                // to the next attempt; live scope/revocation is still checked above and in TX.
                if (AppOwnedClirCampaignExecutor.isRunning() || isPhoneBusy()) {
                    awaitPhoneIdle()
                    return@post
                }
                launchPreparedAttempt(latest, runConfiguration)
            }
        }
        when (runConfiguration) {
            is ConversationRunConfiguration.ScriptAssisted -> CampaignGemmaPreflight.prepare(
                this, onModelPrepared, runConfiguration.audioProfile,
            )
            is ConversationRunConfiguration.ModelDirect -> CampaignGemmaDirectPreflight.prepare(
                this, systemInstruction = runConfiguration.systemInstruction,
                profile = runConfiguration.audioProfile, onReady = onModelPrepared,
            )
        }
    }

    private fun buildDirectBrief(state: CampaignContinuationState, preparation: ConversationPreparation): String? {
        val grant = authorizationStore.load() ?: return null
        if (grant.grantId != state.grantId) return null
        return runCatching {
            GemmaDirectTaskBriefBuilder.build(
                request = ClirCampaignSpec.enableRequest(state.accountScope),
                grant = grant,
                preparation = preparation,
            )
        }.getOrNull()
    }

    private fun isPhoneBusy(): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return false // The executor will reject missing permissions before dialing.
        return runCatching {
            getSystemService(TelecomManager::class.java)?.isInCall == true
        }.getOrDefault(false)
    }

    private fun launchPreparedAttempt(
        state: CampaignContinuationState,
        runConfiguration: ConversationRunConfiguration,
    ) {
        continuationStore.prepareAttempt(state.grantId, System.currentTimeMillis())
            ?: run { finish("Continuation state unavailable"); return }
        attemptOutcome = null
        val generation = continuationGeneration
        AppOwnedClirCampaignExecutor.start(
            context = this,
            authorizationStore = authorizationStore,
            accountScope = state.accountScope,
            runConfiguration = runConfiguration,
            onAttemptFinished = { success, reason ->
                continuationHandler.post {
                    if (campaignRequested && continuationGeneration == generation) {
                        attemptOutcome = success to reason
                    }
                }
            },
        ) { status ->
            continuationHandler.post { handleAttemptStatus(status, state.grantId, generation) }
        }
    }

    private fun awaitPhoneIdle() {
        if (!awaitingPhoneIdle) {
            awaitingPhoneIdle = true
            statusStore.save("Autoryzowane zadanie czeka na zakończenie trwającej rozmowy")
        }
        val generation = continuationGeneration
        val retry = Runnable {
            if (campaignRequested && continuationGeneration == generation) {
                startAuthorizedAttempt()
            }
        }
        scheduledAttempt = retry
        continuationHandler.postDelayed(retry, PHONE_IDLE_RECHECK_MS)
    }

    private fun handleAttemptStatus(status: String, grantId: String, generation: Long) {
        // A late result from STOP or a previous run must never overwrite the
        // current diagnostic state or schedule an unexpected follow-up call.
        if (!campaignRequested || continuationGeneration != generation ||
            continuationStore.load()?.grantId != grantId
        ) return
        statusStore.save(status)
        if (AppOwnedClirCampaignExecutor.isRunning()) {
            startForeground(
                NOTIFICATION_ID,
                notification(status),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            return
        }
        val outcome = attemptOutcome
        attemptOutcome = null
        // A setup/readiness failure is not a reason to blindly redial; it needs a
        // working device. An ended, incomplete authorized conversation can resume.
        if (outcome != null && !outcome.first) {
            val state = continuationStore.planRetry(grantId, System.currentTimeMillis())
            if (state != null && isContinuationAuthorized(state)) {
                scheduleAuthorizedAttempt()
                return
            }
        }
        finish(status)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Explicit, user-attached, read-only pilot. It never dials or disconnects a call.
     * The user must start and end the ordinary cellular call in the existing dialer.
     */
    private fun startSupervisedAudio() {
        // A queued retry is still an active owner-authorized campaign even between
        // calls. An unrelated pilot must not tear it down through finish()/STOP.
        if (campaignRequested || continuationStore.load() != null ||
            AppOwnedClirCampaignExecutor.isRunning() ||
            !supervisedAudioActive.compareAndSet(false, true)
        ) {
            statusStore.save("Campaign or another audio runtime is active; current task continues")
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        val audio = getSystemService(AudioManager::class.java)
        val permissionsReady = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
        ).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!permissionsReady) {
            supervisedAudioActive.set(false)
            finish("Read-only audio pilot blocked: microphone and phone-state permission required")
            return
        }
        startForeground(
            NOTIFICATION_ID,
            notification("Read-only audio pilot on user-controlled call"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        val shizukuReady = runCatching {
            Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val alreadyInCall = permissionsReady && runCatching {
            telecom?.isInCall == true && audio?.mode == AudioManager.MODE_IN_CALL
        }.getOrDefault(false)
        if (!permissionsReady || !shizukuReady || !alreadyInCall) {
            supervisedAudioActive.set(false)
            finish("Read-only pilot blocked: attach to an existing call with phone/mic/Shizuku ready")
            return
        }
        val sessionId = "supervised-" + UUID.randomUUID().toString().take(18)
        statusStore.save("Read-only audio pilot attached; no dial, hangup, account change or PHONE disclosure")
        val result = runCatching {
            AndroidConversationRuntime.run(
                context = this,
                sessionId = sessionId,
                maxTurns = 2,
                scenario = SupervisedConversationScenario(this, modelEnabled = true),
                runConfiguration = ConversationRunConfiguration.ScriptAssisted(AudioEngineProfile.STABLE, ConversationPreparation.supervised()),
                liveAuthorization = {
                    supervisedAudioActive.get() &&
                        runCatching {
                            telecom?.isInCall == true &&
                                audio?.mode == AudioManager.MODE_IN_CALL
                        }.getOrDefault(false)
                },
            ) { result ->
                val report = result.report
                supervisedAudioActive.set(false)
                supervisedAudioSession = null
                val reason = report.lineSequence()
                    .firstOrNull { it.startsWith("failure_reason=") }
                    ?.substringAfter('=')?.take(80)
                val status = if (reason.isNullOrBlank()) {
                    "Read-only audio pilot finished; cellular call not modified or disconnected"
                } else {
                    "Read-only audio pilot ended ($reason); cellular call not modified or disconnected"
                }
                finish(status)
            }
        }
        if (result.isFailure) {
            supervisedAudioActive.set(false)
            finish("Read-only audio pilot failed to start; cellular call remains untouched")
        } else if (supervisedAudioActive.get()) {
            supervisedAudioSession = result.getOrNull()
        }
    }

    override fun onDestroy() {
        preparingLocalModel = false
        CampaignGemmaPreflight.discardUnused()
        CampaignGemmaDirectPreflight.discardUnused()
        supervisedAudioActive.set(false)
        supervisedAudioSession?.stopAudio(ConversationStopReason.SERVICE_DESTROYED)
        supervisedAudioSession = null
        scheduledAttempt?.let { continuationHandler.removeCallbacks(it) }
        scheduledAttempt = null
        continuationGeneration += 1
        AppOwnedClirCampaignExecutor.stop(ConversationStopReason.SERVICE_DESTROYED) { statusStore.save(it) }
        if (currentService === this) currentService = null
        super.onDestroy()
    }

    private fun finish(status: String) {
        preparingLocalModel = false
        CampaignGemmaPreflight.discardUnused()
        CampaignGemmaDirectPreflight.discardUnused()
        campaignRequested = false
        awaitingPhoneIdle = false
        continuationGeneration += 1
        scheduledAttempt?.let { continuationHandler.removeCallbacks(it) }
        scheduledAttempt = null
        continuationStore.clear()
        dialogueModeStore.clear()
        selectedDialogueMode = CampaignDialogueMode.SCRIPT_GEMMA
        selectedAudioProfile = AudioEngineProfile.STABLE
        statusStore.save(status)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(status: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AI Calls campaign")
            .setContentText(status.take(NOTIFICATION_TEXT_LIMIT))
            .setOngoing(AppOwnedClirCampaignExecutor.isRunning() || campaignRequested)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP AI",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, AppOwnedCampaignService::class.java)
                        .setAction(ACTION_STOP_ALL_AI),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        @Volatile private var currentService: AppOwnedCampaignService? = null

        /** Read-only, process-local view. Never inspect persistent history for a live claim. */
        internal fun liveUiPhase(): CampaignLivePhase {
            val active = currentService
            return CampaignLiveStateResolver.resolve(
                supervised = active?.supervisedAudioActive?.get() == true,
                waitingForPhone = active?.awaitingPhoneIdle == true,
                preparingModel = active?.preparingLocalModel == true,
                campaignRequested = active?.campaignRequested == true,
                ownedAttemptRunning = AppOwnedClirCampaignExecutor.isRunning(),
            )
        }

        const val ACTION_PAUSE_AFTER_TAKEOVER = "pl.michalmatu.aicallbridge.PAUSE_AFTER_TAKEOVER"
        const val EXTRA_DIALOGUE_MODE =
            "pl.michalmatu.aicallbridge.extra.CAMPAIGN_DIALOGUE_MODE"
        const val ACTION_START_CLIR_ENABLE =
            "pl.michalmatu.aicallbridge.action.START_CLIR_ENABLE_CAMPAIGN"
        const val ACTION_START_BOOK_APPOINTMENT =
            "pl.michalmatu.aicallbridge.action.START_BOOK_APPOINTMENT"
        const val ACTION_START_SCENARIO_INQUIRY =
            "pl.michalmatu.aicallbridge.action.START_SCENARIO_INQUIRY"
        const val ACTION_START_SUPERVISED_AUDIO =
            "pl.michalmatu.aicallbridge.action.START_SUPERVISED_AUDIO"
        const val ACTION_STOP_SUPERVISED_AUDIO =
            "pl.michalmatu.aicallbridge.action.STOP_SUPERVISED_AUDIO"
        const val ACTION_STOP_ALL_AI =
            "pl.michalmatu.aicallbridge.action.STOP_ALL_AI"
        const val ACTION_CANCEL_OUTGOING_TASK =
            "pl.michalmatu.aicallbridge.action.CANCEL_OUTGOING_TASK"

        private const val CHANNEL_ID = "aicall_campaign"
        private const val NOTIFICATION_ID = 3101
        private const val NOTIFICATION_TEXT_LIMIT = 120
        private const val PHONE_IDLE_RECHECK_MS = 2_000L
    }
}
