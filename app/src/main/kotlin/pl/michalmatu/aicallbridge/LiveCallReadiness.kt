package pl.michalmatu.aicallbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import pl.michalmatu.aicallbridge.session.CallMediaBackendMode
import pl.michalmatu.aicallbridge.session.GsmMediaTransportReadiness
import rikka.shizuku.Shizuku

internal data class LiveCallReadinessInputs(
    val recordAudioGranted: Boolean,
    val shizukuBinderAvailable: Boolean,
    val shizukuSupported: Boolean,
    val shizukuPermissionGranted: Boolean,
    val mode: CallMediaBackendMode = CallMediaBackendMode.SHIZUKU_LEGACY,
    val shellHostAvailable: Boolean = false,
)

internal enum class LiveCallReadinessFailure(val reportValue: String) {
    RECORD_AUDIO_PERMISSION_REQUIRED("record_audio_permission_required"),
    SHIZUKU_BINDER_UNAVAILABLE("shizuku_binder_unavailable"),
    SHIZUKU_UNSUPPORTED("shizuku_unsupported"),
    SHIZUKU_PERMISSION_REQUIRED("shizuku_permission_required"),
    GSM_SHELL_HOST_UNAVAILABLE("gsm_shell_host_unavailable"),
}

internal data class LiveCallReadinessResult(
    val inputs: LiveCallReadinessInputs,
    val failure: LiveCallReadinessFailure?,
) {
    val ready: Boolean
        get() = failure == null

    fun renderReport(): String = buildString {
        appendLine("probe=live_call_readiness")
        appendLine("record_audio_granted=${inputs.recordAudioGranted}")
        if (inputs.mode == CallMediaBackendMode.GSM_SHELL_ADB) {
            appendLine("media_transport=gsm_shell_adb")
            appendLine("gsm_shell_host_available=${inputs.shellHostAvailable}")
        } else {
            appendLine("shizuku_binder_available=${inputs.shizukuBinderAvailable}")
            appendLine("shizuku_supported=${inputs.shizukuSupported}")
            appendLine("shizuku_permission_granted=${inputs.shizukuPermissionGranted}")
        }
        appendLine("live_call_readiness=$ready")
        appendLine("failure_reason=${failure?.reportValue ?: "none"}")
        appendLine("probe_complete=true")
    }
}

/** Pure fail-closed policy used before any developer live-call runner may dial. */
internal object LiveCallReadinessPolicy {
    fun evaluate(inputs: LiveCallReadinessInputs): LiveCallReadinessResult {
        val failure = when {
            !inputs.recordAudioGranted -> LiveCallReadinessFailure.RECORD_AUDIO_PERMISSION_REQUIRED
            inputs.mode == CallMediaBackendMode.GSM_SHELL_ADB &&
                !inputs.shellHostAvailable -> LiveCallReadinessFailure.GSM_SHELL_HOST_UNAVAILABLE
            inputs.mode == CallMediaBackendMode.GSM_SHELL_ADB -> null
            !inputs.shizukuBinderAvailable -> LiveCallReadinessFailure.SHIZUKU_BINDER_UNAVAILABLE
            !inputs.shizukuSupported -> LiveCallReadinessFailure.SHIZUKU_UNSUPPORTED
            !inputs.shizukuPermissionGranted -> LiveCallReadinessFailure.SHIZUKU_PERMISSION_REQUIRED
            else -> null
        }
        return LiveCallReadinessResult(inputs, failure)
    }
}


internal object AndroidLiveCallReadiness {
    fun evaluate(context: Context): LiveCallReadinessResult {
        val recordAudioGranted =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (CallMediaBackendMode.forPackage(context.packageName) == CallMediaBackendMode.GSM_SHELL_ADB) {
            return LiveCallReadinessPolicy.evaluate(
                LiveCallReadinessInputs(
                    recordAudioGranted = recordAudioGranted,
                    shizukuBinderAvailable = false,
                    shizukuSupported = false,
                    shizukuPermissionGranted = false,
                    mode = CallMediaBackendMode.GSM_SHELL_ADB,
                    shellHostAvailable = GsmMediaTransportReadiness.isAvailable(context),
                )
            )
        }
        val shizukuBinderAvailable = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuSupported = shizukuBinderAvailable &&
            runCatching { !Shizuku.isPreV11() }.getOrDefault(false)
        val shizukuPermissionGranted = shizukuSupported &&
            runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)

        return LiveCallReadinessPolicy.evaluate(
            LiveCallReadinessInputs(
                recordAudioGranted = recordAudioGranted,
                shizukuBinderAvailable = shizukuBinderAvailable,
                shizukuSupported = shizukuSupported,
                shizukuPermissionGranted = shizukuPermissionGranted,
            ),
        )
    }
}
