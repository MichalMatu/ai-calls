package pl.michalmatu.aicallbridge.campaign

/** Presentation and guarded removal; independent from Telecom, call archive and note storage. */
internal object PhoneTaskManagement {
    enum class Group { CURRENT, SCHEDULED, HISTORY }

    fun group(task: PhoneTask, now: Long): Group = when {
        task.state == PhoneTaskState.CANCELLED || task.state == PhoneTaskState.FINISHED -> Group.HISTORY
        task.state == PhoneTaskState.READY && task.startAt > now &&
            task.recipients.none { it.state == PhoneRecipientState.IN_FLIGHT } -> Group.SCHEDULED
        else -> Group.CURRENT // ready, due or paused
    }

    fun canDelete(task: PhoneTask, activeTaskId: String?): Boolean =
        task.state in setOf(PhoneTaskState.CANCELLED, PhoneTaskState.FINISHED) &&
        task.id != activeTaskId &&
        task.recipients.none { recipient ->
            recipient.state == PhoneRecipientState.IN_FLIGHT ||
                recipient.attempts.any { it.endedAt == null }
        }

    /** Fail closed if the task changed, was already removed or owns any active attempt. */
    fun removeTerminal(tasks: List<PhoneTask>, id: String, activeTaskId: String?): List<PhoneTask> {
        val existing = requireNotNull(tasks.singleOrNull { it.id == id }) { "task_not_found" }
        check(canDelete(existing, activeTaskId)) { "task_not_deletable" }
        return tasks.filterNot { it.id == id }
    }
}
