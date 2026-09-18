package pl.michalmatu.aicallbridge.realtime

import java.util.ArrayDeque
import java.util.LinkedHashMap

/** Privacy-safe bounded event ledger for one Realtime session. */
class RealtimeEventTrace @JvmOverloads constructor(
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val maxIdentityAliases: Int = DEFAULT_MAX_IDENTITY_ALIASES,
    private val monotonicNs: () -> Long = System::nanoTime,
) {
    private val lock = Any()
    private val startedNs = monotonicNs()
    private val events = ArrayDeque<RealtimeTraceEvent>()
    private val responseAliases = LinkedHashMap<String, String>()
    private val itemAliases = LinkedHashMap<String, String>()
    private val callAliases = LinkedHashMap<String, String>()
    private var nextSequence = 1L

    init {
        require(maxEvents > 0) { "maxEvents must be > 0" }
        require(maxIdentityAliases > 0) { "maxIdentityAliases must be > 0" }
    }

    fun record(
        type: RealtimeTraceEventType,
        partId: RealtimeOutputPartId? = null,
        responseId: String? = null,
        callId: String? = null,
        functionName: String? = null,
        status: RealtimeResponseStatus? = null,
        byteCount: Int? = null,
        charCount: Int? = null,
        errorType: String? = null,
    ) {
        if (byteCount != null) require(byteCount >= 0) { "byteCount must be >= 0" }
        if (charCount != null) require(charCount >= 0) { "charCount must be >= 0" }
        synchronized(lock) {
            val resolvedResponseId = partId?.responseId ?: responseId
            val event = RealtimeTraceEvent(
                sequence = nextSequence++,
                elapsedMs = ((monotonicNs() - startedNs).coerceAtLeast(0L)) / 1_000_000L,
                type = type,
                responseAlias = alias(responseAliases, resolvedResponseId, "R"),
                itemAlias = alias(itemAliases, partId?.itemId, "I"),
                outputIndex = partId?.outputIndex,
                contentIndex = partId?.contentIndex,
                callAlias = alias(callAliases, callId, "C"),
                functionName = functionName,
                status = status,
                byteCount = byteCount,
                charCount = charCount,
                errorType = errorType,
            )
            if (events.size == maxEvents) events.removeFirst()
            events.addLast(event)
        }
    }

    fun snapshot(): List<RealtimeTraceEvent> = synchronized(lock) { events.toList() }

    fun renderCompact(): String = snapshot().joinToString(";") { it.renderCompact() }

    private fun alias(
        aliases: LinkedHashMap<String, String>,
        raw: String?,
        prefix: String,
    ): String? {
        if (raw == null) return null
        aliases[raw]?.let { return it }
        if (aliases.size >= maxIdentityAliases) return "$prefix?"
        return "$prefix${aliases.size + 1}".also { aliases[raw] = it }
    }

    companion object {
        const val DEFAULT_MAX_EVENTS = 256
        const val DEFAULT_MAX_IDENTITY_ALIASES = 512
    }
}

enum class RealtimeTraceEventType {
    CONNECT_START,
    CONNECT_SUCCESS,
    CONNECT_FAILURE,
    CANCEL_RESPONSE,
    FUNCTION_OUTPUT,
    OUTPUT_AUDIO_UNIDENTIFIED,
    OUTPUT_AUDIO,
    OUTPUT_TRANSCRIPT_DELTA,
    OUTPUT_TRANSCRIPT_DONE,
    OUTPUT_AUDIO_DONE,
    RESPONSE_DONE,
    REMOTE_SPEECH_STARTED,
    REMOTE_SPEECH_STOPPED,
    FUNCTION_CALL,
    ERROR,
    CLOSED,
}

data class RealtimeTraceEvent(
    val sequence: Long,
    val elapsedMs: Long,
    val type: RealtimeTraceEventType,
    val responseAlias: String? = null,
    val itemAlias: String? = null,
    val outputIndex: Int? = null,
    val contentIndex: Int? = null,
    val callAlias: String? = null,
    val functionName: String? = null,
    val status: RealtimeResponseStatus? = null,
    val byteCount: Int? = null,
    val charCount: Int? = null,
    val errorType: String? = null,
) {
    fun renderCompact(): String = buildString {
        append(sequence).append('@').append(elapsedMs).append(':').append(type.name)
        responseAlias?.let { append(" r=").append(it) }
        itemAlias?.let { append(" i=").append(it) }
        outputIndex?.let { append(" o=").append(it) }
        contentIndex?.let { append(" c=").append(it) }
        callAlias?.let { append(" call=").append(it) }
        functionName?.let { append(" fn=").append(it) }
        status?.let { append(" status=").append(it.name) }
        byteCount?.let { append(" bytes=").append(it) }
        charCount?.let { append(" chars=").append(it) }
        errorType?.let { append(" error=").append(it) }
    }

    override fun toString(): String = renderCompact()
}
