package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.telecom.TelecomManager
import pl.michalmatu.aicallbridge.LocalSpeechTextPipelineProbe

/**
 * DUMP-gated owner diagnostic: synthetic TTS-generated Polish phrase -> real
 * on-device Android STT -> deterministic LOCAL text reply -> app approval ->
 * real local TTS PCM. Never touches a microphone source, a carrier or a call.
 *
 * Unlike the earlier RTP probe this evaluates STT/TTS availability on the
 * exact installed S22. It does NOT prove the LiteRT model or a SIP call.
 * Only allowlisted booleans are stored; no transcript or raw speech data.
 */
class S8LocalSpeechSmokeActivity : Activity() {
    private var ownedProbe: AutoCloseable? = null
    private var complete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        write("running", false, false, false)
        val telecom = getSystemService(TelecomManager::class.java)
        val audio = getSystemService(AudioManager::class.java)
        val idle = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED && telecom != null &&
            !telecom.isInCall && !telecom.isInManagedCall &&
            audio != null && audio.mode != AudioManager.MODE_IN_CALL &&
            audio.mode != AudioManager.MODE_IN_COMMUNICATION
        val permitted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!idle || !permitted) {
            complete("blocked_permissions_or_busy", false, false, false)
            return
        }

        ownedProbe = LocalSpeechTextPipelineProbe.run(this) { report ->
            if (complete) return@run
            val ok = report.lines().any { it == "text_pipeline_success=true" }
            val stt = report.lines().any { it == "stt_ready=true" }
            val approved = report.lines().any { it == "approved_output_pcm_nonempty=true" }
            val tts = report.lines().any { it.startsWith("output_tts_pcm_bytes=") } &&
                approved
            complete(if (ok && stt && tts) "pass" else "failed",
                stt, approved, tts)
        }
    }

    private fun complete(status: String, stt: Boolean, approved: Boolean, tts: Boolean) {
        if (complete) return
        complete = true
        ownedProbe?.close()
        ownedProbe = null
        write(status, stt, approved, tts)
        finish()
    }

    private fun write(status: String, stt: Boolean, approved: Boolean, tts: Boolean) {
        PrivateDiagnosticReportStore.write(filesDir, REPORT_FILE, buildString {
            appendLine("probe=s8_local_speech")
            appendLine("audio_source=synthetic_local_tts")
            appendLine("text_backend=deterministic_local")
            appendLine("on_device_stt_ready=$stt")
            appendLine("app_output_approved=$approved")
            appendLine("local_tts_pcm_ready=$tts")
            appendLine("status=$status")
            appendLine("real_call=false")
            appendLine("remote_model=false")
            appendLine("report_complete=${status != "running"}")
        })
    }

    override fun onStop() {
        if (!complete) complete("stopped", false, false, false)
        super.onStop()
    }

    private companion object {
        const val REPORT_FILE = "s8-local-speech-report.txt"
    }
}
