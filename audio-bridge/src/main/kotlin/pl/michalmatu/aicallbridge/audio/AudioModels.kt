package pl.michalmatu.aicallbridge.audio

data class PcmFormat(
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
)

data class PcmFrame(
    val format: PcmFormat,
    val data: ByteArray,
    val monotonicTimestampNs: Long,
)

data class AudioBridgeMetrics(
    val framesCaptured: Long = 0,
    val framesInjected: Long = 0,
    val captureOverruns: Long = 0,
    val injectionUnderruns: Long = 0,
    val estimatedPipelineLatencyMs: Double? = null,
)
