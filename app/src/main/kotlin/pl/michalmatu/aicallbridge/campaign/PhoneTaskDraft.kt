package pl.michalmatu.aicallbridge.campaign

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Encrypted private UI draft; not authority. Never put its contents into savedInstanceState. */
internal data class PhoneTaskDraft(
    val id: String = UUID.randomUUID().toString(),
    val step: Int = 0,
    val scenarioId: String = "custom",
    val personalScenarioId: String? = null,
    val title: String = "",
    val goal: String = "",
    val fields: Map<String, String> = emptyMap(),
    val suppliedBrief: String = "",
    val recipients: String = "",
    val subscriptionId: Int? = null,
    val accountScope: String? = null,
    val scheduled: Boolean = false,
    val startLocal: String = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString(),
    val endLocal: String = LocalDateTime.now().plusHours(2).withSecond(0).withNano(0).toString(),
    // Only stale defaults are refreshed. User-picked times are never rewritten.
    val startManuallySet: Boolean = false,
    val endManuallySet: Boolean = false,
    val zoneId: String = ZoneId.systemDefault().id,
    val retry: Boolean = true,
    val retryMinutes: String = "5",
    // Default on for NEW outgoing wizard drafts; explicit opt-out stays saved.
    val recordAudio: Boolean = true,
) {
    override fun toString() = "PhoneTaskDraft(id=$id,step=$step,content=REDACTED)"
}

/** Pure preview rules. Scheduling limits are still enforced by PhoneTask.validated(). */
internal object PhoneTaskScheduleUiPolicy {
    fun refreshDefaults(draft: PhoneTaskDraft, now: LocalDateTime): PhoneTaskDraft {
        var start = LocalDateTime.parse(draft.startLocal)
        var end = LocalDateTime.parse(draft.endLocal)
        if (draft.scheduled && !draft.startManuallySet && !start.isAfter(now)) {
            start = now.plusHours(1).withSecond(0).withNano(0)
        }
        if (!draft.endManuallySet &&
            (!end.isAfter(now) || (draft.scheduled && !end.isAfter(start)))) {
            end = if (draft.scheduled) start.plusHours(1) else now.plusHours(2)
            end = end.withSecond(0).withNano(0)
        }
        return if (start.toString() == draft.startLocal && end.toString() == draft.endLocal) draft
            else draft.copy(startLocal = start.toString(), endLocal = end.toString())
    }

    fun invalidOrder(scheduled: Boolean, start: LocalDateTime, end: LocalDateTime): Boolean =
        scheduled && !end.isAfter(start)

    fun expired(scheduled: Boolean, start: LocalDateTime, end: LocalDateTime,
        now: LocalDateTime): Boolean = !end.isAfter(now) || (scheduled && !start.isAfter(now))
}
