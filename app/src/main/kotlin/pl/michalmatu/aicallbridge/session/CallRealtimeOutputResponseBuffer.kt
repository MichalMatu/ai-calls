package pl.michalmatu.aicallbridge.session

import java.util.LinkedHashMap
import java.util.LinkedHashSet
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeOutputPartId
import pl.michalmatu.aicallbridge.realtime.RealtimePcmFrameAdapter

enum class CallRealtimeOutputDecision {
    RELEASE,
    DROP,
}

fun interface CallRealtimeOutputApprovalPolicy {
    fun evaluate(partId: RealtimeOutputPartId, transcript: String): CallRealtimeOutputDecision
}

data class CallRealtimeBufferedOutput(
    val partId: RealtimeOutputPartId,
    val transcript: String,
    val frames: List<PcmFrame>,
)

sealed interface CallRealtimeOutputBufferResult {
    data object Pending : CallRealtimeOutputBufferResult

    data class Released(
        val output: CallRealtimeBufferedOutput,
    ) : CallRealtimeOutputBufferResult

    data class Dropped(
        val partId: RealtimeOutputPartId,
        val transcript: String,
    ) : CallRealtimeOutputBufferResult
}

data class CallRealtimeOutputResponseBufferSnapshot(
    val pendingParts: Int,
    val bufferedAudioBytes: Long,
)

/**
 * Bounded correlation buffer for one Realtime generation.
 *
 * Identified audio is never released until both the audio stream and its final transcript are
 * complete. The approval policy therefore sees the complete transcript before any buffered PCM is
 * made available to the caller. This is a safety interception point, not a claim that transcript
 * text is a cryptographic representation of the generated audio.
 */
class CallRealtimeOutputResponseBuffer(
    private val approvalPolicy: CallRealtimeOutputApprovalPolicy,
    private val maxBufferedAudioBytes: Long = DEFAULT_MAX_BUFFERED_AUDIO_BYTES,
    private val maxPendingParts: Int = DEFAULT_MAX_PENDING_PARTS,
    private val maxTranscriptChars: Int = DEFAULT_MAX_TRANSCRIPT_CHARS,
) {
    private val pending = LinkedHashMap<RealtimeOutputPartId, Part>()
    private val completed = LinkedHashSet<RealtimeOutputPartId>()
    private var bufferedAudioBytes = 0L

    init {
        require(maxBufferedAudioBytes > 0L) { "maxBufferedAudioBytes must be > 0" }
        require(maxPendingParts > 0) { "maxPendingParts must be > 0" }
        require(maxTranscriptChars > 0) { "maxTranscriptChars must be > 0" }
    }

    @Synchronized
    fun onAudio(partId: RealtimeOutputPartId, frame: PcmFrame): CallRealtimeOutputBufferResult {
        val part = partFor(partId)
        check(!part.audioDone) { "Realtime audio arrived after output_audio.done" }
        validateRealtimeAudio(frame)

        val data = frame.data
        val nextBytes = bufferedAudioBytes + data.size
        check(nextBytes <= maxBufferedAudioBytes) {
            "Realtime gated audio exceeded bounded response buffer"
        }

        val copied = PcmFrame(
            format = frame.format,
            data = data.copyOf(),
            monotonicTimestampNs = frame.monotonicTimestampNs,
        )
        part.frames += copied
        part.audioBytes += copied.data.size
        bufferedAudioBytes = nextBytes
        return completeIfReady(partId, part)
    }

    @Synchronized
    fun onTranscriptDelta(
        partId: RealtimeOutputPartId,
        delta: String,
    ): CallRealtimeOutputBufferResult {
        val part = partFor(partId)
        check(part.finalTranscript == null) {
            "Realtime transcript delta arrived after transcript.done"
        }
        val nextChars = part.transcriptDeltas.length + delta.length
        check(nextChars <= maxTranscriptChars) {
            "Realtime output transcript exceeded bounded character limit"
        }
        part.transcriptDeltas.append(delta)
        return CallRealtimeOutputBufferResult.Pending
    }

    @Synchronized
    fun onTranscriptDone(
        partId: RealtimeOutputPartId,
        transcript: String,
    ): CallRealtimeOutputBufferResult {
        val part = partFor(partId)
        check(part.finalTranscript == null) { "duplicate Realtime output transcript.done" }
        require(transcript.isNotBlank()) { "Realtime output transcript must not be blank" }
        check(transcript.length <= maxTranscriptChars) {
            "Realtime output transcript exceeded bounded character limit"
        }
        if (part.transcriptDeltas.isNotEmpty()) {
            check(part.transcriptDeltas.toString() == transcript) {
                "Realtime final transcript does not match accumulated transcript deltas"
            }
        }
        part.finalTranscript = transcript
        return completeIfReady(partId, part)
    }

    @Synchronized
    fun onAudioDone(partId: RealtimeOutputPartId): CallRealtimeOutputBufferResult {
        val part = partFor(partId)
        check(!part.audioDone) { "duplicate Realtime output_audio.done" }
        part.audioDone = true
        return completeIfReady(partId, part)
    }

    @Synchronized
    fun snapshot(): CallRealtimeOutputResponseBufferSnapshot =
        CallRealtimeOutputResponseBufferSnapshot(
            pendingParts = pending.size,
            bufferedAudioBytes = bufferedAudioBytes,
        )

    @Synchronized
    fun clear() {
        pending.clear()
        completed.clear()
        bufferedAudioBytes = 0L
    }

    private fun partFor(partId: RealtimeOutputPartId): Part {
        check(partId !in completed) { "late Realtime event arrived for a finalized output part" }
        val existing = pending[partId]
        if (existing != null) return existing
        check(pending.size < maxPendingParts) {
            "Realtime output exceeded bounded pending-part limit"
        }
        return Part().also { pending[partId] = it }
    }

    private fun completeIfReady(
        partId: RealtimeOutputPartId,
        part: Part,
    ): CallRealtimeOutputBufferResult {
        val transcript = part.finalTranscript ?: return CallRealtimeOutputBufferResult.Pending
        if (!part.audioDone) return CallRealtimeOutputBufferResult.Pending

        val frames = part.frames.toList()
        removeFinalized(partId, part)
        check(frames.isNotEmpty()) { "Realtime output completed without buffered audio" }

        return when (approvalPolicy.evaluate(partId, transcript)) {
            CallRealtimeOutputDecision.RELEASE -> CallRealtimeOutputBufferResult.Released(
                CallRealtimeBufferedOutput(partId, transcript, frames),
            )
            CallRealtimeOutputDecision.DROP -> CallRealtimeOutputBufferResult.Dropped(
                partId,
                transcript,
            )
        }
    }

    private fun removeFinalized(partId: RealtimeOutputPartId, part: Part) {
        val removed = pending.remove(partId)
        check(removed === part) { "Realtime output buffer state changed unexpectedly" }
        bufferedAudioBytes -= part.audioBytes
        check(bufferedAudioBytes >= 0L) { "Realtime output buffer byte accounting underflow" }
        completed += partId
        while (completed.size > MAX_COMPLETED_TOMBSTONES) {
            val oldest = completed.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
    }

    private fun validateRealtimeAudio(frame: PcmFrame) {
        val format = frame.format
        require(format.sampleRateHz == RealtimePcmFrameAdapter.REALTIME_SAMPLE_RATE_HZ) {
            "Realtime gated output must be 24000 Hz"
        }
        require(format.channels == 1 && format.bitsPerSample == 16) {
            "Realtime gated output must be mono PCM16LE"
        }
        require((frame.data.size and 1) == 0) {
            "Realtime gated output must contain whole PCM16 samples"
        }
    }

    private class Part {
        val frames = mutableListOf<PcmFrame>()
        val transcriptDeltas = StringBuilder()
        var finalTranscript: String? = null
        var audioDone = false
        var audioBytes = 0L
    }

    private companion object {
        const val DEFAULT_MAX_RESPONSE_SECONDS = 12
        const val BYTES_PER_PCM16_SAMPLE = 2
        const val DEFAULT_MAX_BUFFERED_AUDIO_BYTES =
            RealtimePcmFrameAdapter.REALTIME_SAMPLE_RATE_HZ.toLong() *
                BYTES_PER_PCM16_SAMPLE * DEFAULT_MAX_RESPONSE_SECONDS
        const val DEFAULT_MAX_PENDING_PARTS = 4
        const val DEFAULT_MAX_TRANSCRIPT_CHARS = 16_384
        const val MAX_COMPLETED_TOMBSTONES = 64
    }
}
