package pl.michalmatu.aicallbridge.conversation

import com.google.gson.Gson
import java.util.UUID

/** A versioned skill is an instruction, never permission to call, disclose data or execute tools. */
internal enum class SkillSection(val title: String) {
    STYLE("Prowadzenie rozmowy"), MESSAGE("Obsługa wiadomości"), CLOSURE("Zakończenie rozmowy")
}
internal data class SkillRevision(val version: Int, val instructions: String)
internal data class VersionedSkill(
    val id: String, val name: String, val section: SkillSection, val revisions: List<SkillRevision>,
) {
    fun at(version: Int): SkillRevision =
        revisions.singleOrNull { it.version == version } ?: error("skill_revision_missing")
}
internal data class SkillPreset(
    val id: String, val name: String, val versions: Map<String, Int>, val isFactory: Boolean,
)
internal data class SkillSnapshotEntry(
    val id: String, val name: String, val section: SkillSection,
    val version: Int, val instructions: String,
)
internal data class ConversationSkillSnapshot(
    val presetId: String, val presetName: String, val entries: List<SkillSnapshotEntry>,
) {
    fun instructions(): String = entries.joinToString("\n") {
        "- " + it.name + " (" + it.id + " v" + it.version + "): " + it.instructions
    }
}

/** A preset pins exact revisions. Only the active custom preset changes after an edit. */
internal class ConversationSkillLibrary private constructor(
    val skills: List<VersionedSkill>,
    val presets: List<SkillPreset>,
    val activePresetId: String,
) {
    val active: SkillPreset get() = presets.single { it.id == activePresetId }

    init {
        require(skills.map { it.id }.distinct().size == skills.size) { "duplicate_skill" }
        require(presets.map { it.id }.distinct().size == presets.size) { "duplicate_preset" }
        require(presets.any { it.id == activePresetId }) { "active_preset_missing" }
        require(skills.all { s ->
            s.id.isNotBlank() && s.name.isNotBlank() && s.revisions.isNotEmpty() &&
                s.revisions.all { it.version > 0 && it.instructions.isNotBlank() } &&
                s.revisions.map { it.version }.distinct().size == s.revisions.size
        }) { "invalid_skill" }
        require(presets.all { p ->
            p.id.isNotBlank() && p.name.isNotBlank() &&
                p.versions.all { (id, v) ->
                    skills.firstOrNull { it.id == id }?.revisions?.any { it.version == v } == true
                }
        }) { "invalid_preset_binding" }
    }

    fun snapshot(presetId: String = activePresetId): ConversationSkillSnapshot {
        val preset = presets.singleOrNull { it.id == presetId } ?: error("preset_missing")
        val items = preset.versions.map { (id, version) ->
            val skill = skills.single { it.id == id }
            SkillSnapshotEntry(id, skill.name, skill.section, version, skill.at(version).instructions)
        }.sortedWith(compareBy({ it.section.ordinal }, { it.id }))
        return ConversationSkillSnapshot(preset.id, preset.name, items)
    }

    fun select(id: String): ConversationSkillLibrary {
        require(presets.any { it.id == id }) { "preset_missing" }
        return of(skills, presets, id)
    }

    fun copyActive(name: String): ConversationSkillLibrary {
        require(name.isNotBlank()) { "preset_name_missing" }
        val copy = active.copy(id = "custom-" + UUID.randomUUID(), name = name.trim(),
            versions = active.versions.toMap(), isFactory = false)
        return of(skills, presets + copy, copy.id)
    }

    fun reviseSkill(id: String, text: String): ConversationSkillLibrary {
        require(!active.isFactory) { "copy_factory_preset_first" }
        require(text.isNotBlank()) { "skill_instructions_missing" }
        val target = skills.singleOrNull { it.id == id } ?: error("skill_missing")
        val version = target.revisions.maxOf { it.version } + 1
        val changed = skills.map {
            if (it.id == id) it.copy(revisions = it.revisions + SkillRevision(version, text.trim()))
            else it
        }
        val selected = active.copy(versions = active.versions + (id to version))
        return of(changed, presets.map { if (it.id == active.id) selected else it }, activePresetId)
    }

    fun bindVersion(id: String, version: Int?): ConversationSkillLibrary {
        require(!active.isFactory) { "copy_factory_preset_first" }
        val target = skills.singleOrNull { it.id == id } ?: error("skill_missing")
        if (version != null) target.at(version)
        val selected = active.versions.toMutableMap()
        if (version == null) selected.remove(id) else selected[id] = version
        val updated = active.copy(versions = selected)
        return of(skills, presets.map { if (it.id == active.id) updated else it }, activePresetId)
    }

    companion object {
        fun of(skills: List<VersionedSkill>, presets: List<SkillPreset>, activePresetId: String) =
            ConversationSkillLibrary(
                skills.map { it.copy(revisions = it.revisions.toList()) },
                presets.map { it.copy(versions = it.versions.toMap()) }, activePresetId)

        fun defaults(): ConversationSkillLibrary {
            val skills = listOf(
                VersionedSkill("ConversationStyle", "Styl wypowiedzi", SkillSection.STYLE, listOf(
                    SkillRevision(1, "Rozmawiaj naturalnie, ciepło i rzeczowo. Nie recytuj szablonu ani nie witaj ponownie."),
                    SkillRevision(2, "Zachowaj formalny, uprzejmy i precyzyjny ton. Bez zbędnych formuł."),
                )),
                VersionedSkill("MessageCapture", "Przyjmowanie wiadomości", SkillSection.MESSAGE, listOf(
                    SkillRevision(1, "Ustal sens sprawy i ważne informacje, które rozmówca dobrowolnie podaje. Nie wymyślaj faktów ani nie obiecuj nieistniejącego powiadomienia."),
                )),
                VersionedSkill("Clarification", "Dopytywanie", SkillSection.MESSAGE, listOf(
                    SkillRevision(1, "Pytaj tylko o naprawdę niejasne lub brakujące informacje. Uwzględniaj poprawki i potwierdzenia; nie ponawiaj zakończonych pytań."),
                )),
                VersionedSkill("NaturalClosure", "Naturalne zakończenie", SkillSection.CLOSURE, listOf(
                    SkillRevision(1, "Oceniaj zakończenie na podstawie całej rozmowy. Po jasnym zakończeniu zaproponuj end_call z pożegnaniem; przy cytacie, korekcie lub nowej sprawie kontynuuj. Skill nie rozłącza samodzielnie."),
                )),
            )
            val natural = skills.associate { it.id to 1 }
            return of(skills, listOf(
                SkillPreset("natural", "Sekretarka naturalna", natural, true),
                SkillPreset("formal", "Sekretarka formalna", natural + ("ConversationStyle" to 2), true),
                SkillPreset("message", "Zbieranie wiadomości", natural - "ConversationStyle", true),
            ), "natural")
        }
    }
}

/** Persisted in the existing encrypted/no-backup preparation store, not SharedPreferences. */
internal class ConversationSkillLibraryRepository(
    private val read: () -> String?, private val write: (String) -> Unit,
) {
    private data class Record(
        val schema: Int, val skills: List<VersionedSkill>,
        val presets: List<SkillPreset>, val activePresetId: String,
    )
    private val gson = Gson()
    fun load(): ConversationSkillLibrary {
        val raw = read() ?: return ConversationSkillLibrary.defaults()
        val record = gson.fromJson(raw, Record::class.java)
        require(record.schema == 1) { "skill_library_schema_unknown" }
        return ConversationSkillLibrary.of(record.skills, record.presets, record.activePresetId)
    }
    fun save(library: ConversationSkillLibrary) =
        write(gson.toJson(Record(1, library.skills, library.presets, library.activePresetId)))
}
