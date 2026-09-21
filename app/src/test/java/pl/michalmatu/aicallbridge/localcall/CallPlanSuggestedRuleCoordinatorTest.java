package pl.michalmatu.aicallbridge.localcall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy;
import pl.michalmatu.aicallbridge.agent.CallConstraints;
import pl.michalmatu.aicallbridge.agent.CallPaymentMode;
import pl.michalmatu.aicallbridge.agent.CallPlan;
import pl.michalmatu.aicallbridge.agent.CallPlanAction;
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy;
import pl.michalmatu.aicallbridge.agent.CallPlanProposalRule;
import pl.michalmatu.aicallbridge.agent.CallPlanRule;
import pl.michalmatu.aicallbridge.agent.CallPolicyAction;
import pl.michalmatu.aicallbridge.agent.CallPreferences;
import pl.michalmatu.aicallbridge.agent.CallProposal;
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget;
import pl.michalmatu.aicallbridge.agent.CallTask;
import pl.michalmatu.aicallbridge.agent.CallWorkflow;
import pl.michalmatu.aicallbridge.agent.CallWorkflowState;
import pl.michalmatu.aicallbridge.agent.MoneyAmount;

public final class CallPlanSuggestedRuleCoordinatorTest {
    @Test
    public void knownSuggestedFactUsesPlanOwnedValueWithoutTranscriptRematching() {
        CallTask task = task(CallConstraints.unconstrained(), Map.of("birth_year", "1990"));
        CallResolvedTarget target = target();
        CallWorkflow workflow = activeWorkflow(task, target);
        CallPlan plan = new CallPlan(
            task,
            target,
            List.of(new CallPlanRule("birth-year", Set.of("jaki jest rok urodzenia"), "birth_year")),
            List.of(),
            List.of(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanTurnResult result = new CallPlanTurnCoordinator(plan, workflow)
            .handleSuggestedRuleId("birth-year", 0);

        assertEquals(CallPlanAction.SAY, result.decision().action());
        assertEquals("1990", result.decision().text());
        assertEquals("birth-year", result.decision().ruleId());
        assertNull(result.policyDecision());
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state());
    }

    @Test
    public void suggestedProposalStillRoutesThroughExistingWorkflowPolicy() {
        CallTask task = task(
            new CallConstraints(
                List.of(),
                new MoneyAmount(new BigDecimal("100.00"), "PLN"),
                java.util.EnumSet.noneOf(CallPaymentMode.class)
            ),
            Map.of()
        );
        CallResolvedTarget target = target();
        CallWorkflow workflow = activeWorkflow(task, target);
        CallProposal proposal = new CallProposal(
            null,
            new MoneyAmount(new BigDecimal("120.00"), "PLN"),
            CallPaymentMode.PRIVATE,
            "Clinic A",
            "Wrocław"
        );
        CallPlan plan = new CallPlan(
            task,
            target,
            List.of(),
            List.of(),
            List.of(new CallPlanProposalRule("offer", Set.of("oferta"), proposal)),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanTurnResult result = new CallPlanTurnCoordinator(plan, workflow)
            .handleSuggestedRuleId("offer", 0);

        assertEquals(CallPlanAction.PROPOSAL, result.decision().action());
        assertSame(proposal, result.decision().proposal());
        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, result.policyDecision().action());
        assertSame(proposal, workflow.snapshot().pendingProposal());
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state());
    }

    @Test
    public void unknownSuggestedRuleUsesBoundedPlanFallbackWithoutCreatingAuthority() {
        CallTask task = task(CallConstraints.unconstrained(), Map.of("birth_year", "1990"));
        CallResolvedTarget target = target();
        CallWorkflow workflow = activeWorkflow(task, target);
        CallPlan plan = new CallPlan(
            task,
            target,
            List.of(new CallPlanRule("birth-year", Set.of("rok urodzenia"), "birth_year")),
            List.of(),
            List.of(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );
        CallPlanTurnCoordinator coordinator = new CallPlanTurnCoordinator(plan, workflow);

        CallPlanTurnResult first = coordinator.handleSuggestedRuleId("invented-rule", 0);
        CallPlanTurnResult exhausted = coordinator.handleSuggestedRuleId("invented-rule", 1);

        assertEquals(CallPlanAction.ASK_REPEAT, first.decision().action());
        assertNull(first.decision().ruleId());
        assertNull(first.decision().text());
        assertEquals(CallPlanAction.TAKE_OVER, exhausted.decision().action());
        assertNull(exhausted.decision().ruleId());
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state());
    }

    private static CallTask task(CallConstraints constraints, Map<String, String> facts) {
        return new CallTask(
            "Clinic A",
            "book",
            "consultation",
            constraints,
            CallPreferences.none(),
            facts
        );
    }

    private static CallResolvedTarget target() {
        return new CallResolvedTarget("Clinic A", "+48123456789");
    }

    private static CallWorkflow activeWorkflow(CallTask task, CallResolvedTarget target) {
        CallWorkflow workflow = new CallWorkflow(task, new CallConfirmationPolicy(), snapshot -> { });
        workflow.resolveTarget(target);
        workflow.markDialing();
        workflow.markCallActive();
        return workflow;
    }
}
