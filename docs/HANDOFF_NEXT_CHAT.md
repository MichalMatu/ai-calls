# Handoff — Gate C / CallPlan v1

Date: 2026-09-21

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here next time

1. Read fresh `AGENTS.md`, this file, `README.md`, `docs/ROADMAP.md`, and `docs/NIGHT_AUTONOMOUS_RUN_2026-09-21.md`.
2. Read `docs/ARCHITECTURE.md` and `docs/SECURITY_PRIVACY.md` before changing authority or privacy boundaries.
3. Fetch fresh `main` and `agent-control:.agent/status/daemon.json` before any write or Local Agent task.
4. Inspect the exact latest Local Agent terminal result before creating a successor task.
5. Never trust a Local Agent binding or commit SHA copied from prose; use the current Bridge envelope and fresh daemon state.
6. Keep `privileged-helper/` and the frozen Samsung media path untouched.

## Frozen foundation

Target: Samsung Galaxy S22+ `SM-S906B`.

- cellular RX/TX bridge: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned approval: `DONE / PROVEN_S22`;
- Gate A readiness: `DONE`;
- original llama.cpp phone-local model sweep: frozen;
- interactive ChatGPT relay: developer benchmark infrastructure only;
- `privileged-helper/`: frozen.

## Edge Gallery + Agent Skills checkpoint is closed

Status: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`.

The bounded Edge/Gemma experiment remains historical evidence only. The final live Orange experiment failed before application approval/TTS/TX, historical logcat showed the Edge process crashing around `LocalPhoneAgentRuntime.decide()`, the first post-relaunch decision was about 10.65 s, and the Edge process used about 2.58 GB total PSS plus swap pressure. Do not add more Edge probe hacks or make further Orange calls by default.

Representative evidence:

```text
.agent/results/chatgpt-edge-speculative-offcall-benchmark-v16-20260921.json
.agent/results/chatgpt-edge-speculative-live-build-v17-20260921.json
.agent/results/chatgpt-orange-speculative-live-v18-20260921.json
.agent/results/chatgpt-edge-live-transport-diagnosis-v19-20260921.json
.agent/results/chatgpt-edge-exit-reason-offcall-v20-20260921.json
```

No further Orange live-call authorization is currently available.

## Active goal: Gate C — CallPlan v1 product wiring

Gate C deterministic host-policy core is now `DONE / HOST_GREEN`.

The active continuation is **product wiring**, still host-only first: connect the completed `CallPlan` decision model to a product-owned turn coordinator without moving planning into diagnostics, speech/media ownership, or a generative backend.

Existing authority remains unchanged:

- `CallTask` owns immutable user/operator task authority, including `CallConstraints`, `CallPreferences` and `authorizedFacts`;
- `CallResolvedTarget` is one resolved target but does not grant dialing authority or widen a runtime allowlist;
- `CallWorkflow` owns progress, pending proposals, user-decision state and terminal outcome;
- `CallConfirmationPolicy` evaluates concrete `CallProposal` data;
- `CallCommitmentGate` owns one-shot exact-proposal commitment permission;
- application-owned output approval remains required before TTS/TX.

`CallPlan` is deliberately not another authority store. It references the existing task and resolved target and carries only immutable deterministic dialogue policy.

## Completed Gate C host-policy slices

### Slice 1 — known authorized facts

Status: `DONE / HOST_GREEN`.

- immutable `CallPlan` references existing `CallTask` and `CallResolvedTarget`;
- `CallPlanRule` stores a fact key, never a copied fact value;
- exact normalized known question -> `SAY` using the value read from `CallTask.authorizedFacts` at decision time;
- missing fact -> fail-closed `TAKE_OVER`;
- unknown/ambiguous text -> bounded fallback;
- immutable defensive copies and redacted rendering.

Evidence:

```text
.agent/results/chatgpt-gate-c-callplan-red-v22-20260921.json
.agent/results/chatgpt-gate-c-callplan-green-v23-20260921.json
```

### Slice 2 — bounded repeat/escalation

Status: `DONE / HOST_GREEN`.

`CallPlanFallbackPolicy` is stateless and uses explicit `priorUnknownCount`:

```text
priorUnknownCount < repeatLimit -> ASK_REPEAT
priorUnknownCount >= repeatLimit -> TAKE_OVER
```

Known deterministic matches still win after earlier unknown turns. Invalid counters/limits fail at the API boundary.

Evidence:

```text
.agent/results/chatgpt-gate-c-callplan-bounded-fallback-red-v24-20260921.json
.agent/results/chatgpt-gate-c-callplan-bounded-fallback-green-v25-20260921.json
```

### Slice 3 — deterministic completion criteria

Status: `DONE / HOST_GREEN`.

- immutable `CallPlanCompletionRule` contains known final utterances plus one predeclared structured `CallOutcome`;
- exact completion -> `COMPLETE`;
- ambiguity/collision -> bounded fallback;
- `CallPlanEngine` does not mutate `CallWorkflow`;
- `CallWorkflow.complete(CallOutcome)` remains the terminal-state owner;
- `CallPlanDecision` redacts SAY/outcome data.

Evidence:

```text
.agent/results/chatgpt-gate-c-callplan-completion-red-v26-20260921.json
.agent/results/chatgpt-gate-c-callplan-completion-green-v27-20260921.json
```

### Slice 4 — typed counterparty proposal routing

Status: `DONE / HOST_GREEN`.

- immutable `CallPlanProposalRule` contains known final utterances plus one predeclared typed `CallProposal`;
- exact known offer -> `CallPlanAction.PROPOSAL` carrying that same proposal object;
- unknown/ambiguous proposal text never fabricates a proposal;
- collisions across fact/completion/proposal rules fail closed instead of establishing priority;
- proposal collections are defensive/immutable and ordinary rendering redacts proposal data;
- `CallPlanEngine` does not evaluate policy, authorize commitment, or accept anything;
- routing the returned proposal through the existing `CallWorkflow.evaluateProposal(...)` proves an out-of-policy proposal becomes `NEEDS_USER_DECISION`.

Evidence:

```text
.agent/results/chatgpt-gate-c-callplan-proposal-red-v28-20260921.json
.agent/results/chatgpt-gate-c-callplan-proposal-green-v29-20260921.json
```

### Slice 5 — authority-boundary regression

Status: `DONE / HOST_GREEN`.

The test-only integration slice proves that a `CallPlan.PROPOSAL` decision:

- creates no commitment authorization by itself;
- remains subject to `CallConfirmationPolicy` / `CallWorkflow`;
- cannot bypass `NEEDS_USER_DECISION`;
- cannot release speech while the workflow is pending a user decision;
- does not change the exact one-shot semantics of `CallCommitmentGate`;
- leaves application-owned output approval authoritative.

Evidence:

```text
.agent/results/chatgpt-gate-c-callplan-authority-regression-v30-20260921.json
```

### Slice 6 — bounded helper validation

Status: `DONE / HOST_GREEN`.

The optional helper contract is intentionally tiny:

```text
CallPlanHelperSuggestion(ruleId)
```

The helper may suggest only an already-existing plan rule id. `CallPlanHelperValidator` maps that id back to exactly one predeclared fact/completion/proposal rule. It cannot supply speech text, fact values, outcomes, proposals, targets, actions, policy decisions or commitment authority.

- unknown id -> normal bounded fallback;
- duplicate id across rule kinds -> fail closed;
- known fact id still reads `CallTask.authorizedFacts` at validation time;
- missing fact still fails closed;
- completion/proposal payloads remain plan-owned predeclared objects.

Evidence:

```text
.agent/results/chatgpt-gate-c-callplan-helper-red-v31-20260921.json
.agent/results/chatgpt-gate-c-callplan-helper-green-v32-20260921.json
```

`v32` passed the complete CallPlan/authority targeted set plus `bash scripts/verify_host.sh`, with exact three-file helper diff, clean worktree and `privileged-helper/` unchanged.

## Gate C deterministic matrix status

All host-policy cases are now covered:

1. known question -> exact authorized fact: GREEN;
2. missing fact -> fail closed: GREEN;
3. unknown/ambiguous final -> bounded `ASK_REPEAT` / `TAKE_OVER`: GREEN;
4. known offer -> typed `CallProposal`: GREEN;
5. outside policy -> `NEEDS_USER_DECISION`: GREEN;
6. commitment without exact permit remains blocked: GREEN regression evidence;
7. output outside active negotiation or while commitment authority is pending remains dropped: GREEN regression evidence;
8. target binding does not grant/widen dial authority: structural invariant preserved;
9. completion criterion -> structured `COMPLETE`; workflow remains terminal owner: GREEN;
10. unsupported helper suggestion rejected/fallback: GREEN;
11. caller mutable collections copied: GREEN;
12. plan/decision sensitive rendering redacted: GREEN.

No S22/device/live-call gate was required because these slices are pure deterministic host/data-policy behavior.

## Product wiring audit — current boundary

Existing product flow:

```text
LocalTextCallSession
  -> LocalSpeechTextPipeline
      -> final STT transcript
      -> TextCallTurnController
          -> TextCallAgentBackend.generate(...)
          -> TextOutputApprovalPolicy
      -> local TTS
```

Do **not** inject `CallPlan` into Samsung media, STT/TTS, `MainActivity`, diagnostic probes, or `CallPlanEngine` workflow mutation.

`TextCallTurnController` currently has a narrow generative-backend contract: final user text -> complete backend text -> application approval. Keep that contract intact unless a failing wiring test proves it must change.

The lowest-risk next seam is a new **product-owned CallPlan turn coordinator** outside the speech/media layer. It consumes a final transcript and the immutable plan and owns only routing of the already-typed decision to existing workflow owners.

## Next exact engineering step

Implement a host-only RED/GREEN slice for a product-owned `CallPlanTurnCoordinator` (name may vary only if the codebase gives a clearly better product-owned name):

1. place it in product orchestration (`localcall` or an equally narrow product package), not diagnostics, media, `MainActivity`, or the frozen helper;
2. input: immutable `CallPlan`, existing `CallWorkflow`, final transcript, explicit prior-unknown count;
3. call `CallPlanEngine` for deterministic classification; do not duplicate matching logic;
4. `SAY`, `ASK_REPEAT`, and `TAKE_OVER` remain structured coordinator results only — no direct TTS/TX;
5. `PROPOSAL` must route the exact typed proposal through existing `CallWorkflow.evaluateProposal(...)` and expose the resulting `CallPolicyDecision`; it must not call `CallCommitmentGate.authorize` or approve pending proposals;
6. `COMPLETE` must route the exact predeclared `CallOutcome` through existing `CallWorkflow.complete(...)`; the engine itself remains mutation-free;
7. invalid workflow state must fail closed/propagate deterministic workflow rejection rather than silently widening behavior;
8. preserve target identity: the plan target must match the workflow resolved target for product routing, or the coordinator must reject before any workflow mutation;
9. no fallback to a model/backend in this first wiring slice; helper/backend delegation is a separate later decision and must remain proposal-only;
10. RED first, then minimal GREEN, targeted tests plus `bash scripts/verify_host.sh`;
11. no S22 gate and no live Orange call for this slice.

After this coordinator is host-green, wire its structured output into the existing product text/session boundary in a separate slice so application-owned output approval still precedes TTS/TX.
