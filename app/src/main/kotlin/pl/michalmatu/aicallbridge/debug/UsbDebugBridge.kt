package pl.michalmatu.aicallbridge.debug

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import java.io.File
import org.json.JSONObject
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.AudioEngineReadinessProbe
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.audioengines.PiperServiceContract
import pl.michalmatu.aicallbridge.audioengines.SpeechInputId
import pl.michalmatu.aicallbridge.audioengines.SpeechOutputId
import pl.michalmatu.aicallbridge.campaign.CampaignAuthorizationStore

/**
 * Small ADB-shell/DUMP-only extension of the existing DiagnosticProbeActivity.
 * It has no call-placing, STOP bypass, grant creation, identity or transcript API.
 * Responses stay in the app's run-as-only private directory (never logcat).
 */
internal object UsbDebugBridge {
    const val EXTRA_ACTION = "usb_debug_action"
    const val EXTRA_NONCE = "usb_debug_nonce"
    private const val RESPONSE_DIR = "usb-debug-bridge"
    private val noncePattern = Regex("[a-zA-Z0-9_-]{16,64}")

    fun handle(activity: Activity, intent: Intent, finish: (String) -> Unit) {
        val action = intent.getStringExtra(EXTRA_ACTION).orEmpty()
        val nonce = intent.getStringExtra(EXTRA_NONCE).orEmpty()
        if (!noncePattern.matches(nonce)) {
            finish("USB debug: invalid nonce")
            return
        }

        fun reply(ok: Boolean, code: String, details: JSONObject? = null) {
            val result = JSONObject()
                .put("protocol", UsbDebugProtocol.VERSION)
                .put("nonce", nonce)
                .put("action", action)
                .put("ok", ok)
                .put("code", code)
                .put("data", details ?: JSONObject())
            try {
                val directory = File(activity.filesDir, RESPONSE_DIR).apply { mkdirs() }
                val temporary = File(directory, "response.json.new")
                val destination = File(directory, "response.json")
                temporary.writeText(result.toString())
                check(temporary.renameTo(destination)) { "response_atomic_write_failed" }
                if (action != "status" && action != "evidence_status") {
                    File(directory, "audit.jsonl").appendText(
                        JSONObject()
                            .put("at_ms", System.currentTimeMillis())
                            .put("action", action)
                            .put("code", code)
                            .put("ok", ok)
                            .toString() + "\n",
                    )
                }
                finish("USB debug: $code")
            } catch (_: Exception) {
                finish("USB debug: private result write failed")
            }
        }

        try {
            if (!UsbDebugProtocol.accepts(action)) {
                reply(false, "unknown_action")
                return
            }
            when (action) {
                "status", "evidence_status" -> reply(true, "ok", snapshot(activity))
                "profile_reset", "profile_set", "grant_revoke", "evidence_disable",
                "profile_check" -> {
                    val guard = mutationGuard(activity)
                    if (guard != null) {
                        reply(false, guard)
                        return
                    }
                    when (action) {
                        "profile_reset" -> {
                            val saved = AudioEngineProfileStore(activity).save(AudioEngineProfile.STABLE)
                            reply(saved, if (saved) "profile_saved" else "profile_save_failed")
                        }
                        "grant_revoke" -> {
                            val grant = CampaignAuthorizationStore(activity).revoke(System.currentTimeMillis())
                            reply(grant != null, if (grant != null) "grant_revoked" else "grant_missing")
                        }
                        "evidence_disable" -> {
                            val grant = CampaignAuthorizationStore(activity).disableEvidenceRecording()
                            reply(grant != null, if (grant != null) "recording_disabled" else "grant_missing")
                        }
                        else -> {
                            val profile = parseProfile(intent)
                            if (profile == null) {
                                reply(false, "invalid_profile")
                                return
                            }
                            AudioEngineRegistry.incompatibility(profile)?.let {
                                reply(false, it)
                                return
                            }
                            verifyOfflineProfile(activity, profile) { ready, reason, voiceCount ->
                                if (!ready) {
                                    reply(false, reason)
                                } else if (action == "profile_check") {
                                    reply(true, "profile_ready",
                                        JSONObject().put("local_polish_voices", voiceCount))
                                } else {
                                    val recheck = mutationGuard(activity)
                                    if (recheck != null) {
                                        reply(false, recheck)
                                    } else {
                                        val saved = runCatching {
                                            AudioEngineProfileStore(activity).save(profile)
                                        }.getOrDefault(false)
                                        reply(saved, if (saved) "profile_saved" else "profile_save_failed")
                                    }
                                }
                            }
                        }
                    }
                }
                else -> reply(false, "unknown_action")
            }
        } catch (_: Exception) {
            reply(false, "bridge_internal_failure")
        }
    }

    private fun parseProfile(intent: Intent): AudioEngineProfile? = runCatching {
        val input = SpeechInputId.valueOf(checkNotNull(intent.getStringExtra("input")))
        val model = DialogueModelId.valueOf(checkNotNull(intent.getStringExtra("dialogue")))
        val output = SpeechOutputId.valueOf(checkNotNull(intent.getStringExtra("output")))
        val voice = intent.getStringExtra("voice")?.takeUnless { it.isBlank() || it == "auto" }
        AudioEngineProfile(input = input, dialogue = model, output = output, voiceId = voice)
    }.getOrNull()

    private fun snapshot(activity: Activity): JSONObject {
        val profile = AudioEngineProfileStore(activity).load()
        val grant = CampaignAuthorizationStore(activity).load()
        val evidenceDir = File(activity.filesDir, "call-session-evidence")
        val sessions = evidenceDir.listFiles()?.count { it.isDirectory } ?: 0
        return JSONObject()
            .put("call_idle", phoneIdle(activity))
            .put("campaign_service_inactive", !campaignActive(activity))
            .put("pending_retry", pendingRetry(activity))
            .put("profile_valid", profile != null)
            .put("input", profile?.input?.name ?: "UNKNOWN")
            .put("dialogue", profile?.dialogue?.name ?: "UNKNOWN")
            .put("output", profile?.output?.name ?: "UNKNOWN")
            .put("voice", profile?.voiceId ?: "auto")
            .put("grant_present", grant != null)
            .put("grant_revoked", grant?.revokedAtEpochMs != null)
            .put("grant_expired", grant?.let { it.expiresAtEpochMs <= System.currentTimeMillis() }
                ?: true)
            .put("recording_enabled", grant?.recordCallEvidence == true)
            .put("private_evidence_sessions", sessions)
    }

    private fun mutationGuard(activity: Activity): String? = when {
        !phoneIdle(activity) -> "phone_not_idle_or_permission_missing"
        campaignActive(activity) -> "campaign_service_active"
        pendingRetry(activity) -> "campaign_retry_pending"
        else -> null
    }

    private fun phoneIdle(activity: Activity): Boolean {
        if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val telecom = activity.getSystemService(TelecomManager::class.java)
        val audio = activity.getSystemService(AudioManager::class.java)
        return runCatching {
            telecom != null && !telecom.isInCall &&
                audio != null && audio.mode != AudioManager.MODE_IN_CALL
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun campaignActive(activity: Activity): Boolean = runCatching {
        activity.getSystemService(ActivityManager::class.java)
            .getRunningServices(100).any { it.service.className.endsWith("AppOwnedCampaignService") }
    }.getOrDefault(true)

    private fun pendingRetry(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences("campaign_continuation_v1", Activity.MODE_PRIVATE)
        return prefs.getString("grant_id", null)?.isNotBlank() == true
    }

    private fun verifyOfflineProfile(
        activity: Activity,
        profile: AudioEngineProfile,
        result: (Boolean, String, Int) -> Unit,
    ) {
        val engineName = if (profile.output == SpeechOutputId.PIPER_PL)
            PiperServiceContract.PACKAGE else null
        val holder = arrayOfNulls<TextToSpeech>(1)
        var completed = false
        fun done(ready: Boolean, reason: String, voices: Int = 0) {
            if (completed) return
            completed = true
            holder[0]?.shutdown()
            result(ready, reason, voices)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            done(false, "tts_probe_timeout")
        }, 20_000)
        val listener = TextToSpeech.OnInitListener { status ->
            activity.runOnUiThread {
                if (completed) return@runOnUiThread
                val tts = holder[0]
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    done(false, "tts_init_failed")
                    return@runOnUiThread
                }
                if (engineName != null && tts.engines.none { it.name == engineName }) {
                    done(false, "piper_engine_absent")
                    return@runOnUiThread
                }
                val voices = tts.voices.orEmpty()
                    .filter { it.locale.language == "pl" && !it.isNetworkConnectionRequired }
                    .filter { engineName == null || "piperVoice" in it.features }
                    .map { it.name }.toSet()
                val readiness = AudioEngineReadinessProbe.check(activity, profile, voices)
                done(readiness.available, readiness.reason ?: "ok", voices.size)
            }
        }
        holder[0] = try {
            if (engineName == null) TextToSpeech(activity, listener)
            else TextToSpeech(activity, listener, engineName)
        } catch (_: Exception) {
            done(false, "tts_init_failed")
            null
        }
    }
}
