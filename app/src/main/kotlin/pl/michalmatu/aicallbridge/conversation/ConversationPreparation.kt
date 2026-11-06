package pl.michalmatu.aicallbridge.conversation

import com.google.gson.Gson
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

internal enum class ConversationDirection { OUTGOING, INCOMING, UNSPECIFIED }

/** Prompt skills describe behaviour; they never confer tool, call or disclosure authority. */
internal enum class ConversationSkill(val title: String, val guidance: String) {
    EXPLAIN_GOAL("Jasne przedstawienie celu", "Przy pierwszej odpowiedzi jasno przedstaw konkretny cel dzwoniącego. Nie witaj się jak konsultant."),
    HANDLE_IVR("Rozmowa z automatem (IVR)", "Automat infolinii może pytać o kategorię sprawy. Odpowiadaj krótko i konkretnie, bez przedstawiania się jako konsultant."),
    ASK_PROCEDURE("Pytania o procedurę", "Jeśli brakuje instrukcji, zapytaj o kolejny krok niezbędny do wykonania dokładnego zadania."),
    CLARIFY("Wyjaśnianie nieporozumień", "Gdy rozmówca nie rozumie wypowiedzi, przeformułuj cel prościej. Nie powtarzaj bez końca tego samego zdania.");

    fun guidance(direction: ConversationDirection): String =
        if (this == EXPLAIN_GOAL && direction == ConversationDirection.INCOMING)
            "Powitanie aplikacji przedstawiło już rolę asystentki. Ustal konkretną sprawę osoby dzwoniącej bez ponownego przedstawiania się."
        else guidance
}

/** Prepared knowledge and references, kept separately from the transcript and task authority. */
internal class ConversationPreparation(
    val id: String,
    val title: String,
    val direction: ConversationDirection,
    val notes: String,
    skills: Set<ConversationSkill>,
    dataFields: Set<IdentityFieldId>,
    val skillSnapshot: ConversationSkillSnapshot? = null,
) {
    val skills = skills.toSet()
    val dataFields = dataFields.toSet()

    fun scopedDataFields(allowed: Set<IdentityFieldId>) = dataFields.intersect(allowed)

    fun withSettings(notes: String, skills: Set<ConversationSkill>, fields: Set<IdentityFieldId>) =
        ConversationPreparation(id, title, direction, notes, skills, fields, skillSnapshot)

    fun withSkillSnapshot(snapshot: ConversationSkillSnapshot) =
        ConversationPreparation(id, title, direction, notes, skills, dataFields, snapshot)

    override fun toString() = "ConversationPreparation(id=$id, direction=$direction, content=REDACTED)"

    companion object {
        const val OUTGOING_ID = "outgoing-clir"
        const val INCOMING_ID = "incoming-assistant"
        fun outgoing() = ConversationPreparation(OUTGOING_ID, "Rozmowa wychodząca — CLIR",
            ConversationDirection.OUTGOING, "", ConversationSkill.entries.toSet(), setOf(IdentityFieldId.PHONE))
        fun supervised() = ConversationPreparation("supervised-audio", "Test w trwającej rozmowie",
            ConversationDirection.UNSPECIFIED, "", emptySet(), emptySet())
        fun incoming() = ConversationPreparation(INCOMING_ID, "Asystent odbierający",
            ConversationDirection.INCOMING, "", setOf(ConversationSkill.EXPLAIN_GOAL, ConversationSkill.CLARIFY), emptySet())
    }
}

/** Storage is injected: Android supplies encrypted, no-backup records; host tests use memory. */
internal class ConversationPreparationRepository(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    private val gson = Gson()
    private data class Record(val id: String, val title: String, val direction: String,
        val notes: String, val skills: List<String>, val dataFields: List<String>)

    fun load(id: String): ConversationPreparation {
        val raw = read(id) ?: return when (id) {
            ConversationPreparation.OUTGOING_ID -> ConversationPreparation.outgoing()
            ConversationPreparation.INCOMING_ID -> ConversationPreparation.incoming()
            else -> throw NoSuchElementException("conversation_preparation_missing")
        }
        val record = gson.fromJson(raw, Record::class.java)
        require(record.id == id) { "conversation_preparation_id_mismatch" }
        return ConversationPreparation(record.id, record.title, ConversationDirection.valueOf(record.direction),
            record.notes, record.skills.map(ConversationSkill::valueOf).toSet(),
            record.dataFields.map(IdentityFieldId::valueOf).toSet())
    }

    fun save(value: ConversationPreparation) = write(value.id, gson.toJson(Record(
        value.id, value.title, value.direction.name, value.notes, value.skills.map { it.name }.sorted(),
        value.dataFields.map { it.name }.sorted())))
}
