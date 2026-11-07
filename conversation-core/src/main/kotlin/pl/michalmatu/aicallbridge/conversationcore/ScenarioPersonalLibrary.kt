package pl.michalmatu.aicallbridge.conversationcore

/**
 * Personal revisions are private, editable task descriptions, never phone-call grants.
 * Library operations are pure; Android's existing encrypted preparation store persists them.
 */
data class PersonalScenarioVersion(
    val number: Int,
    val title: String,
    val goal: String,
    val values: Map<String, String>,
)

data class PersonalScenario(
    val id: String,
    val templateId: String,
    val versions: List<PersonalScenarioVersion>,
) {
    val current: PersonalScenarioVersion get() = versions.last()

    fun revised(title: String, goal: String, draft: ScenarioDraft): PersonalScenario {
        val template = requireNotNull(ScenarioCatalog.byId(templateId)) { "unknown_scenario_template" }
        require(draft.scenarioId == templateId) { "scenario_draft_mismatch" }
        validateValues(template, draft.values)
        require(title.isNotBlank() && goal.isNotBlank()) { "missing_personal_scenario_name_or_goal" }
        if (current.title == title && current.goal == goal && current.values == draft.values) return this
        return copy(versions = versions + PersonalScenarioVersion(
            current.number + 1, title.trim(), goal.trim(), draft.values.toMap()))
    }

    fun restored(number: Int): PersonalScenario {
        val old = versions.singleOrNull { it.number == number }
            ?: throw IllegalArgumentException("scenario_version_missing")
        return revised(old.title, old.goal, ScenarioDraft(templateId, old.values))
    }

    fun validated(): PersonalScenario {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,64}"))) { "bad_personal_scenario_id" }
        val template = requireNotNull(ScenarioCatalog.byId(templateId)) { "unknown_scenario_template" }
        require(versions.isNotEmpty()) { "scenario_has_no_versions" }
        versions.forEachIndexed { index, version ->
            require(version.number == index + 1) { "non_contiguous_scenario_versions" }
            require(version.title.isNotBlank() && version.goal.isNotBlank()) {
                "missing_personal_scenario_name_or_goal"
            }
            validateValues(template, version.values)
        }
        return this
    }
}

data class ScenarioPersonalLibrary(val items: List<PersonalScenario> = emptyList()) {
    fun validated(): ScenarioPersonalLibrary {
        require(items.map { it.id }.distinct().size == items.size) { "duplicate_personal_scenario_id" }
        items.forEach(PersonalScenario::validated)
        return this
    }

    fun create(
        template: ScenarioDefinition,
        draft: ScenarioDraft,
        id: String,
        title: String,
        goal: String,
    ): ScenarioPersonalLibrary {
        require(ScenarioCatalog.byId(template.id) == template) { "unknown_scenario_template" }
        require(draft.scenarioId == template.id) { "scenario_draft_mismatch" }
        require(items.none { it.id == id }) { "duplicate_personal_scenario_id" }
        require(title.isNotBlank() && goal.isNotBlank()) { "missing_personal_scenario_name_or_goal" }
        validateValues(template, draft.values)
        val entry = PersonalScenario(id, template.id, listOf(
            PersonalScenarioVersion(1, title.trim(), goal.trim(), draft.values.toMap())
        )).validated()
        return copy(items = items + entry).validated()
    }

    fun revise(id: String, title: String, goal: String, draft: ScenarioDraft): ScenarioPersonalLibrary {
        require(items.any { it.id == id }) { "scenario_not_found" }
        return copy(items = items.map {
            if (it.id == id) it.revised(title, goal, draft) else it
        }).validated()
    }

    fun restore(id: String, number: Int): ScenarioPersonalLibrary {
        require(items.any { it.id == id }) { "scenario_not_found" }
        return copy(items = items.map {
            if (it.id == id) it.restored(number) else it
        }).validated()
    }

    fun delete(id: String): ScenarioPersonalLibrary {
        require(items.any { it.id == id }) { "scenario_not_found" }
        return copy(items = items.filterNot { it.id == id }).validated()
    }
}

private fun validateValues(template: ScenarioDefinition, values: Map<String, String>) {
    val permitted = template.fields.map { it.id }.toSet()
    require(values.keys.all { it in permitted }) { "unknown_scenario_form_field" }
}
