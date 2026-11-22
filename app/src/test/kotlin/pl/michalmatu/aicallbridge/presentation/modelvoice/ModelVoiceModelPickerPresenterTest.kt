package pl.michalmatu.aicallbridge.presentation.modelvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

class ModelVoiceModelPickerPresenterTest {
    private val builtIn = listOf(
        DialogueModelId.GEMMA_4_E2B to "Gemma E2B",
        DialogueModelId.GEMMA_4_E4B to "Gemma E4B",
    )
    private val imported = listOf("user-alpha" to "Local A", "user-beta" to "Local B")
    private fun present(profile: AudioEngineProfile = AudioEngineProfile(),
        builtins: List<Pair<DialogueModelId, String>> = builtIn,
        imports: List<Pair<String, String>> = imported
    ) = ModelVoiceModelPickerPresenter.present(profile, builtins, imports)

    @Test fun builtInChoicesPrecedeQualifiedImportsInOriginalOrder() {
        val options = present()
        assertEquals(listOf("Gemma E2B", "Gemma E4B", "Local A", "Local B"),
            options.rows.map { it.title })
        assertEquals(0, options.selected)
        assertEquals(DialogueModelId.GEMMA_4_E2B, options.rows[0].dialogue)
        assertEquals(null, options.rows[0].customId)
    }

    @Test fun alternateBuiltInModelPreservesItsSelection() {
        assertEquals(1, present(AudioEngineProfile(dialogue = DialogueModelId.GEMMA_4_E4B)).selected)
    }

    @Test fun importedModelUsesStableIdRatherThanTitle() {
        val options = present(AudioEngineProfile(
            dialogue = DialogueModelId.USER_LITERT, customModelId = "user-beta"))
        assertEquals(3, options.selected)
        assertEquals(DialogueModelId.USER_LITERT, options.rows[3].dialogue)
        assertEquals("user-beta", options.rows[3].customId)
    }

    @Test fun unavailableImportedModelNeverSelectsAnotherModel() {
        val profile = AudioEngineProfile(dialogue = DialogueModelId.USER_LITERT,
            customModelId = "user-removed")
        assertEquals(-1, present(profile).selected)
        assertEquals(-1, present(profile, imports = emptyList()).selected)
    }

    @Test fun retiredUnlistedBuiltInModelIsNeverSilentlySelected() {
        val profile = AudioEngineProfile(dialogue = DialogueModelId.QWEN_3_0_6B)
        assertEquals(-1, present(profile).selected)
    }

    @Test fun emptyOptionSourcesStayEmptyWithoutSyntheticFallback() {
        val options = present(builtins = emptyList(), imports = emptyList())
        assertEquals(emptyList<ModelVoiceModelPickerPresenter.Choice>(), options.rows)
        assertEquals(-1, options.selected)
    }

    @Test fun duplicateDisplayTitlesCannotReplaceQualifiedModelIdentity() {
        val options = present(
            profile = AudioEngineProfile(dialogue = DialogueModelId.USER_LITERT,
                customModelId = "user-beta"),
            imports = listOf("user-alpha" to "Same", "user-beta" to "Same"))
        assertEquals(3, options.selected)
        assertEquals("user-beta", options.rows[3].customId)
    }

    @Test fun choicesNeverModifyCatalogOrImportedInputLists() {
        present()
        assertEquals(2, builtIn.size)
        assertEquals(2, imported.size)
        assertTrue(builtIn[0].first == DialogueModelId.GEMMA_4_E2B)
    }
}
