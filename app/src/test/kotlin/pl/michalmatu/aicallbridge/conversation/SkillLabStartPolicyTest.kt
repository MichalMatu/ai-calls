package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillLabStartPolicyTest {
    @Test fun adaptiveRequiresCaseBeforeCheckingBudget() {
        assertEquals(SkillLabStartPolicy.Decision.CaseRequired,
            SkillLabStartPolicy.decide(true, false, "invalid", false))
    }

    @Test fun invalidTokenBudgetCannotStartModel() {
        for (value in listOf("abc", "0", "-1", "1.5", "2147483648", " -9 ")) {
            assertEquals(value, SkillLabStartPolicy.Decision.InvalidBudget,
                SkillLabStartPolicy.decide(false, false, value, false))
        }
    }

    @Test fun manualEmptyBudgetKeepsProfileDefaultAndAllSkills() {
        val result = SkillLabStartPolicy.decide(false, false, "  ", false)
            as SkillLabStartPolicy.Decision.Accepted
        assertNull(result.contextTokenBudget)
        assertEquals(SkillLoadingMode.ALL_ACTIVE, result.skillLoadingMode)
    }

    @Test fun adaptiveCaseWithValidBudgetSelectsContextualRouting() {
        val result = SkillLabStartPolicy.decide(true, true, " 4096 ", true)
            as SkillLabStartPolicy.Decision.Accepted
        assertEquals(4096, result.contextTokenBudget)
        assertEquals(SkillLoadingMode.CONTEXTUAL, result.skillLoadingMode)
    }
}
