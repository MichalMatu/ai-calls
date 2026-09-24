package pl.michalmatu.aicallbridge

internal data class LiveCallReadinessInputs(
    val recordAudioGranted: Boolean,
    val shizukuBinderAvailable: Boolean,
    val shizukuSupported: Boolean,
    val shizukuPermissionGranted: Boolean,
)

internal enum class LiveCallReadinessFailure(val reportValue: String) {
    RECORD_AUDIO_PERMISSION_REQUIRED("record_audio_permission_required"),
    SHIZUKU_BINDER_UNAVAILABLE("shizuku_binder_unavailable"),
    SHIZUKU_UNSUPPORTED("shizuku_unsupported"),
    SHIZUKU_PERMISSION_REQUIRED("shizuku_permission_required"),
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
        appendLine("shizuku_binder_available=${inputs.shizukuBinderAvailable}")
        appendLine("shizuku_supported=${inputs.shizukuSupported}")
        appendLine("shizuku_permission_granted=${inputs.shizukuPermissionGranted}")
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
            !inputs.shizukuBinderAvailable -> LiveCallReadinessFailure.SHIZUKU_BINDER_UNAVAILABLE
            !inputs.shizukuSupported -> LiveCallReadinessFailure.SHIZUKU_UNSUPPORTED
            !inputs.shizukuPermissionGranted -> LiveCallReadinessFailure.SHIZUKU_PERMISSION_REQUIRED
            else -> null
        }
        return LiveCallReadinessResult(inputs, failure)
    }
}
