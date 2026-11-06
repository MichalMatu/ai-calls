package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.util.AtomicFile
import com.google.gson.JsonObject
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
            stored.withSkillSnapshot(skillsRepository.load().snapshot())
        else stored
    }
    fun save(value: ConversationPreparation) = synchronized(LOCK) { repository.save(value) }
    fun loadSkillLibrary() = synchronized(LOCK) { skillsRepository.load() }
    fun saveSkillLibrary(value: ConversationSkillLibrary) = synchronized(LOCK) { skillsRepository.save(value) }

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
