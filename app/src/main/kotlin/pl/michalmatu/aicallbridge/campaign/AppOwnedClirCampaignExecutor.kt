package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason
import pl.michalmatu.aicallbridge.conversationcore.ConversationTermination

import android.Manifest
import android.content.Context
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.AndroidLiveCallReadiness
import pl.michalmatu.aicallbridge.conversation.AndroidConversationRuntime
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

internal object AppOwnedClirCampaignExecutor {
    private const val CALL_ACTIVE_TIMEOUT_MS = 30_000L
    private const val CALL_ACTIVE_POLL_MS = 250L
    private val running = AtomicBoolean(false)
    private val ownedCalls = CampaignOwnedCallLifecycle<CampaignOwnedCallSession> { cleanupOwnedCall(it) }
    private val audioSession = AtomicReference<AndroidConversationRuntime.SessionControl?>(null)

    fun isRunning(): Boolean = running.get()

    /** Only the displayed exact owned Call can be transferred; late cleanup loses its owner. */
    fun takeOver(call: android.telecom.Call): Boolean {
        val detached = ownedCalls.detachIf { (it as? AndroidTelecomOwnedCallSession)?.isBoundTo(call) == true } ?: return false
        running.set(false)
        (detached as? AndroidTelecomOwnedCallSession)?.revokeDtmf()
        audioSession.getAndSet(null)?.stopAudio(ConversationStopReason.HUMAN_TAKEOVER)
        return true
    }

    /** STOP revokes AI TX and cleans up only a call identified by this app's controller. */
    fun stop(reason: ConversationStopReason, onStatus: (String) -> Unit): Boolean {
        val wasRunning = running.getAndSet(false)
        audioSession.getAndSet(null)?.stopAudio(reason)
        val cleanup = ownedCalls.releaseCurrent()
        if (wasRunning) onStatus("AI stopped; owned-call cleanup=$cleanup")
        return wasRunning
    }

    fun start(
        context: Context,
        authorizationStore: CampaignAuthorizationStore,
        accountScope: String,
        runConfiguration: ConversationRunConfiguration,
        appointmentTask: AppointmentMvpTask? = null,
        onAttemptFinished: (Boolean, String?) -> Unit = { _, _ -> },
        onStatus: (String) -> Unit,
    ) {
        if (!running.compareAndSet(false, true)) {
            onStatus("Campaign call is already running")
            return
        }

        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val request = appointmentTask?.request() ?: ClirCampaignSpec.enableRequest(accountScope)
        if (appointmentTask != null && (appointmentTask.accountScope != accountScope ||
            request.accountScope != accountScope ||
            authorizationStore.load()?.grantId != appointmentTask.grantId)) {
            running.set(false)
            onStatus("Appointment authorization mismatch")
            return
        }
        // Android does not expose an app-owned cellular Call handle via ACTION_CALL.
        // Resolve an actual call controller, not a synthetic proof with constant booleans.
        val callController = CampaignCallControllerRegistry.available(appContext)
        if (callController == null) {
            running.set(false)
            onStatus("Kontroler niedostępny: wybierz AI Calls jako domyślny dialer w systemowym oknie konfiguracji")
            return
        }
        val permissionFailure = requiredPermissionFailure(appContext)
        if (permissionFailure != null) {
            running.set(false)
            onStatus(permissionFailure)
            return
        }

        val readiness = AndroidLiveCallReadiness.evaluate(appContext)
        if (!readiness.ready) {
            running.set(false)
            onStatus("Live-call readiness failed: ${readiness.failure?.reportValue ?: "unknown"}")
            return
        }

        val telecomManager = appContext.getSystemService(TelecomManager::class.java)
        if (telecomManager == null) {
            running.set(false)
            onStatus("Telecom service unavailable")
            return
        }
        if (
            appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            running.set(false)
            onStatus("Required phone-state permission is not granted")
            return
        }
        val existingCall = runCatching { telecomManager.isInCall }.getOrElse {
            running.set(false)
            onStatus("Could not verify cellular call state")
            return
        }
        if (existingCall) {
            running.set(false)
            onStatus("Refusing to dial while another call is active")
            return
        }

        val voiceAccount = if (appointmentTask == null) {
            CampaignPhoneAccountResolver.resolveDefaultVoiceAccount(appContext, telecomManager)
        } else CampaignPhoneAccountResolver.resolveSelectedVoiceAccount(
            appContext, telecomManager, appointmentTask.subscriptionId,
        )
        val liveScope = if (appointmentTask == null)
            CampaignAccountScope.currentVoiceSubscription(appContext)
        else CampaignAccountScope.forSubscription(appContext, appointmentTask.subscriptionId)
        if (voiceAccount == null || liveScope != request.accountScope) {
            running.set(false)
            onStatus("Dial blocked: exact default voice SIM/account not established")
            return
        }

        val reservedGrant = authorizationStore.reserveAuthorizedAttempt(
            request = request,
        ).getOrElse {
            running.set(false)
            onStatus("Campaign attempt could not be reserved")
            return
        }

        if (
            appContext.checkSelfPermission(Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            running.set(false)
            onStatus("Required phone-call permission is not granted")
            return
        }
        val callIntent = Intent(
            Intent.ACTION_CALL,
            Uri.fromParts("tel", request.target, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, voiceAccount)

        // Revalidate the reserved grant immediately before the irreversible dial edge.
        // The controller implementation must also check revocation at its own dial boundary.
        if (!authorizationStore.isAuthorizedInFlight(
                grantId = reservedGrant.grantId,
                request = request,
            )
        ) {
            running.set(false)
            onStatus("Task cancelled or scope changed before dialing")
            return
        }
        val ownedCall = runCatching {
            callController.dial(callIntent, voiceAccount, request.target)
        }.getOrElse { error ->
            running.set(false)
            onStatus("Owned dial failed: ${error.javaClass.simpleName}")
            return
        }
        if (!ownedCalls.bind(ownedCall)) {
            running.set(false)
            val cleanup = cleanupOwnedCall(ownedCall)
            onStatus("Another owned call session is still present; cleanup=$cleanup")
            return
        }
        // An operator STOP can arrive while Telecom is placing the call.
        if (!running.get()) {
            ownedCalls.release(ownedCall)
            return
        }
        val exactIdentityMatches = runCatching {
            ownedCall.dialedTarget == request.target && ownedCall.phoneAccount == voiceAccount
        }.getOrDefault(false)
        if (!exactIdentityMatches) {
            val cleanup = ownedCalls.release(ownedCall)
            running.set(false)
            onStatus("Owned dial identity missing or mismatched; cleanup=$cleanup")
            return
        }

        val launched = runCatching {
            onStatus("Authorized campaign dial started")
            waitForActiveCall(
                appContext = appContext,
                handler = handler,
                deadlineEpochMs = System.currentTimeMillis() + CALL_ACTIVE_TIMEOUT_MS,
                telecomManager = telecomManager,
                grant = reservedGrant,
                ownedCall = ownedCall,
                request = request,
                authorizationStore = authorizationStore,
                runConfiguration = runConfiguration,
                appointmentTask = appointmentTask,
                onAttemptFinished = onAttemptFinished,
                onStatus = onStatus,
            )
        }
        if (launched.isFailure) {
            val cleanup = ownedCalls.release(ownedCall)
            running.set(false)
            onStatus("Campaign call setup failed; cleanup=$cleanup")
        }
    }

    private fun waitForActiveCall(
        appContext: Context,
        handler: Handler,
        deadlineEpochMs: Long,
        telecomManager: TelecomManager,
        grant: CampaignAuthorizationGrant,
        ownedCall: CampaignOwnedCallSession,
        request: CampaignExecutionRequest,
        authorizationStore: CampaignAuthorizationStore,
        runConfiguration: ConversationRunConfiguration,
        appointmentTask: AppointmentMvpTask?,
        onAttemptFinished: (Boolean, String?) -> Unit,
        onStatus: (String) -> Unit,
    ) {
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        if (audioManager == null) {
            val cleanup = ownedCalls.release(ownedCall)
            running.set(false)
            onStatus("Audio service unavailable; cleanup=$cleanup")
            return
        }

        fun poll() {
            if (!running.get()) return
            val exactActive = runCatching { ownedCall.isExactCallActive() }.getOrElse {
                val cleanup = ownedCalls.release(ownedCall)
                running.set(false)
                onStatus("Owned call state unavailable; cleanup=$cleanup")
                return
            }
            // Samsung/Telecom uses MODE_IN_COMMUNICATION for Wi-Fi Calling (IWLAN).
            // The frozen VOICE_DOWNLINK capture is physically proven only in MODE_IN_CALL.
            // Never bypass this device media gate or wait silently through a doomed call.
            if (exactActive && runCatching { ownedCall.isWifiCalling() }.getOrDefault(false)) {
                val cleanup = ownedCalls.release(ownedCall)
                running.set(false)
                onStatus("Wi-Fi Calling (VoWiFi) nie obsługuje aktualnego toru audio. " +
                    "Wyłącz Połączenia przez Wi-Fi dla tej karty SIM; cleanup=$cleanup")
                return
            }
            if (exactActive && audioManager.mode == AudioManager.MODE_IN_CALL) {
                onStatus("Exact owned cellular call active; starting campaign runtime")
                startLocalRuntime(
                    appContext, telecomManager, grant, ownedCall, request,
                    authorizationStore, runConfiguration, appointmentTask,
                    onAttemptFinished, onStatus,
                )
                return
            }
            if (System.currentTimeMillis() >= deadlineEpochMs) {
                val cleanup = ownedCalls.release(ownedCall)
                running.set(false)
                val mode = when (audioManager.mode) {
                    AudioManager.MODE_NORMAL -> "MODE_NORMAL"
                    AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
                    AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
                    else -> "OTHER_${audioManager.mode}"
                }
                onStatus(
                    "Call activation timeout; exact_active=$exactActive; audio_mode=$mode; cleanup=$cleanup",
                )
                return
            }
            if (!handler.postDelayed({ poll() }, CALL_ACTIVE_POLL_MS)) {
                val cleanup = ownedCalls.release(ownedCall)
                running.set(false)
                onStatus("Call state watchdog unavailable; cleanup=$cleanup")
            }
        }

        poll()
    }

    private fun startLocalRuntime(
        appContext: Context,
        telecomManager: TelecomManager,
        grant: CampaignAuthorizationGrant,
        ownedCall: CampaignOwnedCallSession,
        request: CampaignExecutionRequest,
        authorizationStore: CampaignAuthorizationStore,
        runConfiguration: ConversationRunConfiguration,
        appointmentTask: AppointmentMvpTask?,
        onAttemptFinished: (Boolean, String?) -> Unit,
        onStatus: (String) -> Unit,
    ) {
        if (!running.get()) return
        val sessionId = (if (appointmentTask == null) "app-clir-" else "app-appointment-") +
            grant.grantId.filter(Char::isLetterOrDigit).take(20).ifBlank { "campaign" }
        val phoneDisclosureAuthorized =
            IdentityFieldId.PHONE in runConfiguration.preparation.scopedDataFields(grant.allowedDisclosureFields)

        val launched = runCatching {
            val launchedAudio = AndroidConversationRuntime.run(
                context = appContext,
                sessionId = sessionId,
                maxTurns = AndroidConversationRuntime.TASK_SCOPED_TURNS,
                scenario = if (appointmentTask == null) {
                    ClirConversationScenario(appContext, sessionId, runConfiguration, phoneDisclosureAuthorized)
                } else {
                    AppointmentMvpConversationScenario(appContext, appointmentTask,
                        runConfiguration as ConversationRunConfiguration.ModelDirect)
                },
                runConfiguration = runConfiguration,
                callStillActive = {
                    runCatching { ownedCall.isExactCallActive() }.getOrDefault(false)
                },
                evidenceRecorder = if (grant.recordCallEvidence) {
                    CallSessionEvidenceRecorder.create(appContext)
                } else null,
                liveAuthorization = {
                    running.get() &&
                        runCatching { ownedCall.isExactCallActive() }.getOrDefault(false) &&
                        (if (appointmentTask == null)
                            CampaignAccountScope.currentVoiceSubscription(appContext)
                        else CampaignAccountScope.forSubscription(
                            appContext, appointmentTask.subscriptionId)) == request.accountScope &&
                        authorizationStore.isAuthorizedInFlight(
                            grantId = grant.grantId,
                            request = request,
                        )
                },
            ) { result ->
                val report = result.report
                val probeSuccess = result.termination == ConversationTermination.Completed
                val carrierReportedSuccess = reportValue(report, "clir_carrier_reported_success") == "true"
                val appointmentReview = appointmentTask?.let {
                    AppointmentMvpStore(appContext).review(it.grantId)
                }
                val appointmentReviewReady = appointmentReview != null &&
                    probeSuccess && reportValue(report, "appointment_review_required") == "true"
                val failureReason = if (result.termination == ConversationTermination.Completed) null else result.termination.code
                audioSession.set(null)
                val cleanup = ownedCalls.release(ownedCall)
                val wasRunning = running.getAndSet(false)

                if (wasRunning) {
                    if (appointmentTask == null) {
                        onAttemptFinished(probeSuccess && carrierReportedSuccess, failureReason)
                    } else {
                        onAttemptFinished(appointmentReviewReady, failureReason)
                    }
                    onStatus(when {
                        appointmentReviewReady ->
                            "REVIEW_REQUIRED; user must verify appointment; no booking confirmed; cleanup=$cleanup"
                        appointmentTask != null ->
                            "Appointment ended without confirmed review; cleanup=$cleanup"
                        probeSuccess && carrierReportedSuccess ->
                            "Carrier reports CLIR activation, independent network verification pending; " +
                                "cleanup=$cleanup"

                        failureReason != null ->
                            "Campaign call ended without accepted success: $failureReason; " +
                                "cleanup=$cleanup"

                        else ->
                            "Campaign call ended without accepted success; " +
                                "cleanup=$cleanup"
                    })
                }
            }
            if (running.get()) {
                audioSession.set(launchedAudio)
                if (!running.get()) audioSession.getAndSet(null)?.stopAudio(ConversationStopReason.SESSION_CANCELLED)
            } else {
                launchedAudio?.stopAudio(ConversationStopReason.SESSION_CANCELLED)
            }
        }
        if (launched.isFailure) {
            val cleanup = ownedCalls.release(ownedCall)
            running.set(false)
            onStatus(
                "Campaign runtime launch failed: " +
                    "${launched.exceptionOrNull()?.javaClass?.simpleName ?: "unknown"}; " +
                    "cleanup=$cleanup",
            )
        }
    }

    private fun requiredPermissionFailure(context: Context): String? {
        val required = listOf(
            Manifest.permission.RECORD_AUDIO to "microphone",
            Manifest.permission.CALL_PHONE to "phone-call",
            Manifest.permission.READ_PHONE_STATE to "phone-state",
        )
        val missing = required.firstOrNull { (permission, _) ->
            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        return missing?.let { (_, label) -> "Required $label permission is not granted" }
    }

    /** The only permitted hangup is through the exact call handle returned by the dial controller. */
    private fun cleanupOwnedCall(
        ownedCall: CampaignOwnedCallSession,
    ): String = runCatching {
        // The exact session is the only authority here. A global TelecomManager
        // state cannot prove that our call ended, and may describe another call.
        if (ownedCall.disconnectExactCall()) "targeted_disconnect_requested"
        else "targeted_disconnect_failed"
    }.getOrDefault("targeted_disconnect_exception")

    private fun reportValue(report: String, key: String): String? =
        report.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .firstOrNull { (candidate, _) -> candidate == key }
            ?.second
}
