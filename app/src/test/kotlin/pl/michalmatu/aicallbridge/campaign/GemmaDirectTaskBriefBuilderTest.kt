package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.conversation.*

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaDirectTaskBriefBuilderTest {
    private val now = 1_800_000_000_000L
    private val secretScope = "secret-phone-account-scope"
    private val grant = ClirCampaignSpec.acceptEnableTask(
        accountScope = secretScope, nowEpochMs = now, grantId = "secret-grant-id",
    )
    private val request = ClirCampaignSpec.enableRequest(secretScope)

    @Test fun roleIsCallerNotCustomerSupportAndGoalIsTypedClir() {
        val brief = GemmaDirectTaskBriefBuilder.build(
            request, grant, ConversationPreparation.outgoing(),
        )
        assertTrue(brief.contains("asystentem DZWONIĄCYM"))
        assertTrue(brief.contains("Nie jesteś konsultantem"))
        assertTrue(brief.contains("CLIR"))
        assertTrue(brief.contains("włączenie"))
        assertTrue(brief.contains("Bez JSON"))
        assertTrue(brief.contains("Rozmowa z automatem (IVR)"))
        assertTrue(brief.contains("Wyjaśnianie nieporozumień"))
        assertFalse(brief.contains("secret-phone-account-scope"))
        assertFalse(brief.contains("secret-grant-id"))
        assertFalse(brief.contains(ClirCampaignSpec.TARGET))
        assertFalse(brief.contains("issuerEvidenceRef"))
    }

    @Test fun togglingPromptSkillsChangesOnlyInstructionsAndNotTheTaskOrGrant() {
        val all = GemmaDirectTaskBriefBuilder.build(request, grant,
            ConversationPreparation.outgoing())
        val onlyGoal = GemmaDirectTaskBriefBuilder.build(request, grant,
            ConversationPreparation.outgoing().withSettings("", setOf(ConversationSkill.EXPLAIN_GOAL), emptySet()))
        assertNotEquals(all, onlyGoal)
        assertTrue(onlyGoal.contains("CLIR"))
        assertTrue(onlyGoal.contains("asystentem DZWONIĄCYM"))
        assertFalse(onlyGoal.contains("Rozmowa z automatem (IVR)"))
        assertTrue(onlyGoal.contains("Jasne przedstawienie celu"))
        val noSkills = GemmaDirectTaskBriefBuilder.build(request, grant, ConversationPreparation.outgoing().withSettings("", emptySet(), emptySet()))
        assertTrue(noSkills.contains("Brak dodatkowych instrukcji dialogowych"))
        assertTrue(noSkills.contains("CLIR"))
    }

    @Test fun wideningTargetOrEffectOrDisclosuresCannotCreateTaskBrief() {
        val mismatched = listOf(
            request.copy(target = "unapproved-target"),
            request.copy(enabled = false),
            request.copy(action = "BUY_OTHER_SERVICE"),
            request.copy(accountScope = "other-account"),
            request.copy(disclosureFields = emptySet()),
        )
        for (candidate in mismatched.dropLast(1)) {
            val failed = runCatching {
                GemmaDirectTaskBriefBuilder.build(candidate, grant,
                    ConversationPreparation.outgoing())
            }.isFailure
            assertTrue("Unexpected widened scope", failed)
        }
        val noDisclosure = GemmaDirectTaskBriefBuilder.build(
            mismatched.last(), grant, ConversationPreparation.outgoing(),
        )
        assertTrue(noDisclosure.contains("Nie otrzymujesz numeru telefonu"))
    }

    @Test fun acceptedTaskPersistsAndRevocationFailsClosed() {
        val continued = runCatching {
            GemmaDirectTaskBriefBuilder.build(request, grant,
                ConversationPreparation.outgoing())
        }
        val revoked = runCatching {
            GemmaDirectTaskBriefBuilder.build(request,
                grant.copy(revokedAtEpochMs = now + 10),
                ConversationPreparation.outgoing())
        }
        assertTrue(continued.isSuccess)
        assertTrue(revoked.isFailure)
    }
}
