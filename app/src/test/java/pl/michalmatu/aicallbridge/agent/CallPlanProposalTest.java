package pl.michalmatu.aicallbridge.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class CallPlanProposalTest {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Test
    public void knownOfferPhraseReturnsTypedProposalOnly() {
        CallProposal proposal = offer("250.00", "Secret Provider", "Secret Location");
        CallPlan plan = plan(
            task(CallConstraints.unconstrained()),
            List.of(new CallPlanProposalRule(
                "private-offer",
                Set.of("mamy termin jutro o czternastej za 250 zł prywatnie"),
                proposal
            )),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanDecision decision = new CallPlanEngine().decide(
            plan,
            " Mamy termin jutro o czternastej za 250 zł, prywatnie. ",
            0
        );

        assertEquals(CallPlanAction.PROPOSAL, decision.action());
        assertNull(decision.text());
        assertNull(decision.outcome());
        assertEquals("private-offer", decision.ruleId());
        assertSame(proposal, decision.proposal());
    }

    @Test
    public void unknownOfferUsesFallbackAndNeverFabricatesProposal() {
        CallPlan plan = plan(
            task(CallConstraints.unconstrained()),
            List.of(new CallPlanProposalRule("known-offer", Set.of("mamy termin"), offer("100.00", null, null))),
            CallPlanFallbackPolicy.repeatThenTakeOver(1)
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "inna oferta", 0);

        assertEquals(CallPlanAction.ASK_REPEAT, decision.action());
        assertNull(decision.proposal());
    }

    @Test
    public void ambiguousProposalRulesFailClosed() {
        CallPlan plan = plan(
            task(CallConstraints.unconstrained()),
            List.of(
                new CallPlanProposalRule("one", Set.of("mamy termin"), offer("100.00", null, null)),
                new CallPlanProposalRule("two", Set.of("mamy termin"), offer("120.00", null, null))
            ),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "mamy termin", 0);

        assertEquals(CallPlanAction.TAKE_OVER, decision.action());
        assertNull(decision.proposal());
        assertNull(decision.ruleId());
    }

    @Test
    public void collisionAcrossFactCompletionAndProposalRulesFailsClosed() {
        CallTask task = task(CallConstraints.unconstrained());
        CallPlan plan = new CallPlan(
            task,
            target(),
            List.of(new CallPlanRule("fact", Set.of("gotowe"), "birth_year")),
            List.of(new CallPlanCompletionRule(
                "complete",
                Set.of("gotowe"),
                new CallOutcome(CallOutcomeStatus.SUCCESS, "Done", null, null, null, null, null, null)
            )),
            List.of(new CallPlanProposalRule("proposal", Set.of("gotowe"), offer("100.00", null, null))),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "gotowe", 0);

        assertEquals(CallPlanAction.TAKE_OVER, decision.action());
        assertNull(decision.text());
        assertNull(decision.outcome());
        assertNull(decision.proposal());
        assertNull(decision.ruleId());
    }

    @Test
    public void proposalCollectionsAreDefensivelyCopiedAndRenderingIsRedacted() {
        Set<String> utterances = new LinkedHashSet<>(Set.of("tajna oferta"));
        CallProposal secret = offer("321.00", "Secret Provider", "Secret Location");
        CallPlanProposalRule rule = new CallPlanProposalRule("secret-offer", utterances, secret);
        List<CallPlanProposalRule> proposalRules = new ArrayList<>(List.of(rule));
        CallPlan plan = plan(
            task(CallConstraints.unconstrained()),
            proposalRules,
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        utterances.add("inna oferta");
        proposalRules.clear();

        assertEquals(Set.of("tajna oferta"), rule.utterances());
        assertEquals(List.of(rule), plan.proposalRules());
        assertThrows(UnsupportedOperationException.class, () -> rule.utterances().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> plan.proposalRules().clear());

        CallPlanDecision decision = new CallPlanEngine().decide(plan, "tajna oferta", 0);
        assertFalse(plan.toString().contains("Secret Provider"));
        assertFalse(plan.toString().contains("Secret Location"));
        assertFalse(plan.toString().contains("321.00"));
        assertFalse(decision.toString().contains("Secret Provider"));
        assertFalse(decision.toString().contains("Secret Location"));
        assertFalse(decision.toString().contains("321.00"));
    }

    @Test
    public void existingWorkflowPolicyStillOwnsOutOfPolicyProposalDecision() {
        CallTask task = task(new CallConstraints(
            List.of(),
            money("100.00", "PLN"),
            EnumSet.noneOf(CallPaymentMode.class)
        ));
        CallProposal expensiveOffer = offer("120.00", "Clinic A", "Wrocław");
        CallPlan plan = plan(
            task,
            List.of(new CallPlanProposalRule("expensive", Set.of("oferta 120 zł"), expensiveOffer)),
            CallPlanFallbackPolicy.takeOverImmediately()
        );

        CallPlanDecision planDecision = new CallPlanEngine().decide(plan, "oferta 120 zł", 0);
        assertEquals(CallPlanAction.PROPOSAL, planDecision.action());
        assertSame(expensiveOffer, planDecision.proposal());

        CallWorkflow workflow = new CallWorkflow(task, new CallConfirmationPolicy(), snapshot -> { });
        workflow.resolveTarget(target());
        workflow.markDialing();
        workflow.markCallActive();

        CallPolicyDecision policyDecision = workflow.evaluateProposal(planDecision.proposal());

        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, policyDecision.action());
        assertEquals(List.of(CallPolicyReason.PRICE_ABOVE_MAX), policyDecision.reasons());
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state());
        assertSame(expensiveOffer, workflow.snapshot().pendingProposal());
    }

    private static CallPlan plan(
        CallTask task,
        List<CallPlanProposalRule> proposalRules,
        CallPlanFallbackPolicy fallbackPolicy
    ) {
        return new CallPlan(
            task,
            target(),
            List.of(new CallPlanRule("birth-year", Set.of("jaki jest rok urodzenia"), "birth_year")),
            List.of(),
            proposalRules,
            fallbackPolicy
        );
    }

    private static CallTask task(CallConstraints constraints) {
        return new CallTask(
            "Clinic A",
            "book",
            "consultation",
            constraints,
            CallPreferences.none(),
            Map.of("birth_year", "1990")
        );
    }

    private static CallResolvedTarget target() {
        return new CallResolvedTarget("Clinic A", "+48123456789");
    }

    private static CallProposal offer(String amount, String provider, String location) {
        return new CallProposal(
            ZonedDateTime.of(2026, 9, 22, 14, 0, 0, 0, WARSAW),
            money(amount, "PLN"),
            CallPaymentMode.PRIVATE,
            provider,
            location
        );
    }

    private static MoneyAmount money(String amount, String currency) {
        return new MoneyAmount(new BigDecimal(amount), currency);
    }
}
