package pl.michalmatu.aicallbridge.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class CallPlanBoundedFallbackTest {
    @Test
    public void unknownTranscriptRepeatsOnlyWithinConfiguredBoundThenTakesOver() {
        CallPlan plan = plan(CallPlanFallbackPolicy.repeatThenTakeOver(1));
        CallPlanEngine engine = new CallPlanEngine();

        assertEquals(CallPlanAction.ASK_REPEAT, engine.decide(plan, "nieznane pytanie", 0).action());
        assertEquals(CallPlanAction.TAKE_OVER, engine.decide(plan, "nieznane pytanie", 1).action());
        assertEquals(CallPlanAction.TAKE_OVER, engine.decide(plan, "nieznane pytanie", 2).action());
    }

    @Test
    public void configuredTwoRepeatsAllowExactlyTwoFallbackPrompts() {
        CallPlan plan = plan(CallPlanFallbackPolicy.repeatThenTakeOver(2));
        CallPlanEngine engine = new CallPlanEngine();

        assertEquals(CallPlanAction.ASK_REPEAT, engine.decide(plan, "nieznane", 0).action());
        assertEquals(CallPlanAction.ASK_REPEAT, engine.decide(plan, "nieznane", 1).action());
        assertEquals(CallPlanAction.TAKE_OVER, engine.decide(plan, "nieznane", 2).action());
    }

    @Test
    public void knownAuthorizedFactStillWinsAfterEarlierFallbackAttempts() {
        CallPlan plan = plan(CallPlanFallbackPolicy.repeatThenTakeOver(1));

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "jaki jest rok urodzenia", 7);

        assertEquals(CallPlanAction.SAY, decision.action());
        assertEquals("1990", decision.text());
    }

    @Test
    public void invalidFallbackCountersFailClosedAtApiBoundary() {
        CallPlan plan = plan(CallPlanFallbackPolicy.repeatThenTakeOver(1));

        assertThrows(IllegalArgumentException.class,
            () -> new CallPlanEngine().decide(plan, "nieznane", -1));
        assertThrows(IllegalArgumentException.class,
            () -> CallPlanFallbackPolicy.repeatThenTakeOver(-1));
    }

    @Test
    public void zeroRepeatPolicyTakesOverImmediately() {
        CallPlan plan = plan(CallPlanFallbackPolicy.takeOverImmediately());

        assertEquals(CallPlanAction.TAKE_OVER,
            new CallPlanEngine().decide(plan, "nieznane", 0).action());
    }

    private static CallPlan plan(CallPlanFallbackPolicy policy) {
        CallTask task = new CallTask(
            "Clinic A",
            "book",
            "consultation",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            Map.of("birth_year", "1990")
        );
        return new CallPlan(
            task,
            new CallResolvedTarget("Clinic A", "+48123456789"),
            List.of(new CallPlanRule("birth-year", Set.of("jaki jest rok urodzenia"), "birth_year")),
            policy
        );
    }
}
