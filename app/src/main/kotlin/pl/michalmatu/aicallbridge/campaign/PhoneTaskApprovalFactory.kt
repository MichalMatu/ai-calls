package pl.michalmatu.aicallbridge.campaign

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure campaign approval snapshot. Never starts a Call, grants dialer authority
 * or persists a task; the Activity must check permissions/SIM/schedule first.
 */
internal object PhoneTaskApprovalFactory {
    fun build(
        draft: PhoneTaskDraft,
        brief: String,
        now: Long,
        speechLanguage: String? = null,
    ): PhoneTask {
        val zone = ZoneId.of(draft.zoneId)
        val start = if (draft.scheduled) PhoneTaskInput.instant(
            LocalDateTime.parse(draft.startLocal), zone,
        ) else now
        require(!draft.scheduled || start > now)
        val end = PhoneTaskInput.instant(LocalDateTime.parse(draft.endLocal), zone)
        val delay = if (draft.retry) Math.multiplyExact(
            draft.retryMinutes.toLong(), 60_000L,
        ).also { require(it > 0) } else null
        return PhoneTask(
            draft.id, draft.title, draft.scenarioId, draft.goal, brief,
            requireNotNull(draft.subscriptionId), requireNotNull(draft.accountScope), start, end,
            draft.zoneId, delay, now, PhoneTaskInput.recipients(draft.recipients).values,
            language = speechLanguage,
            recordAudio = draft.recordAudio,
        ).validated()
    }
}
