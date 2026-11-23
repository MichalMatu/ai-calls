package pl.michalmatu.aicallbridge.presentation.scenarios

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.conversationcore.ScenarioDraft

class ScenarioCallPreparationPolicyTest {
    private val definition = ScenarioCatalog.custom

    @Test fun missingRequiredFieldWinsOverInvalidNameAndGoal() {
        val result = ScenarioCallPreparationPolicy.prepare(
            definition, definition.draft(), "pl", "", "",
        )
        assertEquals(ScenarioCallPreparationPolicy.Result.Missing(
            listOf("additional_instructions")), result)
    }

    @Test fun preparedPayloadKeepsTrimmedUserGoalAndLocalizedFields() {
        val draft = ScenarioDraft(definition.id, mapOf("additional_instructions" to "  Ask for price  "))
        val result = ScenarioCallPreparationPolicy.prepare(
            definition, draft, "en", "  Service quote  ", "  Ask for details  ",
        ) as ScenarioCallPreparationPolicy.Result.Ready
        assertEquals("Service quote", result.title)
        assertEquals("Ask for details", result.goal)
        assertEquals("Ask for details\nAI instructions: Ask for price", result.brief)
    }

    @Test fun noLongOrBlankInstructionsCanBePrepared() {
        val draft = ScenarioDraft(definition.id, mapOf("additional_instructions" to "Help"))
        assertEquals(ScenarioCallPreparationPolicy.Result.Invalid,
            ScenarioCallPreparationPolicy.prepare(definition, draft, "pl", "A", "OK"))
        assertEquals(ScenarioCallPreparationPolicy.Result.Invalid,
            ScenarioCallPreparationPolicy.prepare(definition, draft, "pl", "x".repeat(141), "Help"))
        assertEquals(ScenarioCallPreparationPolicy.Result.Invalid,
            ScenarioCallPreparationPolicy.prepare(definition, draft, "pl", "Valid", "x".repeat(301)))
    }
}
