package pl.michalmatu.aicallbridge.conversation

import java.util.Locale

/**
 * G4.1 Lab contract: proposes Skills for the next model turn from a pinned
 * session snapshot. It NEVER mutates an Engine, emits a tool call, changes
 * Android Call state, or grants access to data.
 *
 * A changed proposal is deliberately not an applied prompt. The LiteRT-LM
 * adapter must separately prove a safe prompt-refresh/rebuild mechanism
 * before anyone wires this to live native generation.
 */
internal data class SkillTurnRoutingDecision(
    val turn: Int,
    val snapshot: ConversationSkillSnapshot,
    val selectedIds: List<String>,
    val activatedIds: List<String>,
    val deactivatedIds: List<String>,
    val requiresPromptRefresh: Boolean,
)

/** Stateful intent tracker, scoped to a single off-call Lab session. */
internal class ConversationSkillTurnRouter(
    fullSnapshot: ConversationSkillSnapshot,
    private val mode: SkillLoadingMode,
    direction: ConversationDirection,
    goal: String,
    opening: String = "",
) {
    private val pinned = fullSnapshot.copy(entries = fullSnapshot.entries.toList())
    private val initial = ConversationSkillRouter.route(pinned, mode, direction, goal, opening)
    private var selected = initial.selectedIds
    private var messageActive = initial.snapshot.entries.any {
        it.capability == SkillCapability.MESSAGE_CAPTURE
    }
    private var clarificationActive = initial.snapshot.entries.any {
        it.capability == SkillCapability.CLARIFICATION
    }
    private var turn = 0

    val initialSnapshot: ConversationSkillSnapshot get() = initial.snapshot
    val initialSelectedIds: List<String> get() = initial.selectedIds.toList()

    /**
     * Consumes only a user utterance, never text from the model as authority.
     * Topic carryover retains relevant Skills across follow-ups; an explicit
     * change of subject drops those contexts until re-triggered. Disabled
     * Skills cannot be reenabled: only pinned entries are ever selectable.
     */
    fun observeUserTurn(utterance: String): SkillTurnRoutingDecision {
        turn++
        val text = utterance.lowercase(Locale.ROOT)
        val pivot = listOf("zmiana tematu", "zmieńmy temat", "zmienmy temat",
            "inna sprawa", "inny temat", "nowy temat", "teraz coś innego",
            "teraz cos innego", "zamiast tego").any { it in text }
        val wantsMessage = listOf("wiadomo", "notatk", "przekaż", "przekaz",
            "zapisz", "zanotuj", "kontakt").any { it in text }
        val wantsClarification = listOf("korekt", "popraw", "sprostowan",
            "niejasn", "doprecyz", "wyjaś", "wyjas", "dopyt", "brakuj")
            .any { it in text }

        if (mode == SkillLoadingMode.CONTEXTUAL) {
            if (pivot) {
                messageActive = false
                clarificationActive = false
            }
            if (wantsMessage) messageActive = true
            if (wantsClarification) clarificationActive = true
        }
        val nextEntries = if (mode == SkillLoadingMode.ALL_ACTIVE) pinned.entries
            else pinned.entries.filter { entry ->
                when (entry.capability) {
                    SkillCapability.MESSAGE_CAPTURE -> messageActive
                    SkillCapability.CLARIFICATION -> clarificationActive
                    else -> true // General/closure metadata and versions stay pinned.
                }
            }
        val nextIds = nextEntries.map { it.id }
        val old = selected.toSet()
        val next = nextIds.toSet()
        val added = nextIds.filterNot { it in old }
        val removed = selected.filterNot { it in next }
        selected = nextIds
        return SkillTurnRoutingDecision(
            turn = turn,
            snapshot = if (mode == SkillLoadingMode.ALL_ACTIVE) pinned
                else pinned.copy(entries = nextEntries),
            selectedIds = nextIds,
            activatedIds = added,
            deactivatedIds = removed,
            requiresPromptRefresh = added.isNotEmpty() || removed.isNotEmpty(),
        )
    }
}
