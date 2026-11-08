package pl.michalmatu.aicallbridge.debug

import android.Manifest
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.TelecomManager
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.campaign.AndroidOwnedInCallService
import pl.michalmatu.aicallbridge.campaign.AppOwnedCampaignService
import pl.michalmatu.aicallbridge.campaign.AppOwnedClirCampaignExecutor
import pl.michalmatu.aicallbridge.conversation.AndroidSkillLabSessionFactory
import pl.michalmatu.aicallbridge.conversation.ConversationChatFeed
import pl.michalmatu.aicallbridge.conversation.SkillLabPhase
import pl.michalmatu.aicallbridge.conversation.SkillLabRunner
import pl.michalmatu.aicallbridge.conversation.SkillLabUiState
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantPolicyStore

/**
 * Private USB/ADB mailbox. The exported receiver is DUMP-permission protected
 * in AndroidManifest and refuses non-debuggable variants. No TCP listener,
 * public chat provider, file-based grant, auto-dial or raw telephony API.
 *
 * The Mac writes one request in the app sandbox via "adb shell run-as" and
 * broadcasts only its nonce. No user's text travels in shell arguments.
 */
class UsbLabControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nonce = intent.getStringExtra(EXTRA_NONCE).orEmpty()
        if (!NONCE.matches(nonce)) return
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val dir = File(context.filesDir, "usb-lab-control").apply { mkdirs() }
        val requestFile = File(dir, "request.json")
        val rawRequest = runCatching { requestFile.readText() }.getOrNull()
        // One-shot mailbox: never execute a stale action after process restart.
        requestFile.delete()
        val request = runCatching { JSONObject(rawRequest ?: "") }.getOrNull()
        if (request?.optString("nonce") != nonce) return
        val action = request.optString("action")
        val reply = try {
            UsbLabControl.execute(context.applicationContext, action, request)
        } catch (_: Exception) {
            UsbLabControl.Result(false, "internal_command_failure")
        }
        val response = JSONObject()
            .put("protocol", 1).put("nonce", nonce).put("action", action)
            .put("ok", reply.ok).put("code", reply.code)
            .put("data", reply.data)
        val temp = File(dir, "response.json.new")
        val destination = File(dir, "response.json")
        runCatching {
            temp.writeText(response.toString())
            check(temp.renameTo(destination)) { "response_write_failed" }
            // Never audit request bodies, call identifiers, messages or transcripts.
            if (action !in setOf("status", "call_status", "lab_status", "config_get")) {
                File(dir, "audit.jsonl").appendText(
                    JSONObject().put("at_ms", System.currentTimeMillis())
                        .put("action", action).put("code", reply.code)
                        .put("ok", reply.ok).toString() + "\n")
            }
        }
    }

    companion object {
        const val EXTRA_NONCE = "usb_lab_nonce"
        private val NONCE = Regex("[a-fA-F0-9]{32}")
    }
}

/** Owns one isolated, off-call native Lab dialogue for the duration of this app process. */
internal object UsbLabControl {
    internal data class Result(val ok: Boolean, val code: String,
        val data: JSONObject = JSONObject())

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lab: SkillLabRunner? = null
    private var sessionId: String? = null
    private var budget = 4_096
    private var preset = "natural"
    private var owner: Context? = null
    private var evidenceFile: File? = null

    private fun unavailableIfLive(context: Context): String? {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val callIdle = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED && runCatching {
                telecom != null && !telecom.isInCall &&
                    audio != null && audio.mode != AudioManager.MODE_IN_CALL
            }.getOrDefault(false)
        val campaignRunning = AppOwnedClirCampaignExecutor.isRunning() ||
            runCatching {
                context.getSystemService(ActivityManager::class.java)
                    .getRunningServices(100).any { it.service.className.endsWith("AppOwnedCampaignService") }
            }.getOrDefault(true)
        return when {
            !callIdle -> "live_call_active"
            campaignRunning || ConversationChatFeed.snapshot().active -> "live_ai_active"
            else -> null
        }
    }

    fun execute(context: Context, action: String, request: JSONObject): Result = when (action) {
        "status", "call_status", "config_get" -> Result(true, "ok", snapshot(context))
        "lab_start" -> start(context, request)
        "lab_send" -> send(request)
        "lab_status" -> Result(true, "ok", synchronized(lock) { labSnapshot() })
        "lab_stop" -> stop(request)
        "call_takeover" -> takeover(context)
        "call_resume" -> resume()
        "call_stop_owned" -> stopOwned(context)
        "incoming_ai_disable" -> {
            val ok = AndroidOwnedInCallService.stopIncomingAi(context)
            Result(ok, if (ok) "incoming_ai_disabled" else "incoming_ai_disable_failed")
        }
        else -> Result(false, "unknown_action")
    }

    /** Never return any phone number, account ID, chat text, prompt or private fact. */
    private fun snapshot(context: Context): JSONObject {
        val profile = AudioEngineProfileStore(context).load()
        val call = AndroidOwnedInCallService.currentCall()
        val feed = ConversationChatFeed.snapshot()
        val telecom = context.getSystemService(TelecomManager::class.java)
        val phoneActive =
            if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED)
                runCatching { telecom?.isInCall }.getOrNull()
            else null
        val incoming = runCatching { IncomingAssistantPolicyStore(context).load().mode.name }
            .getOrDefault("UNKNOWN")
        return JSONObject()
            .put("call_idle", phoneActive == false)
            .put("call_tracked", call != null)
            .put("call_state", call?.state ?: -1)
            .put("call_direction", when (call?.details?.callDirection) {
                Call.Details.DIRECTION_INCOMING -> "incoming"
                Call.Details.DIRECTION_OUTGOING -> "outgoing"
                else -> "unknown"
            })
            .put("incoming_ai_state", call?.let(AndroidOwnedInCallService::incomingState) ?: "none")
            .put("incoming_policy", incoming)
            .put("outgoing_ai_running", AppOwnedClirCampaignExecutor.isRunning())
            .put("live_chat_active", feed.active)
            .put("live_chat_revision", feed.revision)
            .put("live_chat_line_count", feed.lines.size)
            .put("live_chat_stage", if (feed.active) "active" else "inactive")
            .put("model", profile?.dialogue?.name ?: "UNKNOWN")
            .put("input", profile?.input?.name ?: "UNKNOWN")
            .put("output", profile?.output?.name ?: "UNKNOWN")
            .put("voice", if (profile?.voiceId.isNullOrBlank()) "auto" else "configured")
            .put("lab", synchronized(lock) { labSnapshot() })
    }

    private fun labSnapshot(): JSONObject {
        val state = lab?.state
        val pending = state?.pending
        return JSONObject()
            .put("session_id", sessionId ?: JSONObject.NULL)
            .put("process_session_active", lab != null)
            .put("phase", state?.phase?.name ?: "NONE")
            .put("turns", state?.completed?.size ?: 0)
            .put("pending_turn", pending?.turnIndex ?: 0)
            .put("last_elapsed_ms", state?.completed?.lastOrNull()?.observation?.elapsedMs ?: -1)
            .put("error_category", state?.error?.substringBefore(':')
                ?.replace(Regex("[^a-zA-Z0-9_]"), "")?.take(90) ?: JSONObject.NULL)
            .put("context_tokens", budget)
            .put("preset", preset)
            .put("private_transcript_ready", evidenceFile?.exists() == true)
    }

    private fun persist(context: Context, id: String, state: SkillLabUiState) {
        // The file is private to the app; readable on the connected Mac via run-as.
        val turns = JSONArray()
        state.completed.forEach {
            turns.put(JSONObject()
                .put("turn", it.pending.turnIndex)
                .put("user", it.pending.utterance)
                .put("assistant", it.observation.reply)
                .put("elapsed_ms", it.observation.elapsedMs)
                .put("end_proposed", it.observation.actualEnd))
        }
        val data = JSONObject()
            .put("protocol", 1).put("session_id", id)
            .put("context_tokens", budget).put("phase", state.phase.name)
            .put("error_category", state.error?.substringBefore(':') ?: JSONObject.NULL)
            .put("turns", turns)
        val dir = File(context.filesDir, "usb-lab-control").apply { mkdirs() }
        val temp = File(dir, "transcript.json.new")
        val dest = File(dir, "transcript.json")
        temp.writeText(data.toString())
        check(temp.renameTo(dest)) { "private_transcript_write_failed" }
        evidenceFile = dest
    }

    private fun start(context: Context, request: JSONObject): Result = synchronized(lock) {
        unavailableIfLive(context)?.let { return Result(false, it) }
        val current = lab
        if (current != null && current.state.phase !in setOf(
                SkillLabPhase.COMPLETED, SkillLabPhase.STOPPED, SkillLabPhase.FAILED)) {
            return Result(false, "lab_session_already_active", labSnapshot())
        }
        val tokens = request.optInt("context_tokens", 4_096)
        if (tokens <= 0) return Result(false, "invalid_context_budget")
        val selectedPreset = request.optString("preset", "natural").ifBlank { "natural" }
        // No owner vault values; AndroidSkillLabSessionFactory builds the existing Lab-only role.
        current?.close()
        val nextId = UUID.randomUUID().toString()
        val factory = AndroidSkillLabSessionFactory(context, tokens)
        budget = tokens
        preset = selectedPreset
        owner = context
        evidenceFile = null
        val runner = SkillLabRunner(factory,
            dispatchToUi = { callback -> mainHandler.post(callback) },
            onState = { state ->
                synchronized(lock) {
                    if (sessionId == nextId) {
                        runCatching { persist(context, nextId, state) }
                    }
                }
            })
        lab = runner
        sessionId = nextId
        val started = runner.startManual(selectedPreset)
        if (!started) {
            runner.close()
            lab = null
            sessionId = null
            return Result(false, "lab_start_rejected")
        }
        Result(true, "lab_started", labSnapshot())
    }

    private fun send(request: JSONObject): Result = synchronized(lock) {
        val id = request.optString("session_id")
        if (sessionId != id || lab == null) return Result(false, "lab_session_mismatch")
        val context = owner ?: return Result(false, "lab_process_not_ready")
        unavailableIfLive(context)?.let { return Result(false, it) }
        val text = request.optString("text")
        if (text.isBlank()) return Result(false, "empty_lab_message")
        val ok = lab?.submitManual(text) == true
        Result(ok, if (ok) "lab_turn_queued" else "lab_not_ready", labSnapshot())
    }

    private fun stop(request: JSONObject): Result = synchronized(lock) {
        val id = request.optString("session_id")
        if (id != sessionId || lab == null) return Result(false, "lab_session_mismatch")
        val stopped = lab?.stop() == true
        Result(stopped, if (stopped) "lab_stop_requested" else "lab_not_running",
            labSnapshot())
    }

    private fun takeover(context: Context): Result {
        val call = AndroidOwnedInCallService.currentCall()
            ?: return Result(false, "no_tracked_call")
        if (call.state != Call.STATE_ACTIVE && call.state != Call.STATE_HOLDING)
            return Result(false, "call_not_active")
        // First give existing incoming coordinator priority only for confirmed
        // incoming call. Never take over somebody else's outgoing call.
        if (call.details?.callDirection == Call.Details.DIRECTION_INCOMING &&
            AndroidOwnedInCallService.incomingState(call) != null) {
            AndroidOwnedInCallService.takeOverIncoming(call)
            return Result(true, "incoming_ai_taken_over")
        }
        if (!AppOwnedClirCampaignExecutor.takeOver(call))
            return Result(false, "not_exact_outgoing_ai_call")
        val dispatched = runCatching {
            context.startService(Intent(context, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_PAUSE_AFTER_TAKEOVER))
            true
        }.getOrDefault(false)
        return Result(dispatched, if (dispatched) "outgoing_ai_taken_over"
            else "outgoing_ai_stopped_service_pause_failed")
    }

    private fun resume(): Result {
        val call = AndroidOwnedInCallService.currentCall()
            ?: return Result(false, "no_tracked_call")
        if (call.state != Call.STATE_ACTIVE ||
            call.details?.callDirection != Call.Details.DIRECTION_INCOMING ||
            AndroidOwnedInCallService.incomingState(call) == null)
            return Result(false, "not_managed_active_incoming")
        val ok = AndroidOwnedInCallService.resumeIncoming(call)
        return Result(ok, if (ok) "incoming_ai_resumed" else "incoming_resume_rejected")
    }

    private fun stopOwned(context: Context): Result {
        if (!AppOwnedClirCampaignExecutor.isRunning())
            return Result(false, "no_exact_outgoing_ai_call")
        // This existing service revokes continuation and stops ONLY the exact
        // app-bound outgoing call; never use global TelecomManager.endCall.
        val dispatched = runCatching {
            context.startService(Intent(context, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_CANCEL_OUTGOING_TASK))
            true
        }.getOrDefault(false)
        return Result(dispatched, if (dispatched) "exact_outgoing_stop_dispatched"
            else "exact_outgoing_stop_dispatch_failed")
    }
}
