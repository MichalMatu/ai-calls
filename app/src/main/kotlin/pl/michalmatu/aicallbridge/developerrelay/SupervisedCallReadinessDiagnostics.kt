package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.telecom.TelecomManager
import pl.michalmatu.aicallbridge.AndroidLiveCallReadiness

/**
 * Read-only, whitelisted readiness report. Never includes call content or contacts.
 * Only the approved GSM Binder host can authorize media access.
 */
internal data class SupervisedCallReadinessSnapshot(
    val microphonePermission: Boolean,
    val phoneStatePermission: Boolean,
    val gsmShellReady: Boolean,
    val activeCellularCall: Boolean,
    val managedCall: Boolean,
    val audioMode: Int?,
    val s8Capabilities: S8AndroidAudioCapabilitySnapshot? = null,
) {
    val failure: SupervisedCallPilotFailure?
        get() = SupervisedCallPilotPolicy.check(
            microphonePermission,
            phoneStatePermission,
            gsmShellReady,
            activeCellularCall,
            audioMode == AudioManager.MODE_IN_CALL,
        )

    val audioModeLabel: String
        get() = when (audioMode) {
            AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
            AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
            AudioManager.MODE_NORMAL -> "MODE_NORMAL"
            AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
            null -> "UNAVAILABLE"
            else -> "OTHER_${audioMode}"
        }

    fun renderReport(): String = buildString {
        appendLine("probe=supervised_call_readiness")
        appendLine("microphone_permission=$microphonePermission")
        appendLine("phone_state_permission=$phoneStatePermission")
        appendLine("gsm_shell_ready=$gsmShellReady")
        appendLine("telecom_in_call=$activeCellularCall")
        appendLine("telecom_managed_call=$managedCall")
        appendLine("audio_mode=$audioModeLabel")
        appendLine("failure_reason=${failure?.name?.lowercase() ?: "none"}")
        s8Capabilities?.let { append(it.renderReport()) }
        appendLine("report_complete=true")
    }
}

internal object SupervisedCallReadinessDiagnostics {
    const val REPORT_FILE = "supervised-call-readiness-report.txt"

    fun capture(context: Context): SupervisedCallReadinessSnapshot {
        val mic = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val phone = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val gsm = runCatching { AndroidLiveCallReadiness.evaluate(context).ready }
            .getOrDefault(false)
        val telecom = context.getSystemService(TelecomManager::class.java)
        val active = if (phone) runCatching { telecom?.isInCall == true }.getOrDefault(false)
            else false
        val managed = if (phone) runCatching { telecom?.isInManagedCall == true }.getOrDefault(false)
            else false
        val mode = runCatching { context.getSystemService(AudioManager::class.java)?.mode }
            .getOrNull()
        return SupervisedCallReadinessSnapshot(
            mic, phone, gsm, active, managed, mode,
            S8AndroidAudioCapabilityProbe.capture(context),
        )
    }

    fun write(context: Context, snapshot: SupervisedCallReadinessSnapshot): Boolean =
        PrivateDiagnosticReportStore.write(context.filesDir, REPORT_FILE, snapshot.renderReport())
}
