# Handoff — Gate C / CallPlan v1

Date: 2026-09-21

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here next time

1. Read fresh `AGENTS.md`, this file, `README.md`, `docs/ROADMAP.md`, and `docs/NIGHT_AUTONOMOUS_RUN_2026-09-21.md`.
2. Read `docs/ARCHITECTURE.md` and `docs/SECURITY_PRIVACY.md` before changing authority/privacy boundaries.
3. Fetch fresh `main` and `agent-control:.agent/status/daemon.json` before any write/task.
4. Inspect the exact latest Local Agent terminal result before creating a successor task.
5. Trust the current Bridge envelope + fresh daemon binding, never prose history, for repository/binding identity.
6. Keep `privileged-helper/` and the frozen Samsung media path untouched.

## Frozen foundation

Target: Samsung Galaxy S22+ `SM-S906B`.

- cellular RX/TX: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned output approval: `DONE / PROVEN_S22`;
- Gate A readiness: `DONE`;
- phone-local general-purpose llama.cpp sweep: frozen;
- Edge Gallery / Gemma 4 E2B / official Agent Skills checkpoint: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`;
- interactive ChatGPT relay: developer benchmark infrastructure only;
- no further Orange live-call authorization is currently available.

## Active goal: Gate C product wiring

The deterministic `CallPlan v1` host-policy core is `DONE / HOST_GREEN`.

Authority remains unchanged:

- `CallTask` owns immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` is a resolved target but grants no dial/allowlist authority;
- `CallWorkflow` owns progress, pending proposal/user-decision state and terminal outcome;
- `CallConfirmationPolicy` evaluates typed `CallProposal` values;
- `CallCommitmentGate` owns exact one-shot commitment authorization;
- application-owned output approval remains mandatory before speech release/TTS/TX.

`CallPlan` is policy/context, not an authority store. Model/helper output remains proposal-only.

## Completed Gate C host-policy core

Host-green behavior:

1. known question -> exact value from existing `CallTask.authorizedFacts`;
2. missing fact -> fail-closed `TAKE_OVER`;
3. unknown/ambiguous final -> bounded `ASK_REPEAT` / `TAKE_OVER`;
4. known offer -> predeclared typed `CallProposal`;
5. out-of-policy proposal -> existing workflow/policy -> `NEEDS_USER_DECISION`;
6. exact commitment permit remains required and one-shot;
7. output remains fail-closed outside `ACTIVE_NEGOTIATION` or while commitment authority is pending;
8. completion -> predeclared `CallOutcome`, with `CallWorkflow` as terminal-state owner;
9. optional helper may suggest only one existing `ruleId`; it cannot supply payload/action/authority;
10. mutable caller collections are defensively copied and normal rendering redacts sensitive payloads.

Representative evidence:

```text
.agent/results/chatgpt-gate-c-callplan-green-v23-20260921.json
.agent/results/chatgpt-gate-c-callplan-bounded-fallback-green-v25-20260921.json
.agent/results/chatgpt-gate-c-callplan-completion-green-v27-20260921.json
.agent/results/chatgpt-gate-c-callplan-proposal-green-v29-20260921.json
.agent/results/chatgpt-gate-c-callplan-authority-regression-v30-20260921.json
.agent/results/chatgpt-gate-c-callplan-helper-green-v32-20260921.json
```

## Completed product-wiring slices

### CallPlan turn coordinator — DONE / HOST_GREEN

`CallPlanTurnCoordinator` is product-owned and outside media/speech ownership.

- final transcript + explicit prior-unknown count -> `CallPlanEngine`;
- `SAY`, `ASK_REPEAT`, `TAKE_OVER` remain structured results;
- `PROPOSAL` routes only through `CallWorkflow.evaluateProposal(...)`;
- `COMPLETE` routes only through `CallWorkflow.complete(...)`;
- plan/workflow task+target mismatch and invalid workflow state fail before mutation;
- no dialing, commitment authorization, TTS/TX or backend/model fallback.

Evidence:

```text
.agent/results/chatgpt-gate-c-callplan-turn-coordinator-red-v33-20260921.json
.agent/results/chatgpt-gate-c-callplan-turn-coordinator-green-v34-20260921.json
```

### Prepared-call CallPlan binding — DONE / HOST_GREEN

The one-shot readiness/session handoff can now carry one immutable `CallPlan` bound to the same workflow/task/target.

- readiness rejects mismatched plan task/target before speech/backend work;
- `PreparedLocalTextCall` carries the bound plan alongside the same workflow/backend ownership;
- `LocalTextCallSession.handlePlanFinalTranscript(...)` returns the structured coordinator result;
- deterministic plan routing does not invoke the generative backend;
- existing no-plan diagnostic/session path remains compatible;
- no media/TTS/TX semantics changed.

`v37` itself was an invalid harness run (`mapfile` unavailable in macOS bash) and tested no product code. `v37b` fixed only the control script and passed the targeted regression set plus full `bash scripts/verify_host.sh`, with the exact three-file product diff and `privileged-helper/` unchanged.

Evidence:

```text
.agent/results/chatgpt-gate-c-prepared-callplan-binding-red-v36-20260921.json
.agent/results/chatgpt-gate-c-prepared-callplan-binding-green-v37b-20260921.json
```

## Current product boundary

Existing speech path remains:

```text
LocalTextCallSession
  -> LocalSpeechTextPipeline
      -> final STT transcript
      -> TextCallTurnController
          -> backend complete text
          -> TextOutputApprovalPolicy
      -> local TTS
```

Current plan path is available structurally through the plan-bound session, but final STT is not yet automatically intercepted before the generative backend.

Important current contracts:

- `TextCallTurnController` is the existing complete-text approval owner;
- `LocalSpeechTextPipeline` owns STT/TTS lifecycle and generation cancellation;
- no `CallPlan` class should own media or output approval;
- only `CallPlanAction.SAY` carries speech text;
- `ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, `TAKE_OVER` carry no implicit speech text and must not invent one or silently fall through to a model.

## Next exact engineering step

Continue host-only/TDD with the smallest safe speech-output seam:

1. first add a controller-level path for **already-determined candidate text** to pass through the existing `TextOutputApprovalPolicy` without invoking `TextCallAgentBackend.generate(...)`;
2. RED must prove RELEASE returns the exact candidate, DROP stays dropped, blank input fails, and backend generation is never called;
3. keep ordinary `submitUserText(...)` backend behavior unchanged;
4. after that primitive is GREEN, add a separate product/pipeline routing slice where final STT can be intercepted before backend generation;
5. for a plan-bound turn, `SAY` may supply only its exact authorized/predeclared text to the candidate-approval path;
6. non-speech plan actions (`ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, `TAKE_OVER`) must stop backend fallback and remain structured product callbacks/results;
7. no new hard-coded conversational text in this step;
8. do not touch Samsung media, dial authorization, commitment authorization, `privileged-helper/`, or live calls;
9. every behavior slice: RED -> minimal GREEN -> targeted regressions -> `bash scripts/verify_host.sh`.
