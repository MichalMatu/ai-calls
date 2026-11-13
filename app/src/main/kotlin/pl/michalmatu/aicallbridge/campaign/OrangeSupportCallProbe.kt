package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import org.json.JSONObject

/**
 * DUMP-protected, debuggable-only, one-number physical acceptance seam.
 * Never controls the system call list, never records and never emits phone/account data.
 * The only stop path goes through the exact call object owned by this session.
 */
internal object OrangeSupportCallPolicy {
    const val target = "510100100"
    const val maxCallMs = 35_000L
    fun allowedTarget(number: String, approved: Boolean) = approved && number == target
}

internal object OrangeSupportCallProbe {
    internal data class Result(val ok: Boolean, val code: String, val data: JSONObject = JSONObject())
    private val main = Handler(Looper.getMainLooper())
    private var session: AndroidTelecomOwnedCallSession? = null
    private var deadline: Runnable? = null
    private var stopRequested = false
    private var watchdogFired = false
    private var lastCode = "not_started"

    private fun idle(context: Context): Boolean {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return runCatching { !telecom.isInCall }.getOrDefault(false)
    }

    private fun status(context: Context): JSONObject {
        val exact = session
        val observed = AndroidOwnedInCallService.currentCall()
        val bound = exact != null && observed != null && exact.isBoundTo(observed)
        return JSONObject()
            .put("call_idle", idle(context))
            .put("session_present", exact != null)
            .put("exact_call_bound", bound)
            .put("exact_call_active", exact?.isExactCallActive() == true)
            .put("exact_call_connecting", exact?.isExactCallConnecting() == true)
            .put("stop_requested", stopRequested)
            .put("watchdog_fired", watchdogFired)
            .put("last_code", lastCode)
    }

    fun snapshot(context: Context): Result = Result(true, "status", status(context))

    fun start(context: Context, number: String, approved: Boolean): Result {
        if (Looper.myLooper() != Looper.getMainLooper()) return Result(false, "main_thread_required")
        if (!OrangeSupportCallPolicy.allowedTarget(number, approved)) return Result(false, "target_not_approved")
        if (Build.MODEL != "SM-S906B" ||
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0)
            return Result(false, "physical_debug_s22_required")
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return Result(false, "phone_permissions_required")
        if (!idle(context)) return Result(false, "phone_not_idle")
        if (!AndroidTelecomOwnedCallController.canControl(context)) return Result(false, "dialer_role_required")
        val telecom = context.getSystemService(TelecomManager::class.java)
            ?: return Result(false, "telecom_unavailable")
        val account = CampaignPhoneAccountResolver.resolveDefaultVoiceAccount(context, telecom)
            ?: return Result(false, "exact_voice_sim_required")
        // A second attempt is impossible while the previous call is active.
        if (session != null && !stopRequested) return Result(false, "previous_probe_not_stopped")
        deadline?.let(main::removeCallbacks)
        deadline = null
        val controller = AndroidTelecomOwnedCallController.obtain(context)
            ?: return Result(false, "owned_controller_unavailable")
        val initiated = runCatching {
            controller.dial(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)),
                account, number)
        }.getOrElse {
            lastCode = "telecom_dial_rejected"
            return Result(false, lastCode)
        }
        val exact = initiated as? AndroidTelecomOwnedCallSession
            ?: return Result(false, "unexpected_call_controller")
        session = exact
        stopRequested = false
        watchdogFired = false
        lastCode = "dial_requested"
        deadline = Runnable {
            if (session === exact) {
                watchdogFired = true
                stopExact(context, "timeout_stop_requested")
            }
        }.also { main.postDelayed(it, OrangeSupportCallPolicy.maxCallMs) }
        return Result(true, "dial_requested", status(context))
    }

    private fun stopExact(context: Context, reason: String): Result {
        val exact = session ?: return Result(false, "no_probe_session", status(context))
        stopRequested = true
        val stopped = exact.disconnectExactCall()
        lastCode = if (stopped) reason else "exact_disconnect_failed"
        // Deliberately retain the exact session for an onCallAdded callback that
        // arrives after STOP: attachIfMatching will disconnect this exact Call.
        deadline?.let(main::removeCallbacks)
        deadline = null
        return Result(stopped, lastCode, status(context))
    }

    fun stop(context: Context): Result = stopExact(context, "operator_stop_requested")
}
