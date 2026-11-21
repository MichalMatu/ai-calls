package pl.michalmatu.aicallbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import pl.michalmatu.aicallbridge.session.GsmMediaTransportReadiness

/** Single shell-Binder readiness policy; never falls back to another transport. */
internal data class LiveCallReadinessInputs(
    val recordAudioGranted: Boolean,
    val shellHostAvailable: Boolean,
)

internal enum class LiveCallReadinessFailure(val reportValue: String) {
    RECORD_AUDIO_PERMISSION_REQUIRED("record_audio_permission_required"),
    GSM_SHELL_HOST_UNAVAILABLE("gsm_shell_host_unavailable"),
}

internal data class LiveCallReadinessResult(
    val inputs: LiveCallReadinessInputs,
    val failure: LiveCallReadinessFailure?,
) {
    val ready: Boolean get() = failure == null

    fun renderReport(): String = buildString {
        appendLine("probe=live_call_readiness")
        appendLine("record_audio_granted=${inputs.recordAudioGranted}")
        appendLine("media_transport=gsm_shell_adb")
        appendLine("gsm_shell_host_available=${inputs.shellHostAvailable}")
        appendLine("live_call_readiness=$ready")
        appendLine("failure_reason=${failure?.reportValue ?: "none"}")
        appendLine("probe_complete=true")
    }
}

internal object LiveCallReadinessPolicy {
    fun evaluate(inputs: LiveCallReadinessInputs): LiveCallReadinessResult =
        LiveCallReadinessResult(inputs, when {
            !inputs.recordAudioGranted -> LiveCallReadinessFailure.RECORD_AUDIO_PERMISSION_REQUIRED
            !inputs.shellHostAvailable -> LiveCallReadinessFailure.GSM_SHELL_HOST_UNAVAILABLE
            else -> null
        })
}

internal object AndroidLiveCallReadiness {
    fun evaluate(context: Context): LiveCallReadinessResult =
        LiveCallReadinessPolicy.evaluate(
            LiveCallReadinessInputs(
                recordAudioGranted =
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED,
                shellHostAvailable = GsmMediaTransportReadiness.isAvailable(context),
            )
        )
}
