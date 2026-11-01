package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.AndroidLiveCallReadiness
import pl.michalmatu.aicallbridge.developerrelay.ChatRelayLiveCallProbe
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

internal object AppOwnedClirCampaignExecutor {
    private const val CALL_ACTIVE_TIMEOUT_MS = 30_000L
    private const val CALL_ACTIVE_POLL_MS = 250L
    private val running = AtomicBoolean(false)

    fun isRunning(): Boolean = running.get()

    fun start(
        context: Context,
        authorizationStore: CampaignAuthorizationStore,
        accountScope: String,
        onStatus: (String) -> Unit,
    ) {
        if (!running.compareAndSet(false, true)) {
            onStatus("Campaign call is already running")
            return
        }

        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val request = ClirCampaignSpec.enableRequest(accountScope)
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

        // No application-owned Call handle exists in the stock Android Telecom path.
        // Fail before dialing rather than risk ending a different foreground call.
        if (!CampaignCallOwnershipPolicy.allowsAutonomousDial(
                CampaignCallOwnershipProof(
                    exactCallIdentityBound = false,
                    targetedTerminationAvailable = false,
                ),
            )
        ) {
            running.set(false)
            onStatus("Dial blocked: exact call ownership and targeted cleanup unavailable")
            return
        }
        val voiceAccount = runCatching {
            val accounts = telecomManager.callCapablePhoneAccounts
            val defaultAccount = telecomManager.getDefaultOutgoingPhoneAccount("tel")
            if (accounts.size == 1 && accounts.single() == defaultAccount) defaultAccount else null
        }.getOrNull()
        if (voiceAccount == null ||
            CampaignAccountScope.currentVoiceSubscription(appContext) != request.accountScope
        ) {
            running.set(false)
            onStatus("Dial blocked: exact default voice SIM/account not established")
            return
        }

        val reservedGrant = authorizationStore.reserveAuthorizedAttempt(
            request = request,
            nowEpochMs = System.currentTimeMillis(),
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

        try {
            appContext.startActivity(callIntent)
        } catch (error: Throwable) {
            running.set(false)
            onStatus("Dial failed: ${error.javaClass.simpleName}")
            return
        }

        onStatus(
            "Authorized campaign dial started " +
                "(attempt ${reservedGrant.attemptsUsed}/${reservedGrant.maxAttempts})",
        )
        waitForActiveCall(
            appContext = appContext,
            handler = handler,
            deadlineEpochMs = System.currentTimeMillis() + CALL_ACTIVE_TIMEOUT_MS,
            telecomManager = telecomManager,
            grant = reservedGrant,
            request = request,
            authorizationStore = authorizationStore,
            onStatus = onStatus,
        )
    }

    private fun waitForActiveCall(
        appContext: Context,
        handler: Handler,
        deadlineEpochMs: Long,
        telecomManager: TelecomManager,
        grant: CampaignAuthorizationGrant,
        request: CampaignExecutionRequest,
        authorizationStore: CampaignAuthorizationStore,
        onStatus: (String) -> Unit,
    ) {
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        if (audioManager == null) {
            running.set(false)
            onStatus("Audio service unavailable")
            return
        }

        fun poll() {
            if (!running.get()) return
            if (audioManager.mode == AudioManager.MODE_IN_CALL) {
                onStatus("Cellular call active; starting local campaign runtime")
                startLocalRuntime(appContext, telecomManager, grant, request, authorizationStore, onStatus)
                return
            }
            if (System.currentTimeMillis() >= deadlineEpochMs) {
                val cleanup = safeCleanupStatus(telecomManager)
                running.set(false)
                onStatus("Call did not become active before timeout; cleanup=$cleanup")
                return
            }
            handler.postDelayed({ poll() }, CALL_ACTIVE_POLL_MS)
        }

        poll()
    }

    private fun startLocalRuntime(
        appContext: Context,
        telecomManager: TelecomManager,
        grant: CampaignAuthorizationGrant,
        request: CampaignExecutionRequest,
        authorizationStore: CampaignAuthorizationStore,
        onStatus: (String) -> Unit,
    ) {
        val sessionId = "app-clir-" +
            grant.grantId.filter(Char::isLetterOrDigit).take(20).ifBlank { "campaign" }
        val phoneDisclosureAuthorized =
            IdentityFieldId.PHONE in grant.allowedDisclosureFields

        ChatRelayLiveCallProbe.run(
            context = appContext,
            sessionId = sessionId,
            maxTurns = ChatRelayLiveCallProbe.MAX_TURNS,
            phoneDisclosureAuthorized = phoneDisclosureAuthorized,
            supervisorRelayEnabled = false,
            liveAuthorization = {
                CampaignAccountScope.currentVoiceSubscription(appContext) == request.accountScope &&
                    authorizationStore.isAuthorizedInFlight(
                        grantId = grant.grantId,
                        request = request,
                        nowEpochMs = System.currentTimeMillis(),
                    )
            },
        ) { report ->
            val probeSuccess = reportValue(report, "chat_relay_live_call_success") == "true"
            val carrierReportedSuccess = reportValue(report, "clir_carrier_reported_success") == "true"
            val failureReason = reportValue(report, "failure_reason")
            val cleanup = safeCleanupStatus(telecomManager)
            running.set(false)

            onStatus(
                when {
                    probeSuccess && carrierReportedSuccess ->
                        "Carrier reports CLIR activation, independent network verification pending; " +
                            "cleanup=$cleanup"

                    failureReason != null ->
                        "Campaign call ended without accepted success: $failureReason; " +
                            "cleanup=$cleanup"

                    else ->
                        "Campaign call ended without accepted success; " +
                            "cleanup=$cleanup"
                },
            )
        }
    }

    private fun requiredPermissionFailure(context: Context): String? {
        val required = listOf(
            Manifest.permission.RECORD_AUDIO to "microphone",
            Manifest.permission.CALL_PHONE to "phone-call",
            Manifest.permission.READ_PHONE_STATE to "phone-state",
            Manifest.permission.ANSWER_PHONE_CALLS to "call-control",
        )
        val missing = required.firstOrNull { (permission, _) ->
            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        return missing?.let { (_, label) -> "Required $label permission is not granted" }
    }

    /** Generic TelecomManager.endCall() could terminate an unrelated call: never invoke it. */
    private fun safeCleanupStatus(telecomManager: TelecomManager): String =
        runCatching {
            if (telecomManager.isInCall) "owned_hangup_unavailable" else "idle"
        }.getOrDefault("call_state_unverified")

    private fun reportValue(report: String, key: String): String? =
        report.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .firstOrNull { (candidate, _) -> candidate == key }
            ?.second
}
