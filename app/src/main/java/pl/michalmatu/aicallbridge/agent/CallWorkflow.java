package pl.michalmatu.aicallbridge.agent;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Deterministic user-level Telephone Agent workflow.
 *
 * <p>This class owns only task progress and confirmation state. Target resolution, dialing, media,
 * Realtime transport and language-model behavior remain outside this state machine.</p>
 */
public final class CallWorkflow {
    private final Object lock = new Object();
    private final CallTask task;
    private final CallConfirmationPolicy confirmationPolicy;
    private final CopyOnWriteArrayList<Consumer<CallWorkflowSnapshot>> listeners =
        new CopyOnWriteArrayList<>();

    private CallWorkflowState state = CallWorkflowState.RESEARCHING;
    private CallResolvedTarget resolvedTarget;
    private CallProposal pendingProposal;
    private CallPolicyDecision pendingDecision;
    private CallOutcome outcome;
    private String failureReason;

    public CallWorkflow(
        CallTask task,
        CallConfirmationPolicy confirmationPolicy,
        Consumer<CallWorkflowSnapshot> listener
    ) {
        this.task = Objects.requireNonNull(task, "task");
        this.confirmationPolicy = Objects.requireNonNull(confirmationPolicy, "confirmationPolicy");
        listeners.add(Objects.requireNonNull(listener, "listener"));
        publish(snapshot());
    }

    /** Adds a future-only observer. The returned handle removes only this subscription. */
    public AutoCloseable addListener(Consumer<CallWorkflowSnapshot> listener) {
        Consumer<CallWorkflowSnapshot> added = Objects.requireNonNull(listener, "listener");
        listeners.add(added);
        return () -> listeners.remove(added);
    }

    public CallWorkflowSnapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    public void resolveTarget(CallResolvedTarget target) {
        final CallWorkflowSnapshot next;
        synchronized (lock) {
            requireState(CallWorkflowState.RESEARCHING);
            resolvedTarget = Objects.requireNonNull(target, "target");
            state = CallWorkflowState.READY_TO_DIAL;
            next = snapshotLocked();
        }
        publish(next);
    }

    public void markDialing() {
        transition(CallWorkflowState.READY_TO_DIAL, CallWorkflowState.DIALING);
    }

    public void markCallActive() {
        transition(CallWorkflowState.DIALING, CallWorkflowState.ACTIVE_NEGOTIATION);
    }

    /**
     * Evaluates one counterparty proposal against the immutable task authorization.
     *
     * <p>An autonomously allowed proposal leaves the workflow active. A material deviation stores
     * exactly that proposal and its machine-readable reasons until the user approves or rejects it.
     * Neither path mutates the task, constraints, preferences or authorized facts.</p>
     */
    public CallPolicyDecision evaluateProposal(CallProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        final CallPolicyDecision decision;
        final CallWorkflowSnapshot next;
        synchronized (lock) {
            requireState(CallWorkflowState.ACTIVE_NEGOTIATION);
            decision = confirmationPolicy.evaluate(task, proposal);
            if (decision.action() == CallPolicyAction.NEEDS_USER_DECISION) {
                pendingProposal = proposal;
                pendingDecision = decision;
                state = CallWorkflowState.NEEDS_USER_DECISION;
                next = snapshotLocked();
            } else {
                next = null;
            }
        }
        publish(next);
        return decision;
    }

    /**
     * Approves only the currently pending concrete proposal. The original task authority is not
     * widened; a later proposal is evaluated from scratch against the same immutable task.
     */
    public CallProposal approvePendingProposal() {
        return finishPendingDecision();
    }

    /** Rejects only the currently pending proposal and resumes negotiation under the same task. */
    public CallProposal rejectPendingProposal() {
        return finishPendingDecision();
    }

    /**
     * Records a structured business result. A negative business outcome is still COMPLETED; FAILED
     * is reserved for workflow/system failures that prevented a reliable result.
     */
    public void complete(CallOutcome completedOutcome) {
        Objects.requireNonNull(completedOutcome, "completedOutcome");
        final CallWorkflowSnapshot next;
        synchronized (lock) {
            if (state != CallWorkflowState.DIALING && state != CallWorkflowState.ACTIVE_NEGOTIATION) {
                throw new IllegalStateException("cannot complete workflow from " + state);
            }
            pendingProposal = null;
            pendingDecision = null;
            failureReason = null;
            outcome = completedOutcome;
            state = CallWorkflowState.COMPLETED;
            next = snapshotLocked();
        }
        publish(next);
    }

    /** Transitions any non-terminal workflow state to technical FAILED. */
    public void fail(Throwable error) {
        Objects.requireNonNull(error, "error");
        final CallWorkflowSnapshot next;
        synchronized (lock) {
            if (state == CallWorkflowState.COMPLETED || state == CallWorkflowState.FAILED) {
                throw new IllegalStateException("cannot fail terminal workflow from " + state);
            }
            pendingProposal = null;
            pendingDecision = null;
            outcome = null;
            failureReason = describe(error);
            state = CallWorkflowState.FAILED;
            next = snapshotLocked();
        }
        publish(next);
    }

    private CallProposal finishPendingDecision() {
        final CallProposal proposal;
        final CallWorkflowSnapshot next;
        synchronized (lock) {
            requireState(CallWorkflowState.NEEDS_USER_DECISION);
            proposal = Objects.requireNonNull(pendingProposal, "pendingProposal");
            pendingProposal = null;
            pendingDecision = null;
            state = CallWorkflowState.ACTIVE_NEGOTIATION;
            next = snapshotLocked();
        }
        publish(next);
        return proposal;
    }

    private void transition(CallWorkflowState expected, CallWorkflowState nextState) {
        final CallWorkflowSnapshot next;
        synchronized (lock) {
            requireState(expected);
            state = nextState;
            next = snapshotLocked();
        }
        publish(next);
    }

    private void requireState(CallWorkflowState expected) {
        if (state != expected) {
            throw new IllegalStateException("expected " + expected + " but was " + state);
        }
    }

    private CallWorkflowSnapshot snapshotLocked() {
        return new CallWorkflowSnapshot(
            task,
            state,
            resolvedTarget,
            pendingProposal,
            pendingDecision,
            outcome,
            failureReason
        );
    }

    private void publish(CallWorkflowSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        for (Consumer<CallWorkflowSnapshot> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException ignored) {
                // UI/telemetry observers do not own workflow state or authorization.
            }
        }
    }

    private static String describe(Throwable error) {
        String type = error.getClass().getSimpleName();
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        return type + ":" + message.replace('\n', ' ').replace('\r', ' ');
    }
}
