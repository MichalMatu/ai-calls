package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.telecom.TelecomManager
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import pl.michalmatu.aicallbridge.AndroidLiveCallReadiness

/**
 * Single, ephemeral observation of the supervised call gate. It owns no audio,
 * telephony action, consent or call identity; it only provides safe diagnostics.
 */
internal data class SupervisedCallReadinessSnapshot(
    val microphonePermission: Boolean,
    val phoneStatePermission: Boolean,
    val shizukuReady: Boolean,
    val activeCellularCall: Boolean,
    val managedCall: Boolean,
    val audioMode: Int?,
) {
    val failure: SupervisedCallPilotFailure?
        get() = SupervisedCallPilotPolicy.check(
            microphonePermission,
            phoneStatePermission,
            shizukuReady,
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

    /** Whitelisted fields only. Never include a number, contact, transcript or PCM. */
    fun renderReport(): String = buildString {
        appendLine("probe=supervised_call_readiness")
        appendLine("microphone_permission=$microphonePermission")
        appendLine("phone_state_permission=$phoneStatePermission")
        appendLine("shizuku_ready=$shizukuReady")
        appendLine("telecom_in_call=$activeCellularCall")
        appendLine("telecom_managed_call=$managedCall")
        appendLine("audio_mode=$audioModeLabel")
        appendLine("failure_reason=${failure?.name?.lowercase() ?: "none"}")
        appendLine("report_complete=true")
    }
}

internal object SupervisedCallReadinessDiagnostics {
    const val REPORT_FILE = "supervised-call-readiness-report.txt"

    /** Read the Android signals once per probe so the failure and report agree. */
    fun capture(context: Context): SupervisedCallReadinessSnapshot {
        val mic = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val phone = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val shizuku = runCatching { AndroidLiveCallReadiness.evaluate(context).ready }
            .getOrDefault(false)
        val telecom = context.getSystemService(TelecomManager::class.java)
        val active = if (phone) {
            runCatching { telecom?.isInCall == true }.getOrDefault(false)
        } else false
        val managed = if (phone) {
            runCatching { telecom?.isInManagedCall == true }.getOrDefault(false)
        } else false
        val mode = runCatching { context.getSystemService(AudioManager::class.java)?.mode }
            .getOrNull()
        return SupervisedCallReadinessSnapshot(mic, phone, shizuku, active, managed, mode)
    }

    /** Atomic replace: a collector never reads a half-written readiness report. */
    fun write(context: Context, snapshot: SupervisedCallReadinessSnapshot): Boolean {
        val file = AtomicFile(File(context.filesDir, REPORT_FILE))
        var stream: FileOutputStream? = null
        return try {
            val output = file.startWrite()
            stream = output
            output.write(snapshot.renderReport().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
            true
        } catch (_: Throwable) {
            stream?.let { file.failWrite(it) }
            false
        }
    }
}
