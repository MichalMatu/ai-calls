package pl.michalmatu.aicallbridge.conversation

import java.util.UUID

/** Private, user-authored laboratory input. Never a telephone task or authorization. */
internal enum class LabRole { GENERAL, INCOMING, OUTGOING }

internal data class LabCaseRevision(
    val revision: Int,
    val title: String,
    val goal: String,
    val role: LabRole,
    val opening: String,
    val callerState: String,
    val facts: String,
    val expectations: String,
    val rules: String,
    val completion: String,
) {
    fun validated(): LabCaseRevision {
        require(revision > 0 && title.isNotBlank() && goal.isNotBlank()) {
            "lab_case_title_goal_required"
        }
        require(opening.isNotBlank()) { "lab_case_opening_required" }
        return this
    }
}

internal data class LabCase(
    val id: String,
    val versions: List<LabCaseRevision>,
    val selectedRevision: Int,
) {
    fun validated(): LabCase {
        require(id.matches(Regex("[a-zA-Z0-9-]+"))) { "lab_case_invalid_id" }
        require(versions.isNotEmpty()) { "lab_case_versions_empty" }
        require(versions.map { it.revision }.distinct().size == versions.size) {
            "lab_case_duplicate_revision"
        }
        versions.forEach { it.validated() }
        require(versions.any { it.revision == selectedRevision }) {
            "lab_case_selected_revision_missing"
        }
        return this
    }

    fun selected(): LabCaseRevision = versions.first { it.revision == selectedRevision }

    fun revise(draft: LabCaseRevision): LabCase {
        val next = (versions.maxOfOrNull { it.revision } ?: 0) + 1
        return copy(versions = versions + draft.copy(revision = next).validated(),
            selectedRevision = next).validated()
    }

    fun select(number: Int): LabCase = copy(selectedRevision = number).validated()
}

internal data class LabCaseLibrary(val cases: List<LabCase> = emptyList()) {
    fun validated(): LabCaseLibrary {
        require(cases.map { it.id }.distinct().size == cases.size) {
            "lab_case_duplicate_id"
        }
        cases.forEach { it.validated() }
        return this
    }

    fun create(draft: LabCaseRevision): LabCaseLibrary =
        copy(cases = cases + LabCase(UUID.randomUUID().toString(),
            listOf(draft.copy(revision = 1).validated()), 1)).validated()

    fun update(id: String, draft: LabCaseRevision): LabCaseLibrary =
        replace(id) { it.revise(draft) }

    fun select(id: String, revision: Int): LabCaseLibrary =
        replace(id) { it.select(revision) }

    fun duplicate(id: String): LabCaseLibrary {
        val source = requireNotNull(cases.find { it.id == id }) { "lab_case_not_found" }
        return create(source.selected().copy(revision = 1,
            title = source.selected().title + " (copy)"))
    }

    fun delete(id: String): LabCaseLibrary {
        require(cases.any { it.id == id }) { "lab_case_not_found" }
        return copy(cases = cases.filterNot { it.id == id }).validated()
    }

    private fun replace(id: String, change: (LabCase) -> LabCase): LabCaseLibrary {
        require(cases.any { it.id == id }) { "lab_case_not_found" }
        return copy(cases = cases.map { if (it.id == id) change(it) else it }).validated()
    }
}
