package pl.michalmatu.aicallbridge.presentation.tasks

import pl.michalmatu.aicallbridge.campaign.PhoneTask
import pl.michalmatu.aicallbridge.campaign.PhoneTaskDraft
import pl.michalmatu.aicallbridge.campaign.PhoneTaskManagement

/** Pure task-list projection; no telephony, persistence, navigation or side effects. */
internal object PhoneTaskListController {
    data class Section(
        val group: PhoneTaskManagement.Group,
        val tasks: List<PhoneTask>,
    )

    fun sections(tasks: List<PhoneTask>, now: Long): List<Section> =
        PhoneTaskManagement.Group.entries.mapNotNull { group ->
            val members = tasks.filter { PhoneTaskManagement.group(it, now) == group }
                .sortedByDescending { it.approvedAt }
            if (members.isEmpty()) null else Section(group, members)
        }

    /** Only a draft is copied; never carry granted call authority or attempts. */
    fun duplicateDraft(task: PhoneTask): PhoneTaskDraft = PhoneTaskDraft(
        scenarioId = task.scenarioId,
        title = task.title,
        goal = task.goal,
        suppliedBrief = task.brief,
        recipients = task.recipients.joinToString("\n") { it.name + "; " + it.number },
        subscriptionId = task.subscriptionId,
        accountScope = task.accountScope,
        recordAudio = task.recordAudio,
    )
}
