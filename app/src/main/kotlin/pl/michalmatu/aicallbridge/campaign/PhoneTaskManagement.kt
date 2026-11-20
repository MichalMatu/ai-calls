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

    /** Reverses only an explicit skip, not an answered, unknown or in-flight attempt. */
    fun canRestore(task: PhoneTask, recipientId: String, now: Long): Boolean =
        task.state in setOf(PhoneTaskState.READY, PhoneTaskState.PAUSED, PhoneTaskState.FINISHED) &&
        now < task.endAt &&
        task.recipients.any { it.id == recipientId &&
            it.state == PhoneRecipientState.SKIPPED &&
            it.attempts.none { attempt -> attempt.endedAt == null } }

    /** A finished task reopens PAUSED, so restoring the last skip never dials by itself. */
    fun restoreSkipped(task: PhoneTask, recipientId: String, now: Long): PhoneTask {
        require(canRestore(task, recipientId, now)) { "recipient_not_restorable" }
        return task.copy(
            state = if (task.state == PhoneTaskState.FINISHED) PhoneTaskState.PAUSED else task.state,
            recipients = task.recipients.map {
                if (it.id == recipientId && it.state == PhoneRecipientState.SKIPPED)
                    it.copy(state = PhoneRecipientState.QUEUED, nextAt = maxOf(now, task.startAt, it.nextAt))
                else it
            },
        ).validated()
    }

    fun skipQueued(task: PhoneTask, recipientId: String): PhoneTask {
        if (task.state !in setOf(PhoneTaskState.READY, PhoneTaskState.PAUSED)) return task
        return task.copy(recipients = task.recipients.map {
            if (it.id == recipientId && it.state == PhoneRecipientState.QUEUED)
                it.copy(state = PhoneRecipientState.SKIPPED) else it
        }).validated()
    }

    fun hasComparison(task: PhoneTask): Boolean = task.recipients.size > 1 &&
        task.recipients.any { recipient -> recipient.attempts.any { it.answers.isNotEmpty() } }

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
