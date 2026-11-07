package pl.michalmatu.aicallbridge.textagent

import java.util.Collections
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

/** Read-only model selection and transfer presentation; no Android context, service or IO. */
internal enum class LanguageModelMeaning { ACTIVE, READY, NOT_INSTALLED }

internal data class LanguageModelRowState(
    val id: DialogueModelId,
    val meaning: LanguageModelMeaning,
    val active: Boolean,
    val showDownload: Boolean,
    val showImport: Boolean,
    val showSelect: Boolean,
    val actionsEnabled: Boolean,
    val showProgress: Boolean,
    val progressPercent: Int,
)

internal data class LanguageModelScreenState(
    val rows: List<LanguageModelRowState>,
    val showCancel: Boolean,
    /** Only fresh, non-empty service messages change the existing Activity feedback label. */
    val newMessage: String?,
)

/**
 * The transfer service remains the only source of transfer state; the profile store
 * remains the only selection authority; readiness probes remain Android-owned.
 * A selected-but-missing model is not presented as active or ready.
 */
internal object LanguageModelStatusPresenter {
    fun present(
        entries: List<LanguageModelEntry>,
        selected: DialogueModelId?,
        ready: Set<DialogueModelId>,
        transfer: TransferStatus,
        previous: TransferStatus?,
    ): LanguageModelScreenState {
        val rows = entries.map { entry ->
            val installed = entry.id in ready
            val active = installed && selected == entry.id
            val meaning = when {
                active -> LanguageModelMeaning.ACTIVE
                installed -> LanguageModelMeaning.READY
                else -> LanguageModelMeaning.NOT_INSTALLED
            }
            val bytesTotal = checkNotNull(entry.spec.expectedBytes).coerceAtLeast(1L)
            val progress = if (transfer.id == entry.id) {
                (transfer.bytes.coerceAtLeast(0L).toDouble() * 100.0 /
                    bytesTotal.toDouble()).toInt().coerceIn(0, 100)
            } else 0
            LanguageModelRowState(
                id = entry.id,
                meaning = meaning,
                active = active,
                showDownload = !installed,
                showImport = !installed,
                showSelect = installed && selected != entry.id,
                actionsEnabled = !transfer.busy,
                showProgress = transfer.busy && transfer.id == entry.id,
                progressPercent = progress,
            )
        }
        return LanguageModelScreenState(
            rows = Collections.unmodifiableList(rows),
            showCancel = transfer.busy,
            newMessage = transfer.message.takeIf { it.isNotEmpty() && transfer != previous },
        )
    }
}
