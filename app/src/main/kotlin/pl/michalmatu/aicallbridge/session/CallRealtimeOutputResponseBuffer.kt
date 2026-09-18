package pl.michalmatu.aicallbridge.session

import java.util.LinkedHashMap
import java.util.LinkedHashSet
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeOutputPartId
import pl.michalmatu.aicallbridge.realtime.RealtimePcmFrameAdapter
import pl.michalmatu.aicallbridge.realtime.RealtimeResponseStatus

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
 * Identified audio is never released until its audio stream and final transcript are complete and
 * the whole Realtime response finishes with status COMPLETED. Cancelled, failed and incomplete
 * responses are discarded without consulting the approval policy. The policy therefore sees only
 * finalized successful responses before any buffered PCM is made available to telephony TX.
 *
 * Transcript deltas are bounded as streaming diagnostics, while transcript.done is authoritative
 * for approval. This is a safety interception point, not a claim that transcript text is a
 * cryptographic representation of the generated audio.
 */
class CallRealtimeOutputResponseBuffer(
    private val approvalPolicy: CallRealtimeOutputApprovalPolicy,
    private val maxBufferedAudioBytes: Long = DEFAULT_MAX_BUFFERED_AUDIO_BYTES,
    private val maxPendingParts: Int = DEFAULT_MAX_PENDING_PARTS,
    private val maxTranscriptChars: Int = DEFAULT_MAX_TRANSCRIPT_CHARS,
) {
    private val pending = LinkedHashMap<RealtimeOutputPartId, Part>()
    private val completedParts = LinkedHashSet<RealtimeOutputPartId>()
    private val finalizedResponses = LinkedHashSet<String>()
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
        return CallRealtimeOutputBufferResult.Pending
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
        val nextChars = part.transcriptDeltaChars + delta.length
        check(nextChars <= maxTranscriptChars) {
            "Realtime output transcript exceeded bounded character limit"
        }
        part.transcriptDeltaChars = nextChars
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
        part.finalTranscript = transcript
        return CallRealtimeOutputBufferResult.Pending
    }

    @Synchronized
    fun onAudioDone(partId: RealtimeOutputPartId): CallRealtimeOutputBufferResult {
        val part = partFor(partId)
        check(!part.audioDone) { "duplicate Realtime output_audio.done" }
        part.audioDone = true
        return CallRealtimeOutputBufferResult.Pending
    }

    /**
     * Finalizes all buffered audio parts belonging to one Realtime response.
     *
     * A successful response is evaluated atomically: every part must be complete before any policy
     * result is returned. Non-success terminal statuses discard their buffered parts. UNKNOWN is a
     * protocol-safety failure and is intentionally not interpreted as success.
     */
    @Synchronized
    fun onResponseDone(
        responseId: String,
        status: RealtimeResponseStatus,
    ): List<CallRealtimeOutputBufferResult> {
        require(responseId.isNotBlank()) { "Realtime response id must not be blank" }
        check(responseId !in finalizedResponses) { "duplicate Realtime response.done" }

        val responseParts = pending.entries
            .filter { (partId, _) -> partId.responseId == responseId }

        return when (status) {
            RealtimeResponseStatus.COMPLETED -> finalizeCompletedResponse(responseId, responseParts)
            RealtimeResponseStatus.CANCELLED,
            RealtimeResponseStatus.FAILED,
            RealtimeResponseStatus.INCOMPLETE,
            -> {
                responseParts.forEach { (partId, part) -> removePart(partId, part) }
                finalizeResponse(responseId)
                emptyList()
            }
            RealtimeResponseStatus.UNKNOWN ->
                throw IllegalStateException("Realtime response.done has unknown terminal status")
        }
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
        completedParts.clear()
        finalizedResponses.clear()
        bufferedAudioBytes = 0L
    }

    private fun finalizeCompletedResponse(
        responseId: String,
        responseParts: List<Map.Entry<RealtimeOutputPartId, Part>>,
    ): List<CallRealtimeOutputBufferResult> {
        if (responseParts.isEmpty()) {
            finalizeResponse(responseId)
            return emptyList()
        }

        responseParts.forEach { (_, part) ->
            check(part.audioDone) { "completed Realtime response has unfinished audio output" }
            check(part.finalTranscript != null) {
                "completed Realtime response has unfinished output transcript"
            }
            check(part.frames.isNotEmpty()) { "completed Realtime audio output has no buffered PCM" }
        }

        // Evaluate every complete part before removing any state or returning any releasable PCM.
        val decisions = responseParts.map { (partId, part) ->
            val transcript = checkNotNull(part.finalTranscript)
            Triple(partId, part, approvalPolicy.evaluate(partId, transcript))
        }

        val results = decisions.map { (partId, part, decision) ->
            val transcript = checkNotNull(part.finalTranscript)
            when (decision) {
                CallRealtimeOutputDecision.RELEASE -> CallRealtimeOutputBufferResult.Released(
                    CallRealtimeBufferedOutput(partId, transcript, part.frames.toList()),
                )
                CallRealtimeOutputDecision.DROP -> CallRealtimeOutputBufferResult.Dropped(
                    partId,
                    transcript,
                )
            }
        }

        responseParts.forEach { (partId, part) -> removePart(partId, part) }
        finalizeResponse(responseId)
        return results
    }

    private fun partFor(partId: RealtimeOutputPartId): Part {
        check(partId.responseId !in finalizedResponses) {
            "late Realtime event arrived for a finalized response"
        }
        check(partId !in completedParts) { "late Realtime event arrived for a finalized output part" }
        val existing = pending[partId]
        if (existing != null) return existing
        check(pending.size < maxPendingParts) {
            "Realtime output exceeded bounded pending-part limit"
        }
        return Part().also { pending[partId] = it }
    }

    private fun removePart(partId: RealtimeOutputPartId, part: Part) {
        val removed = pending.remove(partId)
        check(removed === part) { "Realtime output buffer state changed unexpectedly" }
        bufferedAudioBytes -= part.audioBytes
        check(bufferedAudioBytes >= 0L) { "Realtime output buffer byte accounting underflow" }
        completedParts += partId
        trimSet(completedParts, MAX_COMPLETED_TOMBSTONES)
    }

    private fun finalizeResponse(responseId: String) {
        finalizedResponses += responseId
        trimSet(finalizedResponses, MAX_FINALIZED_RESPONSE_TOMBSTONES)
    }

    private fun <T> trimSet(values: LinkedHashSet<T>, maximum: Int) {
        while (values.size > maximum) {
            val iterator = values.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
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
        var transcriptDeltaChars = 0
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
        const val MAX_FINALIZED_RESPONSE_TOMBSTONES = 64
    }
}
