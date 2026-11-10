package pl.michalmatu.aicallbridge.conversation

import com.google.gson.Gson
import java.util.UUID

/** A versioned skill is an instruction, never permission to call, disclose data or execute tools. */
internal enum class SkillSection(val title: String) {
    STYLE("Prowadzenie rozmowy"), MESSAGE("Obsługa wiadomości"), CLOSURE("Zakończenie rozmowy")
}
/** Metadata is versioned together with instructions; never grants execution rights. */
internal enum class SkillCapability { GENERAL, MESSAGE_CAPTURE, CLARIFICATION, CLOSURE }

internal data class SkillMetadata(
    val name: String,
    val description: String,
    val capability: SkillCapability,
) {
    fun validated(): SkillMetadata {
        require(name.isNotBlank()) { "skill_metadata_name_missing" }
        require(description.isNotBlank()) { "skill_metadata_description_missing" }
        return copy(name = name.trim(), description = description.trim())
    }
}

/** Old encrypted records omit metadata; null preserves Gson schema=1 compatibility. */
internal data class SkillRevision(
    val version: Int,
    val instructions: String,
    val metadata: SkillMetadata? = null,
)
internal data class VersionedSkill(
    val id: String, val name: String, val section: SkillSection, val revisions: List<SkillRevision>,
) {
    fun at(version: Int): SkillRevision =
        revisions.singleOrNull { it.version == version } ?: error("skill_revision_missing")

    fun metadataAt(version: Int): SkillMetadata = at(version).metadata
        ?: SkillMetadata(
            name = name,
            description = when (id) {
                "ConversationStyle" -> "Styl i sposób wypowiedzi"
                "MessageCapture" -> "Przyjmowanie i porządkowanie wiadomości"
                "Clarification" -> "Dopytywanie i obsługa korekt"
                "NaturalClosure" -> "Rozpoznawanie zakończenia rozmowy"
                else -> "Wskazówki rozmowy"
            },
            capability = when (id) {
                "MessageCapture" -> SkillCapability.MESSAGE_CAPTURE
                "Clarification" -> SkillCapability.CLARIFICATION
                "NaturalClosure" -> SkillCapability.CLOSURE
                else -> SkillCapability.GENERAL
            },
        )
}
internal data class SkillPreset(
    val id: String, val name: String, val versions: Map<String, Int>, val isFactory: Boolean,
)
internal data class SkillSnapshotEntry(
    val id: String, val name: String, val section: SkillSection,
    val version: Int, val instructions: String,
    val description: String = "",
    val capability: SkillCapability = SkillCapability.GENERAL,
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
                s.revisions.all { it.version > 0 && it.instructions.isNotBlank() &&
                    (it.metadata == null || runCatching { it.metadata.validated() }.isSuccess) } &&
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
            val metadata = skill.metadataAt(version)
            SkillSnapshotEntry(id, metadata.name, skill.section, version,
                skill.at(version).instructions, metadata.description, metadata.capability)
        }.sortedWith(compareBy({ it.section.ordinal }, { it.id }))
        return ConversationSkillSnapshot(preset.id, preset.name, items)
    }

    /** The consumer selector reflects only the incoming receptionist's speaking tone. */
    fun receptionistToneVersion(): Int =
        active.versions["ConversationStyle"] ?: 0

    fun withReceptionistTone(version: Int): ConversationSkillLibrary {
        require(version == 1 || version == 2) { "unknown_receptionist_tone" }
        return if (active.isFactory) {
            val desired = if (version == 2) "formal" else "natural"
            if (activePresetId == desired) this else select(desired)
        } else {
            if (active.versions["ConversationStyle"] == version) this
            else bindVersion("ConversationStyle", version)
        }
    }

    fun personalInstructions(): String {
        val revision = active.versions[PERSONAL_INSTRUCTIONS_ID] ?: return ""
        return skills.singleOrNull { it.id == PERSONAL_INSTRUCTIONS_ID }
            ?.at(revision)?.instructions.orEmpty()
    }

    /**
     * No user-facing preset naming step. An edited factory configuration is copied
     * only on Save; all other presets and versions remain intact.
     */
    fun withPersonalInstructions(text: String, automaticName: String): ConversationSkillLibrary {
        val normalized = text.trim()
        require(normalized.length <= MAX_PERSONAL_INSTRUCTIONS_LENGTH) {
            "personal_instructions_too_long"
        }
        if (normalized == personalInstructions()) return this
        if (normalized.isEmpty()) {
            return if (active.isFactory || PERSONAL_INSTRUCTIONS_ID !in active.versions) this
                else bindVersion(PERSONAL_INSTRUCTIONS_ID, null)
        }
        val source = if (active.isFactory) copyActive(automaticName) else this
        if (source.skills.any { it.id == PERSONAL_INSTRUCTIONS_ID }) {
            return source.reviseSkill(PERSONAL_INSTRUCTIONS_ID, normalized)
        }
        val skill = VersionedSkill(
            PERSONAL_INSTRUCTIONS_ID, "Własne instrukcje", SkillSection.STYLE,
            listOf(SkillRevision(1, normalized,
                SkillMetadata("Własne instrukcje",
                    "Dodatkowe wskazówki dla sekretarki odbierającej",
                    SkillCapability.GENERAL))),
        )
        val updated = source.active.copy(
            versions = source.active.versions + (PERSONAL_INSTRUCTIONS_ID to 1))
        return of(source.skills + skill,
            source.presets.map { if (it.id == source.activePresetId) updated else it },
            source.activePresetId)
    }

    /**
     * Canonical incoming-receptionist configuration. Drops obsolete preset entries,
     * historical revisions and unused instructions while retaining the currently
     * selected tone and the currently active user's personal guidance.
     * Idempotent, deterministic and safe for repeated encrypted-store loads.
     */
    fun cleanedForReceptionist(): ConversationSkillLibrary {
        val tone = if (receptionistToneVersion() == 2) 2 else 1
        val core = defaults().withReceptionistTone(tone)
        val personal = personalInstructions().trim()
        if (personal.isEmpty()) return core
        val privateSkill = VersionedSkill(PERSONAL_INSTRUCTIONS_ID,
            "Własne instrukcje", SkillSection.STYLE,
            listOf(SkillRevision(1, personal, SkillMetadata(
                "Własne instrukcje",
                "Dodatkowe wskazówki dla sekretarki odbierającej",
                SkillCapability.GENERAL))))
        val personalPreset = SkillPreset(
            "receptionist-personal", "Własne ustawienia sekretarki",
            core.active.versions + (PERSONAL_INSTRUCTIONS_ID to 1), false)
        return of(core.skills + privateSkill, core.presets + personalPreset,
            personalPreset.id)
    }

    fun isCleanForReceptionist(): Boolean {
        val canonical = cleanedForReceptionist()
        return skills == canonical.skills && presets == canonical.presets &&
            activePresetId == canonical.activePresetId
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

    fun reviseSkill(id: String, text: String,
        metadata: SkillMetadata? = null): ConversationSkillLibrary {
        require(!active.isFactory) { "copy_factory_preset_first" }
        require(text.isNotBlank()) { "skill_instructions_missing" }
        val target = skills.singleOrNull { it.id == id } ?: error("skill_missing")
        val version = target.revisions.maxOf { it.version } + 1
        val selectedVersion = active.versions[id]
        val inherited = target.metadataAt(selectedVersion ?: target.revisions.maxOf { it.version })
        val resolved = (metadata ?: inherited).validated()
        val changed = skills.map {
            if (it.id == id) it.copy(revisions = it.revisions +
                SkillRevision(version, text.trim(), resolved))
            else it
        }
        val selected = active.copy(versions = active.versions + (id to version))
        return of(changed, presets.map { if (it.id == active.id) selected else it }, activePresetId)
    }

    /** Metadata revisions share the same reversible version history as instructions. */
    fun reviseMetadata(id: String, metadata: SkillMetadata): ConversationSkillLibrary {
        val target = skills.singleOrNull { it.id == id } ?: error("skill_missing")
        val current = active.versions[id] ?: target.revisions.maxOf { it.version }
        return reviseSkill(id, target.at(current).instructions, metadata.validated())
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
        const val PERSONAL_INSTRUCTIONS_ID = "PersonalInstructions"
        const val MAX_PERSONAL_INSTRUCTIONS_LENGTH = 1600
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
