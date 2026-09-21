package pl.michalmatu.aicallbridge.localcall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy;
import pl.michalmatu.aicallbridge.agent.CallConstraints;
import pl.michalmatu.aicallbridge.agent.CallOutcome;
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus;
import pl.michalmatu.aicallbridge.agent.CallPaymentMode;
import pl.michalmatu.aicallbridge.agent.CallPlan;
import pl.michalmatu.aicallbridge.agent.CallPlanAction;
import pl.michalmatu.aicallbridge.agent.CallPlanCompletionRule;
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy;
import pl.michalmatu.aicallbridge.agent.CallPlanProposalRule;
import pl.michalmatu.aicallbridge.agent.CallPlanRule;
import pl.michalmatu.aicallbridge.agent.CallPolicyAction;
import pl.michalmatu.aicallbridge.agent.CallPolicyReason;
import pl.michalmatu.aicallbridge.agent.CallPreferences;
import pl.michalmatu.aicallbridge.agent.CallProposal;
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget;
import pl.michalmatu.aicallbridge.agent.CallTask;
import pl.michalmatu.aicallbridge.agent.CallWorkflow;
import pl.michalmatu.aicallbridge.agent.CallWorkflowState;
import pl.michalmatu.aicallbridge.agent.MoneyAmount;

public final class CallPlanTurnCoordinatorTest {
    @Test
    public void sayDecisionIsReturnedWithoutWorkflowMutationOrPolicyDecision() {
        CallTask task = task(CallConstraints.unconstrained(), Map.of("birth_year", "1990"));
        CallResolvedTarget target = target("Clinic A", "+48123456789");
        CallWorkflow workflow = activeWorkflow(task, target);
        CallPlan plan = new CallPlan(
            task,
            target,
            List.of(new CallPlanRule("birth-year", Set.of("rok urodzenia"), "birth_year")),
            List.of(),
            List.of(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanTurnResult result = new CallPlanTurnCoordinator(plan, workflow)
            .handleFinalTranscript("rok urodzenia", 0);

        assertEquals(CallPlanAction.SAY, result.decision().action());
        assertEquals("1990", result.decision().text());
        assertNull(result.policyDecision());
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state());
        assertNull(workflow.snapshot().pendingProposal());
        assertNull(workflow.snapshot().outcome());
    }

    @Test
    public void proposalRoutesExactPayloadThroughExistingWorkflowPolicy() {
        CallTask task = task(
            new CallConstraints(
                List.of(),
                new MoneyAmount(new BigDecimal("100.00"), "PLN"),
                java.util.EnumSet.noneOf(CallPaymentMode.class)
            ),
            Map.of()
        );
        CallResolvedTarget target = target("Clinic A", "+48123456789");
        CallWorkflow workflow = activeWorkflow(task, target);
        CallProposal expensive = new CallProposal(
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
            List.of(new CallPlanProposalRule("offer", Set.of("oferta 120 zł"), expensive)),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanTurnResult result = new CallPlanTurnCoordinator(plan, workflow)
            .handleFinalTranscript("oferta 120 zł", 0);

        assertEquals(CallPlanAction.PROPOSAL, result.decision().action());
        assertSame(expensive, result.decision().proposal());
        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, result.policyDecision().action());
        assertEquals(List.of(CallPolicyReason.PRICE_ABOVE_MAX), result.policyDecision().reasons());
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state());
        assertSame(expensive, workflow.snapshot().pendingProposal());
    }

    @Test
    public void completionRoutesExactOutcomeThroughWorkflowTerminalOwner() {
        CallTask task = task(CallConstraints.unconstrained(), Map.of());
        CallResolvedTarget target = target("Clinic A", "+48123456789");
        CallWorkflow workflow = activeWorkflow(task, target);
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
        CallPlan plan = new CallPlan(
            task,
            target,
            List.of(),
            List.of(new CallPlanCompletionRule("done", Set.of("gotowe"), outcome)),
            List.of(),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanTurnResult result = new CallPlanTurnCoordinator(plan, workflow)
            .handleFinalTranscript("gotowe", 0);

        assertEquals(CallPlanAction.COMPLETE, result.decision().action());
        assertSame(outcome, result.decision().outcome());
        assertNull(result.policyDecision());
        assertEquals(CallWorkflowState.COMPLETED, workflow.snapshot().state());
        assertSame(outcome, workflow.snapshot().outcome());
    }

    @Test
    public void fallbackDecisionsRemainStructuredWithoutWorkflowMutation() {
        CallTask task = task(CallConstraints.unconstrained(), Map.of());
        CallResolvedTarget target = target("Clinic A", "+48123456789");
        CallWorkflow workflow = activeWorkflow(task, target);
        CallPlan plan = new CallPlan(
            task,
            target,
            List.of(),
            List.of(),
            List.of(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );
        CallPlanTurnCoordinator coordinator = new CallPlanTurnCoordinator(plan, workflow);

        CallPlanTurnResult repeat = coordinator.handleFinalTranscript("nieznane", 0);
        CallPlanTurnResult takeOver = coordinator.handleFinalTranscript("nieznane", 1);

        assertEquals(CallPlanAction.ASK_REPEAT, repeat.decision().action());
        assertEquals(CallPlanAction.TAKE_OVER, takeOver.decision().action());
        assertNull(repeat.policyDecision());
        assertNull(takeOver.policyDecision());
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state());
    }

    @Test
    public void targetMismatchFailsBeforeAnyProposalWorkflowMutation() {
        CallTask task = task(CallConstraints.unconstrained(), Map.of());
        CallResolvedTarget workflowTarget = target("Clinic A", "+48111111111");
        CallResolvedTarget planTarget = target("Clinic B", "+48222222222");
        CallWorkflow workflow = activeWorkflow(task, workflowTarget);
        CallProposal proposal = new CallProposal(null, null, null, "Clinic B", "Wrocław");
        CallPlan plan = new CallPlan(
            task,
            planTarget,
            List.of(),
            List.of(),
            List.of(new CallPlanProposalRule("offer", Set.of("mamy ofertę"), proposal)),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        assertThrows(
            IllegalStateException.class,
            () -> new CallPlanTurnCoordinator(plan, workflow).handleFinalTranscript("mamy ofertę", 0)
        );

        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state());
        assertNull(workflow.snapshot().pendingProposal());
        assertNull(workflow.snapshot().outcome());
    }

    @Test
    public void invalidWorkflowStateFailsClosedInsteadOfSilentlyRoutingProposal() {
        CallTask task = task(CallConstraints.unconstrained(), Map.of());
        CallResolvedTarget target = target("Clinic A", "+48123456789");
        CallWorkflow workflow = new CallWorkflow(task, new CallConfirmationPolicy(), snapshot -> { });
        workflow.resolveTarget(target);
        CallProposal proposal = new CallProposal(null, null, null, "Clinic A", "Wrocław");
        CallPlan plan = new CallPlan(
            task,
            target,
            List.of(),
            List.of(),
            List.of(new CallPlanProposalRule("offer", Set.of("mamy ofertę"), proposal)),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        assertThrows(
            IllegalStateException.class,
            () -> new CallPlanTurnCoordinator(plan, workflow).handleFinalTranscript("mamy ofertę", 0)
        );

        assertEquals(CallWorkflowState.READY_TO_DIAL, workflow.snapshot().state());
        assertNull(workflow.snapshot().pendingProposal());
    }

    @Test
    public void resultRenderingDoesNotExposeSayTextOrProposalPayload() {
        CallTask factTask = task(CallConstraints.unconstrained(), Map.of("secret", "Sensitive Fact"));
        CallResolvedTarget target = target("Clinic A", "+48123456789");
        CallWorkflow factWorkflow = activeWorkflow(factTask, target);
        CallPlan factPlan = new CallPlan(
            factTask,
            target,
            List.of(new CallPlanRule("secret", Set.of("sekret"), "secret")),
            List.of(),
            List.of(),
            CallPlanFallbackPolicy.takeOverImmediately()
        );
        CallPlanTurnResult say = new CallPlanTurnCoordinator(factPlan, factWorkflow)
            .handleFinalTranscript("sekret", 0);

        assertFalse(say.toString().contains("Sensitive Fact"));
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

    private static CallResolvedTarget target(String label, String number) {
        return new CallResolvedTarget(label, number);
    }

    private static CallWorkflow activeWorkflow(CallTask task, CallResolvedTarget target) {
        CallWorkflow workflow = new CallWorkflow(task, new CallConfirmationPolicy(), snapshot -> { });
        workflow.resolveTarget(target);
        workflow.markDialing();
        workflow.markCallActive();
        return workflow;
    }
}
