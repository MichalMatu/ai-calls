package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.JsonObject
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.conversationcore.ScenarioDraft
import pl.michalmatu.aicallbridge.conversationcore.ScenarioPersonalLibrary
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import pl.michalmatu.aicallbridge.identity.AndroidKeystoreIdentityVaultAead
import pl.michalmatu.aicallbridge.identity.IdentityVaultCiphertext

/** Encrypted preparation records; personal fact values remain in the existing IdentityVault. */
internal class AndroidConversationPreparationStore(
    context: Context,
    directoryName: String = "conversation-preparation",
    keyAlias: String = "pl.michalmatu.aicallbridge.conversation.preparation.v1",
    legacyPreferencesName: String = "campaign_gemma_direct_prompt_skills_v1",
) {
    // Synthetic test stores keep full versioned history for Lab A/B regression coverage.
    // The user's actual receptionist store holds only the current tone and guidance.
    private val cleanUserPresets = directoryName == "conversation-preparation"

    private val root = File(context.noBackupFilesDir, directoryName).apply {
        require(directoryName.isNotBlank() && directoryName != "." && directoryName != ".." && directoryName == File(directoryName).name) { "invalid_preparation_directory" }
        check(isDirectory || mkdirs())
    }
    private val aead = AndroidKeystoreIdentityVaultAead(keyAlias)
    private val repository = ConversationPreparationRepository(::readRecord, ::writeRecord)
    private val skillsRepository = ConversationSkillLibraryRepository(
        { readRecord("skills-library-v1") }, { writeRecord("skills-library-v1", it) })

    init { synchronized(LOCK) {
        // One-shot data migration. The old mutable API/store is removed; configuration needs no grant.
        val legacy = context.getSharedPreferences(legacyPreferencesName, Context.MODE_PRIVATE)
        if (legacy.contains("skills")) {
            val record = file(ConversationPreparation.OUTGOING_ID)
            if (!record.baseFile.exists()) {
                val selected = legacy.getStringSet("skills", emptySet()).orEmpty().map(ConversationSkill::valueOf).toSet()
                repository.save(ConversationPreparation.outgoing().withSettings("", selected, ConversationPreparation.outgoing().dataFields))
            }
            check(legacy.edit().clear().commit()) { "legacy_skills_migration_commit_failed" }
        }
    } }

    fun load(id: String) = synchronized(LOCK) {
        val stored = repository.load(id)
        if (stored.direction == ConversationDirection.INCOMING)
            stored.withSkillSnapshot(loadSkillLibrary().snapshot())
        else stored
    }
    fun save(value: ConversationPreparation) = synchronized(LOCK) { repository.save(value) }
    fun loadSkillLibrary(): ConversationSkillLibrary = synchronized(LOCK) {
        val previous = skillsRepository.load()
        if (!cleanUserPresets || previous.isCleanForReceptionist()) previous
        else previous.cleanedForReceptionist().also(skillsRepository::save)
    }
    fun saveSkillLibrary(value: ConversationSkillLibrary) = synchronized(LOCK) {
        skillsRepository.save(if (cleanUserPresets) value.cleanedForReceptionist() else value)
    }


    /** The laboratory has its own encrypted no-backup record, separate from call scenarios. */
    fun loadLabCases(): LabCaseLibrary = synchronized(LOCK) {
        val raw = readRecord("lab-dialog-cases-v1") ?: return@synchronized LabCaseLibrary()
        requireNotNull(Gson().fromJson(raw, LabCaseLibrary::class.java)) {
            "lab_case_library_unreadable"
        }.validated()
    }

    fun saveLabCases(library: LabCaseLibrary) = synchronized(LOCK) {
        writeRecord("lab-dialog-cases-v1", Gson().toJson(library.validated()))
    }


    /** Reports and private transcript evidence remain encrypted and excluded from backup. */
    fun loadLabReports(): LabReportLibrary = synchronized(LOCK) {
        val raw = readRecord("lab-dialog-reports-v1")
            ?: return@synchronized LabReportLibrary()
        requireNotNull(Gson().fromJson(raw, LabReportLibrary::class.java)) {
            "lab_report_library_unreadable"
        }
    }

    fun saveLabReports(library: LabReportLibrary) = synchronized(LOCK) {
        writeRecord("lab-dialog-reports-v1", Gson().toJson(library))
    }

    /** Private Keystore-encrypted task payload. No phone number in Intent extras. */
    private data class ScenarioOutgoingRecord(
        val schema: Int, val task: pl.michalmatu.aicallbridge.campaign.ScenarioOutgoingTask,
    )

    fun loadOutgoingScenarioTask(): pl.michalmatu.aicallbridge.campaign.ScenarioOutgoingTask? =
        synchronized(LOCK) {
            runCatching {
                val raw = readRecord("outgoing-scenario-task-v1")
                    ?: return@synchronized null
                val record = Gson().fromJson(raw, ScenarioOutgoingRecord::class.java)
                require(record.schema == 1) { "outgoing_scenario_schema_unknown" }
                record.task.validated()
            }.getOrNull()
        }

    fun saveOutgoingScenarioTask(
        task: pl.michalmatu.aicallbridge.campaign.ScenarioOutgoingTask,
    ) = synchronized(LOCK) {
        writeRecord("outgoing-scenario-task-v1",
            Gson().toJson(ScenarioOutgoingRecord(1, task.validated())))
    }

    private data class ScenarioDraftRecord(
        val version: Int, val scenarioId: String, val values: Map<String, String>,
    )

    /** Same encrypted, no-backup store as conversation preparation: no second preference silo. */
    fun loadScenarioDraft(id: String): ScenarioDraft? = synchronized(LOCK) {
        val definition = ScenarioCatalog.byId(id) ?: return@synchronized null
        val raw = readRecord("scenario-draft-$id") ?: return@synchronized null
        val record = Gson().fromJson(raw, ScenarioDraftRecord::class.java)
        require(record.version == 1 && record.scenarioId == id) {
            "scenario_draft_version_or_id_mismatch"
        }
        val permitted = definition.fields.map { it.id }.toSet()
        ScenarioDraft(id, record.values.filterKeys { it in permitted })
    }

    /** Personal versions stay in the same Keystore-AEAD / AtomicFile namespace as Skills. */
    fun loadPersonalScenarios(): ScenarioPersonalLibrary = synchronized(LOCK) {
        val raw = readRecord("scenario-personal-library-v1")
            ?: return@synchronized ScenarioPersonalLibrary()
        requireNotNull(Gson().fromJson(raw, ScenarioPersonalLibrary::class.java)) {
            "scenario_personal_library_unreadable"
        }.validated()
    }

    fun savePersonalScenarios(library: ScenarioPersonalLibrary) = synchronized(LOCK) {
        val valid = library.validated()
        writeRecord("scenario-personal-library-v1", Gson().toJson(valid))
    }

    fun saveScenarioDraft(draft: ScenarioDraft) = synchronized(LOCK) {
        val definition = requireNotNull(ScenarioCatalog.byId(draft.scenarioId)) {
            "scenario_definition_missing"
        }
        val permitted = definition.fields.map { it.id }.toSet()
        require(draft.values.keys.all { it in permitted }) { "unknown_scenario_form_field" }
        writeRecord("scenario-draft-${draft.scenarioId}",
            Gson().toJson(ScenarioDraftRecord(1, draft.scenarioId, draft.values.toMap())))
    }

    /** One encrypted AtomicFile holding independent notebooks keyed by call session ID. */
    private val notebookRecordId = "notebook-library-v1"

    fun notebooks(): List<ConversationNotebook> = synchronized(LOCK) {
        readNotebook().conversations.sortedByDescending {
            it.entries.lastOrNull()?.timestampMs ?: 0L
        }
    }

    fun appendNote(sessionId: String, direction: String, turn: Int,
        proposal: pl.michalmatu.aicallbridge.conversationtools.ConversationNoteProposal
    ): Boolean = synchronized(LOCK) {
        require(ConversationNotebookPolicy.validSession(sessionId, direction, turn)) {
            "notebook_scope_invalid"
        }
        val previous = readNotebook()
        val next = ConversationNotebookPolicy.append(previous, sessionId, direction,
            turn, proposal, System.currentTimeMillis())
        if (next != previous) {
            val json = Gson().toJson(next)
            require(json.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) {
                "notebook_storage_full"
            }
            writeRecord(notebookRecordId, json)
        }
        true
    }

    private fun readNotebook(): ConversationNotebookLibrary {
        val raw = readRecord(notebookRecordId) ?: return ConversationNotebookLibrary()
        require(raw.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) {
            "notebook_storage_full"
        }
        val value = requireNotNull(Gson().fromJson(raw, ConversationNotebookLibrary::class.java)) {
            "notebook_unreadable"
        }
        return ConversationNotebookPolicy.validate(value)
    }

    private fun file(id: String): AtomicFile {
        val name = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(root, "$name.json"))
    }
    private fun readRecord(id: String): String? {
        val file = file(id)
        if (!file.baseFile.exists()) return null
        val envelope = JsonParser.parseString(file.openRead().bufferedReader().use { it.readText() }).asJsonObject
        val decoder = Base64.getDecoder()
        val ciphertext = IdentityVaultCiphertext(decoder.decode(envelope["nonce"].asString), decoder.decode(envelope["ciphertext"].asString))
        return aead.decrypt(ciphertext, id.toByteArray()).toString(Charsets.UTF_8)
    }
    private fun writeRecord(id: String, content: String) {
        val cipher = aead.encrypt(content.toByteArray(), id.toByteArray())
        val encoder = Base64.getEncoder()
        val envelope = JsonObject().apply {
            addProperty("nonce", encoder.encodeToString(cipher.nonceCopy()))
            addProperty("ciphertext", encoder.encodeToString(cipher.ciphertextCopy()))
        }
        val file = file(id)
        val output = file.startWrite()
        try { output.write(envelope.toString().toByteArray()); file.finishWrite(output) }
        catch (failure: Throwable) { file.failWrite(output); throw failure }
    }
    private companion object { val LOCK = Any() }
}
