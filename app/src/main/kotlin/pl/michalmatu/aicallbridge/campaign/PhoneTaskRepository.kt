package pl.michalmatu.aicallbridge.campaign

import com.google.gson.Gson

/** Uses the preparation store's existing Keystore + AtomicFile, under its shared lock. */
internal class PhoneTaskRepository(private val read: () -> String?, private val write: (String) -> Unit) {
    private data class Record(val schema: Int = 1, val tasks: List<PhoneTask> = emptyList())
    private val gson = Gson()

    fun load(): List<PhoneTask> {
        val raw = read() ?: return emptyList()
        require(raw.toByteArray().size <= MAX_BYTES) { "task_storage_full" }
        val record = requireNotNull(gson.fromJson(raw, Record::class.java))
        require(record.schema == 1) { "task_schema_unknown" }
        return validate(record.tasks)
    }
    fun update(change: (List<PhoneTask>) -> List<PhoneTask>): List<PhoneTask> {
        val previous = load()
        val next = validate(change(previous))
        if (previous != next) {
            val raw = gson.toJson(Record(tasks = next))
            require(raw.toByteArray().size <= MAX_BYTES) { "task_storage_full" }
            write(raw) // Fail closed: no returned claim until the reservation is durable.
        }
        return next
    }
    private fun validate(tasks: List<PhoneTask>): List<PhoneTask> {
        require(tasks.map { it.id }.distinct().size == tasks.size)
        tasks.forEach { it.validated() }
        val attempts = tasks.flatMap { it.recipients }.flatMap { it.attempts }.map { it.id }
        require(attempts.distinct().size == attempts.size)
        require(tasks.flatMap { it.recipients }.count { it.state == PhoneRecipientState.IN_FLIGHT } <= 1)
        return tasks
    }
    private companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
}
