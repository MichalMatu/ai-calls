package pl.michalmatu.aicallbridge.presentation.modelvoice

import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

/** Read-only list of qualified model choices. File qualification remains with the owner. */
internal object ModelVoiceModelPickerPresenter {
    data class Choice(val dialogue: DialogueModelId, val customId: String?, val title: String)
    data class Options(val rows: List<Choice>, val selected: Int)

    fun present(
        draft: AudioEngineProfile,
        builtIn: List<Pair<DialogueModelId, String>>,
        imported: List<Pair<String, String>>,
    ): Options {
        val rows = builtIn.map { (id, title) -> Choice(id, null, title) } +
            imported.map { (id, title) -> Choice(DialogueModelId.USER_LITERT, id, title) }
        val selected = if (draft.dialogue == DialogueModelId.USER_LITERT) {
            imported.indexOfFirst { it.first == draft.customModelId }
                .takeIf { it >= 0 }?.plus(builtIn.size) ?: -1
        } else {
            builtIn.indexOfFirst { it.first == draft.dialogue }
        }
        return Options(rows, selected)
    }
}
