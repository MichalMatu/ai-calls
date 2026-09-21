package pl.michalmatu.aicallbridge.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class CallPlanHelperValidatorTest {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Test
    public void knownFactRuleSuggestionResolvesOnlyPlanOwnedFact() {
        CallPlan plan = plan(
            List.of(new CallPlanRule("birth-year", Set.of("jaki jest rok urodzenia"), "birth_year")),
            List.of(),
            List.of(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanDecision decision = new CallPlanHelperValidator().validate(
            plan,
            new CallPlanHelperSuggestion("birth-year"),
            0
        );

        assertEquals(CallPlanAction.SAY, decision.action());
        assertEquals("1990", decision.text());
        assertEquals("birth-year", decision.ruleId());
        assertNull(decision.outcome());
        assertNull(decision.proposal());
    }

    @Test
    public void knownCompletionAndProposalSuggestionsReturnOnlyPredeclaredPayloads() {
        CallOutcome outcome = new CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "Done",
            null,
            null,
            null,
            null,
            null,
            null
        );
        CallProposal proposal = new CallProposal(
            ZonedDateTime.of(2026, 9, 22, 14, 0, 0, 0, WARSAW),
            new MoneyAmount(new BigDecimal("120.00"), "PLN"),
            CallPaymentMode.PRIVATE,
            "Clinic A",
            "Wrocław"
        );
        CallPlan plan = plan(
            List.of(),
            List.of(new CallPlanCompletionRule("done", Set.of("gotowe"), outcome)),
            List.of(new CallPlanProposalRule("offer", Set.of("mamy ofertę"), proposal)),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanDecision completion = new CallPlanHelperValidator().validate(
            plan,
            new CallPlanHelperSuggestion("done"),
            0
        );
        CallPlanDecision offer = new CallPlanHelperValidator().validate(
            plan,
            new CallPlanHelperSuggestion("offer"),
            0
        );

        assertEquals(CallPlanAction.COMPLETE, completion.action());
        assertSame(outcome, completion.outcome());
        assertEquals(CallPlanAction.PROPOSAL, offer.action());
        assertSame(proposal, offer.proposal());
    }

    @Test
    public void unsupportedSuggestionFallsBackAndCannotCreateAuthority() {
        CallPlan plan = plan(
            List.of(new CallPlanRule("birth-year", Set.of("rok urodzenia"), "birth_year")),
            List.of(),
            List.of(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanDecision first = new CallPlanHelperValidator().validate(
            plan,
            new CallPlanHelperSuggestion("invented-action"),
            0
        );
        CallPlanDecision exhausted = new CallPlanHelperValidator().validate(
            plan,
            new CallPlanHelperSuggestion("invented-action"),
            1
        );

        assertEquals(CallPlanAction.ASK_REPEAT, first.action());
        assertNull(first.text());
        assertNull(first.outcome());
        assertNull(first.proposal());
        assertEquals(CallPlanAction.TAKE_OVER, exhausted.action());
        assertNull(exhausted.ruleId());
    }

    @Test
    public void duplicateRuleIdAcrossKindsFailsClosedInsteadOfChoosingPrecedence() {
        CallOutcome outcome = new CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "Done",
            null,
            null,
            null,
            null,
            null,
            null
        );
        CallPlan plan = plan(
            List.of(new CallPlanRule("shared", Set.of("fakt"), "birth_year")),
            List.of(new CallPlanCompletionRule("shared", Set.of("koniec"), outcome)),
            List.of(),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanDecision decision = new CallPlanHelperValidator().validate(
            plan,
            new CallPlanHelperSuggestion("shared"),
            0
        );

        assertEquals(CallPlanAction.TAKE_OVER, decision.action());
        assertNull(decision.ruleId());
        assertNull(decision.text());
        assertNull(decision.outcome());
        assertNull(decision.proposal());
    }

    @Test
    public void missingAuthorizedFactStillFailsClosedForKnownSuggestedRule() {
        CallTask task = new CallTask(
            "Clinic A",
            "book",
            "consultation",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            Map.of()
        );
        CallPlan plan = new CallPlan(
            task,
            target(),
            List.of(new CallPlanRule("birth-year", Set.of("rok urodzenia"), "birth_year")),
            List.of(),
            List.of(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanDecision decision = new CallPlanHelperValidator().validate(
            plan,
            new CallPlanHelperSuggestion("birth-year"),
            0
        );

        assertEquals(CallPlanAction.TAKE_OVER, decision.action());
        assertEquals("birth-year", decision.ruleId());
        assertNull(decision.text());
    }

    @Test
    public void suggestionAndRetryInputsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new CallPlanHelperSuggestion("   "));
        CallPlan plan = plan(List.of(), List.of(), List.of(), CallPlanFallbackPolicy.takeOverImmediately());
        assertThrows(
            IllegalArgumentException.class,
            () -> new CallPlanHelperValidator().validate(plan, new CallPlanHelperSuggestion("x"), -1)
        );
    }

    private static CallPlan plan(
        List<CallPlanRule> rules,
        List<CallPlanCompletionRule> completions,
        List<CallPlanProposalRule> proposals,
        CallPlanFallbackPolicy fallback
    ) {
        return new CallPlan(task(), target(), rules, completions, proposals, fallback);
    }

    private static CallTask task() {
        return new CallTask(
            "Clinic A",
            "book",
            "consultation",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            Map.of("birth_year", "1990")
        );
    }

    private static CallResolvedTarget target() {
        return new CallResolvedTarget("Clinic A", "+48123456789");
    }
}
