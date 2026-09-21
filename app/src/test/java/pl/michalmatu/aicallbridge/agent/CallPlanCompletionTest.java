package pl.michalmatu.aicallbridge.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class CallPlanCompletionTest {
    @Test
    public void knownCompletionPhraseReturnsStructuredCompleteProposal() {
        CallOutcome outcome = success("Wizyta umówiona");
        CallPlan plan = plan(
            List.of(new CallPlanCompletionRule(
                "booking-confirmed",
                Set.of("wizyta została umówiona"),
                outcome
            )),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanDecision decision = new CallPlanEngine().decide(
            plan,
            "  Wizyta została umówiona. ",
            0
        );

        assertEquals(CallPlanAction.COMPLETE, decision.action());
        assertNull(decision.text());
        assertEquals("booking-confirmed", decision.ruleId());
        assertSame(outcome, decision.outcome());
    }

    @Test
    public void unknownCompletionTextUsesFallbackAndNeverFabricatesOutcome() {
        CallPlan plan = plan(
            List.of(new CallPlanCompletionRule(
                "booking-confirmed",
                Set.of("wizyta została umówiona"),
                success("Wizyta umówiona")
            )),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "może coś jeszcze", 0);

        assertEquals(CallPlanAction.ASK_REPEAT, decision.action());
        assertNull(decision.outcome());
    }

    @Test
    public void ambiguousCompletionTextFailsClosedInsteadOfChoosingOutcome() {
        CallPlan plan = plan(
            List.of(
                new CallPlanCompletionRule("success", Set.of("to wszystko"), success("Sukces")),
                new CallPlanCompletionRule("failure", Set.of("to wszystko"), failure("Brak terminu"))
            ),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "to wszystko", 0);

        assertEquals(CallPlanAction.TAKE_OVER, decision.action());
        assertNull(decision.outcome());
        assertNull(decision.ruleId());
    }

    @Test
    public void collisionBetweenFactAndCompletionRuleFailsClosed() {
        CallTask task = task();
        CallPlan plan = new CallPlan(
            task,
            new CallResolvedTarget("Clinic A", "+48123456789"),
            List.of(new CallPlanRule("fact", Set.of("gotowe"), "birth_year")),
            List.of(new CallPlanCompletionRule("complete", Set.of("gotowe"), success("Gotowe"))),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "gotowe", 0);

        assertEquals(CallPlanAction.TAKE_OVER, decision.action());
        assertNull(decision.text());
        assertNull(decision.outcome());
        assertNull(decision.ruleId());
    }

    @Test
    public void knownFactBehaviorRemainsSourceCompatibleWithCompletionRulesPresent() {
        CallPlan plan = plan(
            List.of(new CallPlanCompletionRule(
                "booking-confirmed",
                Set.of("wizyta została umówiona"),
                success("Wizyta umówiona")
            )),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "jaki jest rok urodzenia", 9);

        assertEquals(CallPlanAction.SAY, decision.action());
        assertEquals("1990", decision.text());
        assertNull(decision.outcome());
    }

    @Test
    public void completionCollectionsAreDefensivelyCopiedAndRenderingIsRedacted() {
        Set<String> utterances = new LinkedHashSet<>(Set.of("wizyta została umówiona"));
        CallOutcome secretOutcome = new CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "VERY_SECRET_SUMMARY",
            null,
            "Secret Provider",
            null,
            null,
            "SECRET_REFERENCE",
            null
        );
        CallPlanCompletionRule completion = new CallPlanCompletionRule(
            "booking-confirmed",
            utterances,
            secretOutcome
        );
        List<CallPlanCompletionRule> completions = new ArrayList<>(List.of(completion));
        CallPlan plan = plan(completions, CallPlanFallbackPolicy.repeatThenTakeOver(1));

        utterances.add("inna fraza");
        completions.clear();

        assertEquals(Set.of("wizyta została umówiona"), completion.utterances());
        assertEquals(List.of(completion), plan.completionRules());
        assertThrows(UnsupportedOperationException.class, () -> completion.utterances().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> plan.completionRules().clear());
        assertFalse(plan.toString().contains("VERY_SECRET_SUMMARY"));
        assertFalse(plan.toString().contains("SECRET_REFERENCE"));
        assertFalse(plan.toString().contains("Secret Provider"));
    }

    private static CallPlan plan(
        List<CallPlanCompletionRule> completionRules,
        CallPlanFallbackPolicy fallbackPolicy
    ) {
        return new CallPlan(
            task(),
            new CallResolvedTarget("Clinic A", "+48123456789"),
            List.of(new CallPlanRule("birth-year", Set.of("jaki jest rok urodzenia"), "birth_year")),
            completionRules,
            fallbackPolicy
        );
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

    private static CallOutcome success(String summary) {
        return new CallOutcome(CallOutcomeStatus.SUCCESS, summary, null, null, null, null, null, null);
    }

    private static CallOutcome failure(String summary) {
        return new CallOutcome(CallOutcomeStatus.FAILURE, summary, null, null, null, null, null, null);
    }
}
