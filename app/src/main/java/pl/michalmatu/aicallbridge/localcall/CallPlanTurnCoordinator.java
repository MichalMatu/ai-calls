package pl.michalmatu.aicallbridge.localcall;

import java.util.Objects;

import pl.michalmatu.aicallbridge.agent.CallPlan;
import pl.michalmatu.aicallbridge.agent.CallPlanDecision;
import pl.michalmatu.aicallbridge.agent.CallPlanEngine;
import pl.michalmatu.aicallbridge.agent.CallPolicyDecision;
import pl.michalmatu.aicallbridge.agent.CallWorkflow;
import pl.michalmatu.aicallbridge.agent.CallWorkflowSnapshot;
import pl.michalmatu.aicallbridge.agent.CallWorkflowState;

/**
 * Product-owned host-side router from one final transcript into the deterministic CallPlan layer.
 *
 * <p>This class does not own dialing, media, speech output, commitment authorization, or model
 * behavior. It only delegates typed proposal/completion mutations to the existing CallWorkflow.
 * SAY/ASK_REPEAT/TAKE_OVER remain structured decisions for a later session/output layer.</p>
 */
final class CallPlanTurnCoordinator {
    private final CallPlan plan;
    private final CallWorkflow workflow;
    private final CallPlanEngine engine;

    CallPlanTurnCoordinator(CallPlan plan, CallWorkflow workflow) {
        this(plan, workflow, new CallPlanEngine());
    }

    CallPlanTurnCoordinator(CallPlan plan, CallWorkflow workflow, CallPlanEngine engine) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.workflow = Objects.requireNonNull(workflow, "workflow");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    CallPlanTurnResult handleFinalTranscript(String finalTranscript, int priorUnknownCount) {
        validateBindingAndState();
        CallPlanDecision decision = engine.decide(plan, finalTranscript, priorUnknownCount);

        return switch (decision.action()) {
            case SAY, ASK_REPEAT, TAKE_OVER -> new CallPlanTurnResult(decision, null);
            case PROPOSAL -> routeProposal(decision);
            case COMPLETE -> routeCompletion(decision);
        };
    }

    private CallPlanTurnResult routeProposal(CallPlanDecision decision) {
        CallPolicyDecision policyDecision = workflow.evaluateProposal(
            Objects.requireNonNull(decision.proposal(), "proposal decision payload")
        );
        return new CallPlanTurnResult(decision, policyDecision);
    }

    private CallPlanTurnResult routeCompletion(CallPlanDecision decision) {
        workflow.complete(Objects.requireNonNull(decision.outcome(), "completion decision outcome"));
        return new CallPlanTurnResult(decision, null);
    }

    private void validateBindingAndState() {
        CallWorkflowSnapshot snapshot = workflow.snapshot();
        if (snapshot.task() != plan.task()) {
            throw new IllegalStateException("CallPlan task does not match workflow task");
        }
        if (!Objects.equals(snapshot.resolvedTarget(), plan.resolvedTarget())) {
            throw new IllegalStateException("CallPlan target does not match workflow target");
        }
        if (snapshot.state() != CallWorkflowState.ACTIVE_NEGOTIATION) {
            throw new IllegalStateException(
                "CallPlan final transcript requires ACTIVE_NEGOTIATION but was " + snapshot.state()
            );
        }
    }
}
