package pl.michalmatu.aicallbridge.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class CallPlanTest {
    @Test
    public void knownQuestionReturnsExactlyAuthorizedFactValue() {
        CallTask task = task(Map.of("birth_year", "1990"));
        CallResolvedTarget target = new CallResolvedTarget("Clinic A", "+48123456789");
        CallPlan plan = new CallPlan(
            task,
            target,
            List.of(new CallPlanRule("birth-year", Set.of("jaki jest rok urodzenia"), "birth_year")),
            CallPlanFallback.ASK_REPEAT
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "  Jaki jest rok urodzenia? ");

        assertEquals(CallPlanAction.SAY, decision.action());
        assertEquals("1990", decision.text());
        assertEquals("birth-year", decision.ruleId());
        assertSame(task, plan.task());
        assertSame(target, plan.resolvedTarget());
    }

    @Test
    public void referencedMissingFactFailsClosedWithoutInventingValue() {
        CallPlan plan = new CallPlan(
            task(Map.of()),
            new CallResolvedTarget("Clinic A", "+48123456789"),
            List.of(new CallPlanRule("birth-year", Set.of("jaki jest rok urodzenia"), "birth_year")),
            CallPlanFallback.ASK_REPEAT
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "jaki jest rok urodzenia");

        assertEquals(CallPlanAction.TAKE_OVER, decision.action());
        assertEquals(null, decision.text());
        assertEquals("birth-year", decision.ruleId());
    }

    @Test
    public void unknownTranscriptUsesBoundedFallbackAndNeverGuesses() {
        CallPlan plan = new CallPlan(
            task(Map.of("birth_year", "1990")),
            new CallResolvedTarget("Clinic A", "+48123456789"),
            List.of(new CallPlanRule("birth-year", Set.of("jaki jest rok urodzenia"), "birth_year")),
            CallPlanFallback.ASK_REPEAT
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "proszę powiedzieć coś jeszcze");

        assertEquals(CallPlanAction.ASK_REPEAT, decision.action());
        assertEquals(null, decision.text());
        assertEquals(null, decision.ruleId());
    }

    @Test
    public void ambiguousTranscriptUsesFallbackInsteadOfChoosingAuthority() {
        CallPlan plan = new CallPlan(
            task(Map.of("a", "A", "b", "B")),
            new CallResolvedTarget("Clinic A", "+48123456789"),
            List.of(
                new CallPlanRule("rule-a", Set.of("podaj dane"), "a"),
                new CallPlanRule("rule-b", Set.of("podaj dane"), "b")
            ),
            CallPlanFallback.TAKE_OVER
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "podaj dane");

        assertEquals(CallPlanAction.TAKE_OVER, decision.action());
        assertEquals(null, decision.text());
        assertEquals(null, decision.ruleId());
    }

    @Test
    public void callerCollectionsAreDefensivelyCopiedAndImmutable() {
        Set<String> utterances = new LinkedHashSet<>(Set.of("jaki jest rok urodzenia"));
        CallPlanRule rule = new CallPlanRule("birth-year", utterances, "birth_year");
        List<CallPlanRule> rules = new ArrayList<>(List.of(rule));
        CallPlan plan = new CallPlan(
            task(Map.of("birth_year", "1990")),
            new CallResolvedTarget("Clinic A", "+48123456789"),
            rules,
            CallPlanFallback.ASK_REPEAT
        );

        utterances.add("inna fraza");
        rules.clear();

        assertEquals(Set.of("jaki jest rok urodzenia"), rule.utterances());
        assertEquals(List.of(rule), plan.rules());
        assertThrows(UnsupportedOperationException.class, () -> rule.utterances().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> plan.rules().clear());
    }

    @Test
    public void ordinaryRenderingRedactsTaskFactsAndDialTarget() {
        CallPlan plan = new CallPlan(
            task(Map.of("secret_fact", "VERY_SECRET_VALUE")),
            new CallResolvedTarget("Secret Clinic", "+48111222333"),
            List.of(new CallPlanRule("secret", Set.of("podaj sekret"), "secret_fact")),
            CallPlanFallback.ASK_REPEAT
        );

        String rendered = plan.toString();

        assertTrue(rendered.contains("REDACTED"));
        assertFalse(rendered.contains("VERY_SECRET_VALUE"));
        assertFalse(rendered.contains("+48111222333"));
        assertFalse(rendered.contains("Secret Clinic"));
    }

    private static CallTask task(Map<String, String> facts) {
        return new CallTask(
            "Clinic A",
            "book",
            "consultation",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            facts
        );
    }
}
