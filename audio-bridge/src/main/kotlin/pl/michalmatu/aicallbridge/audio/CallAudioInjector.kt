package pl.michalmatu.aicallbridge.audio

interface CallAudioInjector {
    val backendName: String

    suspend fun probe(): Result<ProbeResult>

    suspend fun start(): Result<Unit>

    suspend fun write(frame: PcmFrame): Result<Unit>

    suspend fun stop()

    suspend fun abortNow(): Result<Unit>
}
