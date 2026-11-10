package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.os.Looper
import pl.michalmatu.aicallbridge.conversationcore.OutgoingIvrDtmfGate
import pl.michalmatu.aicallbridge.conversationcore.OutgoingIvrDtmfRequest
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.net.Uri
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A carrier call controller, available only after Android grants the default-dialer role.
 * The InCallService supplies an actual Call object; no global endCall or KEYCODE_ENDCALL.
 */
internal class AndroidTelecomOwnedCallController private constructor(
    private val app: Context,
) : CampaignOwnedCallController {
    override fun dial(intent: Intent, account: PhoneAccountHandle, target: String): CampaignOwnedCallSession {
        check(canControl(app)) { "android_default_dialer_role_required" }
        require(intent.action == Intent.ACTION_CALL) { "invalid_dial_intent" }
        require(intent.data?.scheme == "tel" && intent.data?.schemeSpecificPart == target) {
            "dial_target_mismatch"
        }
        val telecom = app.getSystemService(TelecomManager::class.java)
            ?: error("telecom_unavailable")
        check(app.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            "call_phone_permission_required"
        }
        check(!telecom.isInCall) { "another_call_already_active" }
        // An explicitly selected active voice SIM is a valid target too. The caller
        // must already hold a grant for the exact subscription; this port validates
        // the phone account again at the irreversible Telecom edge.
        check(account in telecom.callCapablePhoneAccounts) { "voice_account_unavailable" }
        val tel = app.getSystemService(android.telephony.TelephonyManager::class.java)
            ?: error("telephony_unavailable")
        // getSubscriptionId() is API 30+. Deny rather than guess on Android 10,
        // preserving the exact-selected-SIM guarantee for real calls.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            error("api_30_required_for_exact_voice_subscription")
        }
        val subscriptionId = tel.createForPhoneAccountHandle(account)?.subscriptionId
            ?: error("voice_subscription_unavailable")
        check(CampaignPhoneAccountResolver.resolveSelectedVoiceAccount(
            app, telecom, subscriptionId) == account) { "voice_account_changed" }

        val session = AndroidTelecomOwnedCallSession(target, account, SystemClock.elapsedRealtime())
        check(AndroidOwnedInCallService.reserve(session)) { "owned_call_pending" }
        try {
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
                putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, Bundle().apply {
                    putString(AndroidOwnedInCallService.OWNED_REQUEST_ID, session.requestId)
                })
            }
            telecom.placeCall(Uri.fromParts("tel", target, null), extras)
            return session
        } catch (error: Throwable) {
            AndroidOwnedInCallService.abandon(session)
            throw error
        }
    }

    companion object {
        fun canControl(context: Context): Boolean {
            val role = context.getSystemService(RoleManager::class.java) ?: return false
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
            return role.isRoleHeld(RoleManager.ROLE_DIALER) &&
                telecom.defaultDialerPackage == context.packageName
        }

        fun obtain(context: Context): AndroidTelecomOwnedCallController? =
            if (canControl(context)) AndroidTelecomOwnedCallController(context.applicationContext) else null
    }
}

/** Only this exact Call object can be disconnected, including if STOP arrives before onCallAdded. */
internal class AndroidTelecomOwnedCallSession(
    override val dialedTarget: String,
    override val phoneAccount: PhoneAccountHandle,
    val requestedAtElapsedMs: Long,
) : CampaignOwnedCallSession {
    val requestId: String = UUID.randomUUID().toString()
    private val boundCall = AtomicReference<Call?>(null)
    private val stopRequested = AtomicBoolean(false)
    private val dtmfPort = AtomicReference<AndroidTelecomDtmfDigitPort?>(null)
    private val dtmfRevoked = AtomicBoolean(false)

    fun attachIfMatching(call: Call, nowElapsedMs: Long): Boolean {
        val details = call.details ?: return false
        val target = details.handle?.schemeSpecificPart ?: return false
        val account = details.accountHandle ?: return false
        val metadataRequest = details.intentExtras?.getString(AndroidOwnedInCallService.OWNED_REQUEST_ID)
        if (!CampaignCallMatchPolicy.accept(
                expectedTarget = dialedTarget,
                actualTarget = target,
                sameAccount = account == phoneAccount,
                outgoing = details.callDirection == Call.Details.DIRECTION_OUTGOING,
                ageMs = nowElapsedMs - requestedAtElapsedMs,
                requestTokenMatches = metadataRequest == null || metadataRequest == requestId,
            )
        ) return false
        if (!boundCall.compareAndSet(null, call)) return boundCall.get() === call
        if (stopRequested.get()) runCatching { call.disconnect() }
        return true
    }

    /**
     * A grant-bound integration seam, NOT an autonomous IVR command path. No gate is
     * constructed without the exact active outbound Call and an explicit authorizer.
     * All digit requests must run on Telecom's main looper.
     */
    internal fun createAuthorizedDtmfGate(
        generation: Long,
        authorized: (OutgoingIvrDtmfRequest) -> Boolean,
    ): OutgoingIvrDtmfGate? {
        if (generation <= 0 || dtmfRevoked.get() ||
            Looper.myLooper() != Looper.getMainLooper() || !isExactCallActive()) return null
        val port = AndroidTelecomDtmfDigitPort(
            exactCall = { boundCall.get()?.takeIf { it.state == Call.STATE_ACTIVE && !stopRequested.get() } },
            active = { !dtmfRevoked.get() && isExactCallActive() },
            authorized = { request -> !dtmfRevoked.get() && request.callGeneration == generation && authorized(request) },
        )
        if (!dtmfPort.compareAndSet(null, port)) { port.close(); return null }
        if (dtmfRevoked.get() || !isExactCallActive()) { revokeDtmf(); return null }
        return OutgoingIvrDtmfGate(
            active = { !dtmfRevoked.get() && it == generation && isExactCallActive() },
            authorized = { request -> !dtmfRevoked.get() && request.callGeneration == generation &&
                isExactCallActive() && authorized(request) },
            port = port,
        )
    }

    /** Terminal for this owned session. A manual takeover never re-arms AI tones. */
    internal fun revokeDtmf() {
        dtmfRevoked.set(true)
        dtmfPort.getAndSet(null)?.close()
    }

    internal fun onExactCallRemoved(call: Call) {
        if (boundCall.get() !== call) return
        stopRequested.set(true)
        revokeDtmf()
    }

    override fun isExactCallActive(): Boolean =
        !stopRequested.get() && boundCall.get()?.state == Call.STATE_ACTIVE

    override fun isWifiCalling(): Boolean =
        boundCall.get()?.details?.hasProperty(Call.Details.PROPERTY_WIFI) == true

    override fun disconnectExactCall(): Boolean {
        stopRequested.set(true)
        revokeDtmf()
        val call = boundCall.get() ?: return true // deferred until the matched Call arrives
        return runCatching {
            if (call.state != Call.STATE_DISCONNECTED) call.disconnect()
            true
        }.getOrDefault(false)
    }

    fun isBoundTo(call: Call): Boolean = boundCall.get() === call

    fun expired(nowElapsedMs: Long): Boolean =
        boundCall.get() == null && nowElapsedMs - requestedAtElapsedMs > 60_000L
}

/** Pure criteria, with no privilege to dial or terminate a call. */
internal object CampaignCallMatchPolicy {
    fun accept(
        expectedTarget: String,
        actualTarget: String,
        sameAccount: Boolean,
        outgoing: Boolean,
        ageMs: Long,
        requestTokenMatches: Boolean,
    ): Boolean = expectedTarget == actualTarget &&
        sameAccount && outgoing && requestTokenMatches && ageMs in 0L..30_000L
}
