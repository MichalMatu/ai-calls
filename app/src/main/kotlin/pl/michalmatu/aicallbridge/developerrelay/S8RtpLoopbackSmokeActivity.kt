package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.telecom.TelecomManager
import pl.michalmatu.aicallbridge.session.S8RtpLoopbackSmokeProbe
import pl.michalmatu.aicallbridge.session.S8RtpRealtimeBridgeSmokeProbe

/**
 * DUMP-protected, owner-triggered synthetic RTP smoke on the device itself.
 * Uses only 127.0.0.1, never phone audio, an operator or a SIP account.
 */
class S8RtpLoopbackSmokeActivity : Activity() {
    companion object { const val REPORT_FILE = "s8-rtp-loopback-report.txt" }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A synchronous, PII-free marker distinguishes launch failure from blocked RTP.
        PrivateDiagnosticReportStore.write(
            filesDir, REPORT_FILE,
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
                    val bridge = S8RtpRealtimeBridgeSmokeProbe.run()
                    Report(
                        result.tx(), result.rx(), result.stop(),
                        if (result.allPassed() && bridge.allPassed()) "pass" else "failed",
                        bridgeTx = bridge.tx(),
                        bridgeRx = bridge.rx(),
                        bridgeStop = bridge.stop(),
                        bridgeSdp = bridge.sdp(),
                        publicSdpRejected = bridge.publicEndpointRejected(),
                    )
                }
            } catch (_: Throwable) {
                Report(false, false, false, "failed")
            }
            PrivateDiagnosticReportStore.write(
                filesDir,
                REPORT_FILE,
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
        val bridgeTx: Boolean = false,
        val bridgeRx: Boolean = false,
        val bridgeStop: Boolean = false,
        val bridgeSdp: Boolean = false,
        val publicSdpRejected: Boolean = false,
    ) {
        fun render(): String = buildString {
            appendLine("probe=s8_rtp_loopback")
            appendLine("transport=localhost_only")
            appendLine("pcm_rate_hz=8000")
            appendLine("tx=$tx")
            appendLine("rx=$rx")
            appendLine("stop=$stop")
            appendLine("app_pcm_rate_hz=16000")
            appendLine("realtime_pcm_rate_hz=24000")
            appendLine("bridge_tx=$bridgeTx")
            appendLine("bridge_rx=$bridgeRx")
            appendLine("bridge_stop=$bridgeStop")
            appendLine("bridge_sdp=$bridgeSdp")
            appendLine("public_sdp_rejected=$publicSdpRejected")
            appendLine("status=$status")
            appendLine("real_call=false")
            appendLine("report_complete=true")
        }
    }
}
