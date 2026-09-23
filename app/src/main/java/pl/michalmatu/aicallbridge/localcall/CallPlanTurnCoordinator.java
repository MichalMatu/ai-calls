package pl.michalmatu.aicallbridge.localcall;

import java.util.Objects;

import pl.michalmatu.aicallbridge.agent.CallPlan;
import pl.michalmatu.aicallbridge.agent.CallPlanDecision;
import pl.michalmatu.aicallbridge.agent.CallPlanEngine;
import pl.michalmatu.aicallbridge.agent.CallPlanHelperSuggestion;
import pl.michalmatu.aicallbridge.agent.CallPlanHelperValidator;
import pl.michalmatu.aicallbridge.agent.CallPolicyDecision;
import pl.michalmatu.aicallbridge.agent.CallWorkflow;
import pl.michalmatu.aicallbridge.agent.CallWorkflowSnapshot;
import pl.michalmatu.aicallbridge.agent.CallWorkflowState;

/**
 * Product-owned host-side router from one final transcript or one bounded existing-rule suggestion
 * into the deterministic CallPlan layer.
 *
 * <p>This class does not own dialing, media, speech output, commitment authorization, or model
 * behavior. It delegates typed proposal mutations to the existing CallWorkflow. COMPLETE keeps the
 * historic workflow-owned mutation by default; an explicitly reviewed product composition may
 * defer only that mutation so stronger application-owned success evidence can be required first.
 * SAY/ASK_REPEAT/TAKE_OVER remain structured decisions for a later session/output layer.</p>
 */
final class CallPlanTurnCoordinator {
    private final CallPlan plan;
    private final CallWorkflow workflow;
    private final CallPlanCompletionMode completionMode;
    private final CallPlanEngine engine;
    private final CallPlanHelperValidator helperValidator;

    CallPlanTurnCoordinator(CallPlan plan, CallWorkflow workflow) {
        this(
            plan,
            workflow,
            CallPlanCompletionMode.APPLY_TO_WORKFLOW,
            new CallPlanEngine(),
            new CallPlanHelperValidator()
        );
    }

    CallPlanTurnCoordinator(
        CallPlan plan,
        CallWorkflow workflow,
        CallPlanCompletionMode completionMode
    ) {
        this(
            plan,
            workflow,
            completionMode,
            new CallPlanEngine(),
            new CallPlanHelperValidator()
        );
    }

    CallPlanTurnCoordinator(CallPlan plan, CallWorkflow workflow, CallPlanEngine engine) {
        this(
            plan,
            workflow,
            CallPlanCompletionMode.APPLY_TO_WORKFLOW,
            engine,
            new CallPlanHelperValidator()
        );
    }

    CallPlanTurnCoordinator(
        CallPlan plan,
        CallWorkflow workflow,
        CallPlanEngine engine,
        CallPlanHelperValidator helperValidator
    ) {
        this(
            plan,
            workflow,
            CallPlanCompletionMode.APPLY_TO_WORKFLOW,
            engine,
            helperValidator
        );
    }

    private CallPlanTurnCoordinator(
        CallPlan plan,
        CallWorkflow workflow,
        CallPlanCompletionMode completionMode,
        CallPlanEngine engine,
        CallPlanHelperValidator helperValidator
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.workflow = Objects.requireNonNull(workflow, "workflow");
        this.completionMode = Objects.requireNonNull(completionMode, "completionMode");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.helperValidator = Objects.requireNonNull(helperValidator, "helperValidator");
    }

    CallPlanTurnResult handleFinalTranscript(String finalTranscript, int priorUnknownCount) {
        validateBindingAndState();
        return routeDecision(engine.decide(plan, finalTranscript, priorUnknownCount));
    }

    /**
     * Routes one proposal-only rule id through the existing CallPlan helper validator.
     *
     * <p>The caller cannot supply speech, facts, proposals, outcomes, or authority. The rule id must
     * resolve uniquely inside the immutable bound plan; unsupported/colliding ids use the plan's
     * bounded fail-closed fallback.</p>
     */
    CallPlanTurnResult handleSuggestedRuleId(String ruleId, int priorUnknownCount) {
        validateBindingAndState();
        CallPlanDecision decision = helperValidator.validate(
            plan,
            new CallPlanHelperSuggestion(ruleId),
            priorUnknownCount
        );
        return routeDecision(decision);
    }

    private CallPlanTurnResult routeDecision(CallPlanDecision decision) {
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
        Objects.requireNonNull(decision.outcome(), "completion decision outcome");
        if (completionMode == CallPlanCompletionMode.APPLY_TO_WORKFLOW) {
            workflow.complete(decision.outcome());
        }
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
