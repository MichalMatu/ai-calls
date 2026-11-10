package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/** A private editor prefill; never a CallTask, grant, SIM selection or dial request. */
internal data class AppointmentEditorDraft(
    val target: String,
    val goal: String,
    val from: ZonedDateTime,
    val until: ZonedDateTime,
    val terms: String,
) {
    companion object {
        private val DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        fun create(target: String, goal: String, from: String, until: String,
            terms: String): AppointmentEditorDraft? = runCatching {
            val number = target.trim()
            val purpose = goal.trim()
            val conditions = terms.trim()
            require(number.matches(Regex("\\+[1-9][0-9]{6,14}")))
            require(purpose.length in 5..280)
            require(conditions.length <= 500)
            val tz = ZoneId.of("Europe/Warsaw")
            val start = LocalDateTime.parse(from.trim(), DATE).atZone(tz)
            val end = LocalDateTime.parse(until.trim(), DATE).atZone(tz)
            require(start.toInstant() < end.toInstant())
            AppointmentEditorDraft(number, purpose, start, end, conditions)
        }.getOrNull()
        fun date(value: ZonedDateTime) = value.format(DATE)
    }
}

internal class AppointmentEditorDraftStore(context: Context) {
    private val dir = File(context.filesDir, "usb-lab-control")
    private val file = File(dir, "appointment-editor-draft.json")

    fun stage(draft: AppointmentEditorDraft): Boolean = runCatching {
        check(dir.isDirectory || dir.mkdirs())
        val data = JSONObject()
            .put("created_at_ms", System.currentTimeMillis())
            .put("target", draft.target).put("goal", draft.goal)
            .put("from", AppointmentEditorDraft.date(draft.from))
            .put("until", AppointmentEditorDraft.date(draft.until))
            .put("terms", draft.terms)
        val temp = File(dir, "appointment-editor-draft.json.new")
        temp.writeText(data.toString())
        check(temp.renameTo(file))
        true
    }.getOrDefault(false)

    /** One-shot and expires after 15 minutes; never reused as an authority. */
    fun consume(): AppointmentEditorDraft? {
        val raw = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
        file.delete()
        if (raw == null) return null
        return runCatching {
            val data = JSONObject(raw)
            val age = System.currentTimeMillis() - data.getLong("created_at_ms")
            if (age !in 0L..900_000L) return@runCatching null
            AppointmentEditorDraft.create(
                data.getString("target"), data.getString("goal"),
                data.getString("from"), data.getString("until"),
                data.getString("terms"))
        }.getOrNull()
    }
}
