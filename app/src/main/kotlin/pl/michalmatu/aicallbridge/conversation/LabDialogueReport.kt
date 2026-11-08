package pl.michalmatu.aicallbridge.conversation

import java.util.UUID

/** An unset semantic assessment is NEVER an automatic pass. */
internal enum class LabReviewDimension {
    TOPIC, MEMORY, CORRECTIONS, FACTUALITY, NATURALNESS, QUESTIONS, PROMISES, ENDING
}
internal enum class LabReviewVerdict { UNASSESSED, PASS, FAIL }

internal data class LabReportTurn(
    val turn: Int,
    val caller: String,
    val assistant: String,
    val endProposed: Boolean,
    val latencyMs: Long,
)

internal data class LabReviewEvidence(
    val dimension: LabReviewDimension,
    val verdict: LabReviewVerdict,
    val turn: Int?,
    val note: String,
)

internal data class LabDialogueReport(
    val id: String,
    val testId: String?,
    val testRevision: Int?,
    val modelId: String,
    val presetId: String,
    val turns: List<LabReportTurn>,
    val branches: List<LabAdaptiveTrace>,
    val reviews: List<LabReviewEvidence>,
    val stopped: Boolean,
) {
    fun meanLatency(): Long = if (turns.isEmpty()) 0 else turns.sumOf { it.latencyMs } / turns.size

    fun review(dimension: LabReviewDimension, verdict: LabReviewVerdict,
               turn: Int?, note: String): LabDialogueReport {
        require(turn == null || turns.any { it.turn == turn }) { "lab_review_unknown_turn" }
        require(verdict == LabReviewVerdict.UNASSESSED || turn != null) {
            "lab_review_evidence_turn_required"
        }
        val updated = LabReviewEvidence(dimension, verdict, turn, note.trim())
        return copy(reviews = reviews.filterNot { it.dimension == dimension } + updated)
    }

    fun evidenceText(): String {
        val base = "Model: " + modelId + " · Skills: " + presetId +
            "\nTurns: " + turns.size + " · Average model response: " + meanLatency() +
            " ms · END_CALL proposals: " + turns.count { it.endProposed } +
            " · Stopped: " + stopped
        val decisions = branches.joinToString("\n") {
            "After #" + it.turn + " → rule " + it.ruleNumber + " (" + it.condition + ")"
        }
        val rubric = LabReviewDimension.entries.joinToString("\n") { dimension ->
            val result = reviews.find { it.dimension == dimension }
            dimension.name + ": " + (result?.verdict ?: LabReviewVerdict.UNASSESSED) +
                (result?.turn?.let { " [turn " + it + "]" } ?: "") +
                (result?.note?.let { " " + it } ?: "")
        }
        return base + "\nAdaptive decisions:\n" + decisions +
            "\nSemantic review (unassessed until evidence is entered):\n" + rubric
    }

    companion object {
        fun fromSnapshot(
            testId: String?, revision: Int?, modelId: String, presetId: String,
            state: SkillLabUiState, id: String = UUID.randomUUID().toString(),
        ): LabDialogueReport {
            val turns = state.completed.map {
                LabReportTurn(it.pending.turnIndex, it.pending.utterance,
                    it.observation.reply, it.observation.actualEnd, it.observation.elapsedMs)
            }
            return LabDialogueReport(id, testId, revision, modelId, presetId, turns,
                state.adaptiveTrace.toList(), LabReviewDimension.entries.map {
                    LabReviewEvidence(it, LabReviewVerdict.UNASSESSED, null, "")
                }, state.phase == SkillLabPhase.STOPPED)
        }
    }
}

internal data class LabABComparison(
    val comparable: Boolean,
    val aTurns: Int,
    val bTurns: Int,
    val aLatencyMs: Long,
    val bLatencyMs: Long,
    val aEndProposals: Int,
    val bEndProposals: Int,
    val assessments: Map<LabReviewDimension, Pair<LabReviewVerdict, LabReviewVerdict>>,
) {
    companion object {
        fun between(a: LabDialogueReport, b: LabDialogueReport): LabABComparison =
            LabABComparison(
                comparable = a.testId != null && a.testId == b.testId &&
                    a.testRevision == b.testRevision,
                aTurns = a.turns.size,
                bTurns = b.turns.size,
                aLatencyMs = a.meanLatency(),
                bLatencyMs = b.meanLatency(),
                aEndProposals = a.turns.count { it.endProposed },
                bEndProposals = b.turns.count { it.endProposed },
                assessments = LabReviewDimension.entries.associateWith { dimension ->
                    (a.reviews.find { it.dimension == dimension }?.verdict
                        ?: LabReviewVerdict.UNASSESSED) to
                    (b.reviews.find { it.dimension == dimension }?.verdict
                        ?: LabReviewVerdict.UNASSESSED)
                },
            )
    }
}

internal data class LabReportLibrary(val reports: List<LabDialogueReport> = emptyList()) {
    fun add(report: LabDialogueReport) = copy(reports = reports.filterNot { it.id == report.id } + report)

    fun review(id: String, dimension: LabReviewDimension, verdict: LabReviewVerdict,
               turn: Int?, note: String): LabReportLibrary {
        require(reports.any { it.id == id }) { "lab_report_not_found" }
        return copy(reports = reports.map {
            if (it.id == id) it.review(dimension, verdict, turn, note) else it
        })
    }

    fun compare(a: String, b: String): LabABComparison {
        require(a != b) { "lab_ab_same_report" }
        val left = requireNotNull(reports.find { it.id == a }) { "lab_ab_missing_a" }
        val right = requireNotNull(reports.find { it.id == b }) { "lab_ab_missing_b" }
        return LabABComparison.between(left, right)
    }
}
