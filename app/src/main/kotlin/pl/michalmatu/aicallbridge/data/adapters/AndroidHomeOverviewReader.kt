package pl.michalmatu.aicallbridge.data.adapters

import android.content.Context
import pl.michalmatu.aicallbridge.campaign.PhoneTaskWork
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.domain.models.HomeIncomingReadiness
import pl.michalmatu.aicallbridge.domain.models.HomeOverviewSnapshot
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsController
import pl.michalmatu.aicallbridge.session.GsmMediaTransportReadiness

/** Executes the previous Home snapshot reads on the caller's worker thread. */
internal object AndroidHomeOverviewReader {
    fun read(context: Context): HomeOverviewSnapshot {
        val result = runCatching {
            PhoneTaskWork.recover(context)
            PhoneTaskWork.store(context).phoneTasks().size
        }
        val notebookCount = runCatching {
            AndroidConversationPreparationStore(context.applicationContext).notebooks().size
        }
        val incoming = runCatching {
            val controller = IncomingAssistantSettingsController(context.applicationContext)
            val policy = controller.current().getOrThrow()
            val selectedSimPresent = policy.accountKey?.let { accountKey ->
                controller.selectedAccountIndex(controller.availableAccounts(), accountKey) >= 0
            } ?: false
            val hostReady = policy.mode == AnsweringMode.AI && selectedSimPresent &&
                GsmMediaTransportReadiness.isAvailable(context.applicationContext)
            HomeIncomingReadiness(policy.mode, selectedSimPresent, hostReady)
        }
        return HomeOverviewSnapshot(result, notebookCount, incoming)
    }
}
