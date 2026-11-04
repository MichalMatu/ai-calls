package pl.michalmatu.aicallbridge.developerrelay

/**
 * Private, process-local live view of ONE call. Never persisted or exposed through
 * broadcasts, intents, notifications, logs, GitHub or Android savedInstanceState.
 *
 * The feed is read-only: observers cannot issue commands to the call engine.
 * Opt-in call evidence recording is an independent feature.
 */
internal object LiveCallChatFeed {
    internal enum class Speaker { OFFICE, GEMMA }

    internal data class Line(
        val turn: Int,
        val speaker: Speaker,
        val text: String,
        val txStatus: String? = null,
    )

    internal data class Snapshot(
        val revision: Long,
        val active: Boolean,
        val status: String,
        val lines: List<Line>,
    )

    private val lock = Any()
    private val lines = mutableListOf<Line>()
    private var revision = 0L
    private var generation = 0L
    private var active = false
    private var status = "Brak aktywnej rozmowy"

    fun begin(): Long = synchronized(lock) {
        generation += 1
        lines.clear()
        active = true
        status = "Łączenie z rozmówcą…"
        revision += 1
        generation
    }

    fun stage(token: Long, value: String) = synchronized(lock) {
        if (active && token == generation && status != value) {
            status = value
            revision += 1
        }
    }

    fun heard(token: Long, turn: Int, text: String) {
        add(token, turn, Speaker.OFFICE, text)
    }

    fun replied(token: Long, turn: Int, text: String) {
        add(token, turn, Speaker.GEMMA, text)
    }

    private fun add(token: Long, turn: Int, speaker: Speaker, text: String) = synchronized(lock) {
        if (!active || token != generation || turn < 1 || text.isBlank()) return@synchronized
        // Display the exact finalized text, without serializing it beyond this process.
        lines.add(Line(turn, speaker, text))
        revision += 1
    }

    fun tx(token: Long, turn: Int, complete: Boolean) = synchronized(lock) {
        if (!active || token != generation) return@synchronized
        val index = lines.indexOfLast { it.turn == turn && it.speaker == Speaker.GEMMA }
        if (index < 0) return@synchronized
        lines[index] = lines[index].copy(
            txStatus = if (complete) "TX: wysłano lokalnie" else "TX: niepełne",
        )
        revision += 1
    }

    fun finish(token: Long, code: String?) = synchronized(lock) {
        if (!active || token != generation) return@synchronized
        active = false
        status = when (code) {
            "owned_call_ended" -> "Rozmowa zakończona"
            "operator_stopped" -> "STOP AI — zakończono"
            null -> "Sesja zakończona"
            else -> "Sesja zakończona / przerwana"
        }
        revision += 1
    }

    /** Local UI operation only; does not cancel recording, a call or a grant. */
    fun clearVisibleHistory() = synchronized(lock) {
        lines.clear()
        if (!active) status = "Brak aktywnej rozmowy"
        revision += 1
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(revision, active, status, lines.toList())
    }
}
