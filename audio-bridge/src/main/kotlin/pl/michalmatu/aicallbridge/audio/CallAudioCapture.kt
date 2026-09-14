package pl.michalmatu.aicallbridge.audio

interface CallAudioCapture {
    val backendName: String

    suspend fun probe(): Result<ProbeResult>

    suspend fun start(
        onFrame: (PcmFrame) -> Unit,
    ): Result<Unit>

    suspend fun stop()
}

data class ProbeResult(
    val available: Boolean,
    val requiresPrivilegedAccess: Boolean,
    val notes: String,
)
