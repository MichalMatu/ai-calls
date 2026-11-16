package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.telecom.TelecomManager
import pl.michalmatu.aicallbridge.session.S8RtpLoopbackSmokeProbe

/**
 * DUMP-protected, owner-triggered synthetic RTP smoke on the device itself.
 * Uses only 127.0.0.1, never phone audio, an operator or a SIP account.
 */
class S8RtpLoopbackSmokeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A synchronous, PII-free marker distinguishes launch failure from blocked RTP.
        PrivateDiagnosticReportStore.write(
            filesDir, "s8-rtp-loopback-report.txt",
            Report(false, false, false, "running").render()
        )
        Thread({
            val status = try {
                val telecom = getSystemService(TelecomManager::class.java)
                val audio = getSystemService(AudioManager::class.java)
                val allowed = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                    PackageManager.PERMISSION_GRANTED &&
                    telecom != null && !telecom.isInCall && !telecom.isInManagedCall &&
                    audio != null && audio.mode != AudioManager.MODE_IN_CALL
                if (!allowed) {
                    Report(false, false, false, "blocked_not_idle_or_permission")
                } else {
                    val result = S8RtpLoopbackSmokeProbe.run()
                    Report(
                        result.tx(), result.rx(), result.stop(),
                        if (result.allPassed()) "pass" else "failed"
                    )
                }
            } catch (_: Throwable) {
                Report(false, false, false, "failed")
            }
            PrivateDiagnosticReportStore.write(
                filesDir,
                "s8-rtp-loopback-report.txt",
                status.render()
            )
            runOnUiThread { finish() }
        }, "s8-rtp-offcall-smoke").apply { isDaemon = true }.start()
    }

    private data class Report(
        val tx: Boolean,
        val rx: Boolean,
        val stop: Boolean,
        val status: String,
    ) {
        fun render(): String = buildString {
            appendLine("probe=s8_rtp_loopback")
            appendLine("transport=localhost_only")
            appendLine("pcm_rate_hz=8000")
            appendLine("tx=$tx")
            appendLine("rx=$rx")
            appendLine("stop=$stop")
            appendLine("status=$status")
            appendLine("real_call=false")
            appendLine("report_complete=true")
        }
    }
}
