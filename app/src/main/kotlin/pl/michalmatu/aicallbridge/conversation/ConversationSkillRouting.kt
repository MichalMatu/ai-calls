package pl.michalmatu.aicallbridge.conversation

import java.util.Locale

/**
 * Lab experiment only. This chooses version-pinned prompt instructions at the
 * start of an offline session; it does not select tools or confer authority.
 * In-session, per-turn routing is intentionally NOT claimed by this contract.
 */
internal enum class SkillLoadingMode { ALL_ACTIVE, CONTEXTUAL }

internal data class SkillRoutingDecision(
    val snapshot: ConversationSkillSnapshot,
    val mode: SkillLoadingMode,
    val availableCount: Int,
    val selectedIds: List<String>,
    val selectorMicros: Long,
)

/**
 * Fast, deterministic, zero-model-call baseline. Unknown/custom skills are
 * retained rather than silently discarded until editable routing metadata is
 * introduced. Disabled revisions can never be reactivated by this selector.
 */
internal object ConversationSkillRouter {
    fun route(
        snapshot: ConversationSkillSnapshot,
        mode: SkillLoadingMode,
        direction: ConversationDirection,
        goal: String,
        opening: String = "",
    ): SkillRoutingDecision {
        val started = System.nanoTime()
        val context = (goal + " " + opening).lowercase(Locale.ROOT)
        val messageRelevant = direction == ConversationDirection.INCOMING ||
            listOf("wiadomo", "notatk", "przekaż", "przekaz", "zapisz", "kontakt")
                .any { context.contains(it) }
        val clarifyRelevant = direction == ConversationDirection.INCOMING ||
            listOf("korekt", "popraw", "niejasn", "doprecyz", "wyjaś",
                "wyjas", "dopyt", "brakuj", "pytan")
                .any { context.contains(it) }
        val filtered = if (mode == SkillLoadingMode.ALL_ACTIVE) snapshot.entries else
            snapshot.entries.filter { entry ->
                when (entry.id) {
                    "MessageCapture" -> messageRelevant
                    "Clarification" -> clarifyRelevant
                    else -> true // style, closing, and future user-defined skills
                }
            }
        return SkillRoutingDecision(
            snapshot = if (mode == SkillLoadingMode.ALL_ACTIVE) snapshot
                else snapshot.copy(entries = filtered),
            mode = mode,
            availableCount = snapshot.entries.size,
            selectedIds = filtered.map { it.id },
            selectorMicros = ((System.nanoTime() - started).coerceAtLeast(0L) / 1_000L),
        )
    }
}
