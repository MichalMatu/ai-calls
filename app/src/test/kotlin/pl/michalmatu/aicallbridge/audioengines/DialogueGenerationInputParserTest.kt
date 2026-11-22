package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mirrors inline parsing rules from the previous generation-settings dialog. */
class DialogueGenerationInputParserTest {
    private fun parse(
        context: String = "", output: String = "",
        temperature: String = "0.0", topK: String = "1", topP: String = "1.0",
    ) = DialogueGenerationInputParser.parse(context, output, temperature, topK, topP)

    @Test fun blankBudgetsPreserveAutomaticDefaults() {
        assertEquals(DialogueGenerationSettings.DEFAULTS, parse(context = "  ", output = " "))
    }

    @Test fun boundariesAndCommaDecimalsAreAccepted() {
        assertEquals(DialogueGenerationSettings(2048, 1024, 1.5, 64, 0.05),
            parse(" 2048 ", "1024", "1,5", "64", "0,05"))
        assertEquals(DialogueGenerationSettings(8192, 256, 0.0, 1, 1.0),
            parse("8192", " 256 ", "0", "1", "1"))
    }

    @Test fun invalidNumericTokensAndOutOfRangeAreRejected() {
        assertNull(parse(context = "n/a"))
        assertNull(parse(output = "12x"))
        assertNull(parse(context = "2047"))
        assertNull(parse(output = "1025"))
        assertNull(parse(temperature = "NaN"))
        assertNull(parse(temperature = "1,6"))
        assertNull(parse(topK = "0"))
        assertNull(parse(topP = "0"))
        assertNull(parse(topP = "bad"))
    }

    @Test fun integerFieldsDoNotGainNewImplicitTrimming() {
        assertNull(parse(topK = " 1 "))
        assertNull(parse(topK = ""))
    }
}
