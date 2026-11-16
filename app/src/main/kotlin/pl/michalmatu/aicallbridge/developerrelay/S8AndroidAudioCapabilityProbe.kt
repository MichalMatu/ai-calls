package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * S8 read-only observation. These public OS signals are not proof of GSM PCM
 * downlink capture, uplink injection or STOP. No test call is placed.
 */
internal data class S8AndroidAudioCapabilitySnapshot(
    val dialerRoleHeld: Boolean,
    val microphoneGranted: Boolean,
    val phoneStateGranted: Boolean,
    val privilegedCaptureGranted: Boolean,
) {
    fun renderReport(): String = buildString {
        appendLine("s8_dialer_role_held=$dialerRoleHeld")
        appendLine("s8_record_audio_granted=$microphoneGranted")
        appendLine("s8_read_phone_state_granted=$phoneStateGranted")
        appendLine("s8_capture_audio_output_granted=$privilegedCaptureGranted")
        appendLine("s8_public_cellular_rx=not_available")
        appendLine("s8_public_cellular_tx=not_available")
        appendLine("s8_cellular_pcm_verified=false")
    }
}

internal object S8AndroidAudioCapabilityProbe {
    fun capture(context: Context): S8AndroidAudioCapabilitySnapshot {
        val role = runCatching {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        }.getOrDefault(false)
        fun granted(permission: String): Boolean =
            runCatching {
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)

        return S8AndroidAudioCapabilitySnapshot(
            dialerRoleHeld = role,
            microphoneGranted = granted(Manifest.permission.RECORD_AUDIO),
            phoneStateGranted = granted(Manifest.permission.READ_PHONE_STATE),
            privilegedCaptureGranted = granted("android.permission.CAPTURE_AUDIO_OUTPUT"),
        )
    }
}
