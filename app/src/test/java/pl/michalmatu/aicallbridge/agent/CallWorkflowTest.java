package pl.michalmatu.aicallbridge.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class CallWorkflowTest {
    @Test
    public void startsInResearchingWithOriginalImmutableTask() {
        CallTask task = task(CallConstraints.unconstrained());
        CallWorkflow workflow = new CallWorkflow(task, new CallConfirmationPolicy(), ignored -> {});

        CallWorkflowSnapshot snapshot = workflow.snapshot();

        assertSame(task, snapshot.task());
        assertEquals(CallWorkflowState.RESEARCHING, snapshot.state());
        assertNull(snapshot.resolvedTarget());
        assertNull(snapshot.pendingProposal());
        assertNull(snapshot.pendingDecision());
        assertNull(snapshot.outcome());
        assertNull(snapshot.failureReason());
    }

    @Test
    public void normalLifecycleReachesActiveNegotiation() {
        CallWorkflow workflow = workflow(CallConstraints.unconstrained());
        CallResolvedTarget target = new CallResolvedTarget("Clinic A", "+48123456789");

        workflow.resolveTarget(target);
        assertEquals(CallWorkflowState.READY_TO_DIAL, workflow.snapshot().state());
        assertSame(target, workflow.snapshot().resolvedTarget());

        workflow.markDialing();
        assertEquals(CallWorkflowState.DIALING, workflow.snapshot().state());

        workflow.markCallActive();
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state());
    }

    @Test
    public void autonomouslyAllowedProposalDoesNotPauseWorkflow() {
        CallWorkflow workflow = activeWorkflow(CallConstraints.unconstrained());
        CallProposal proposal = new CallProposal(null, null, null, "Clinic A", "Wroclaw");

        CallPolicyDecision decision = workflow.evaluateProposal(proposal);

        assertEquals(CallPolicyAction.AUTONOMOUSLY_ALLOWED, decision.action());
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state());
        assertNull(workflow.snapshot().pendingProposal());
        assertNull(workflow.snapshot().pendingDecision());
    }

    @Test
    public void outOfPolicyProposalWaitsForOneTimeUserApprovalWithoutMutatingTask() {
        CallConstraints constraints = new CallConstraints(
            List.of(),
            new MoneyAmount(new BigDecimal("100.00"), "PLN"),
            Set.of()
        );
        CallWorkflow workflow = activeWorkflow(constraints);
        CallProposal proposal = new CallProposal(
            null,
            new MoneyAmount(new BigDecimal("150.00"), "PLN"),
            null,
            "Clinic A",
            "Wroclaw"
        );
        CallTask originalTask = workflow.snapshot().task();

        CallPolicyDecision decision = workflow.evaluateProposal(proposal);

        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, decision.action());
        assertEquals(List.of(CallPolicyReason.PRICE_ABOVE_MAX), decision.reasons());
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state());
        assertSame(proposal, workflow.snapshot().pendingProposal());
        assertSame(decision, workflow.snapshot().pendingDecision());

        CallProposal approved = workflow.approvePendingProposal();

        assertSame(proposal, approved);
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state());
        assertNull(workflow.snapshot().pendingProposal());
        assertNull(workflow.snapshot().pendingDecision());
        assertSame(originalTask, workflow.snapshot().task());
        assertEquals(new BigDecimal("100.00"), workflow.snapshot().task().constraints().maxPrice().amount());
    }

    @Test
    public void rejectedProposalReturnsToNegotiationWithoutChangingAuthority() {
        CallConstraints constraints = new CallConstraints(
            List.of(),
            new MoneyAmount(new BigDecimal("100.00"), "PLN"),
            Set.of()
        );
        CallWorkflow workflow = activeWorkflow(constraints);
        CallProposal proposal = new CallProposal(
            null,
            new MoneyAmount(new BigDecimal("150.00"), "PLN"),
            null,
            "Clinic A",
            null
        );
        workflow.evaluateProposal(proposal);

        CallProposal rejected = workflow.rejectPendingProposal();

        assertSame(proposal, rejected);
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state());
        assertEquals(new BigDecimal("100.00"), workflow.snapshot().task().constraints().maxPrice().amount());
    }

    @Test
    public void negativeBusinessOutcomeIsCompletedNotTechnicalFailure() {
        CallWorkflow workflow = activeWorkflow(CallConstraints.unconstrained());
        CallOutcome outcome = new CallOutcome(
            CallOutcomeStatus.FAILURE,
            "No appointments available",
            null,
            "Clinic A",
            null,
            null,
            null,
            "Try again next week"
        );

        workflow.complete(outcome);

        assertEquals(CallWorkflowState.COMPLETED, workflow.snapshot().state());
        assertSame(outcome, workflow.snapshot().outcome());
        assertNull(workflow.snapshot().failureReason());
    }

    @Test
    public void technicalFailureUsesFailedStateAndNoBusinessOutcome() {
        CallWorkflow workflow = activeWorkflow(CallConstraints.unconstrained());

        workflow.fail(new IllegalStateException("media lost"));

        assertEquals(CallWorkflowState.FAILED, workflow.snapshot().state());
        assertTrue(workflow.snapshot().failureReason().contains("media lost"));
        assertNull(workflow.snapshot().outcome());
    }

    @Test
    public void invalidTransitionsAreRejected() {
        CallWorkflow workflow = workflow(CallConstraints.unconstrained());

        assertThrows(IllegalStateException.class, workflow::markDialing);
        assertThrows(IllegalStateException.class, workflow::markCallActive);
        assertThrows(IllegalStateException.class, workflow::approvePendingProposal);
        assertThrows(IllegalStateException.class, workflow::rejectPendingProposal);
        assertThrows(
            IllegalStateException.class,
            () -> workflow.evaluateProposal(new CallProposal(null, null, null, null, null))
        );
    }

    @Test
    public void terminalWorkflowCannotBeMutated() {
        CallWorkflow workflow = activeWorkflow(CallConstraints.unconstrained());
        workflow.complete(new CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "Booked",
            null,
            "Clinic A",
            null,
            null,
            "REF-1",
            null
        ));

        assertThrows(IllegalStateException.class, workflow::markDialing);
        assertThrows(IllegalStateException.class, () -> workflow.fail(new RuntimeException("late")));
    }

    @Test
    public void observerFailureCannotBlockWorkflowStateChanges() {
        AtomicInteger healthyEvents = new AtomicInteger();
        CallWorkflow workflow = new CallWorkflow(
            task(CallConstraints.unconstrained()),
            new CallConfirmationPolicy(),
            ignored -> { throw new RuntimeException("observer failed"); }
        );
        workflow.addListener(ignored -> healthyEvents.incrementAndGet());

        workflow.resolveTarget(new CallResolvedTarget("Clinic A", "+48123456789"));

        assertEquals(CallWorkflowState.READY_TO_DIAL, workflow.snapshot().state());
        assertEquals(1, healthyEvents.get());
    }

    @Test
    public void resolvedTargetStringDoesNotExposeDialAddress() {
        CallResolvedTarget target = new CallResolvedTarget("Clinic A", "+48123456789");

        assertEquals("Clinic A", target.displayName());
        assertEquals("+48123456789", target.dialAddress());
        assertFalse(target.toString().contains("+48123456789"));
        assertTrue(target.toString().contains("REDACTED"));
    }

    private static CallWorkflow workflow(CallConstraints constraints) {
        return new CallWorkflow(task(constraints), new CallConfirmationPolicy(), ignored -> {});
    }

    private static CallWorkflow activeWorkflow(CallConstraints constraints) {
        CallWorkflow workflow = workflow(constraints);
        workflow.resolveTarget(new CallResolvedTarget("Clinic A", "+48123456789"));
        workflow.markDialing();
        workflow.markCallActive();
        return workflow;
    }

    private static CallTask task(CallConstraints constraints) {
        return new CallTask(
            "Clinic A",
            "book",
            "consultation",
            constraints,
            CallPreferences.none(),
            Map.of("firstName", "Jan")
        );
    }
}
