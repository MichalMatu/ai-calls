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

## Active goal: Gate C — CallPlan v1

Gate C is the active productization gate: a deterministic call brain for common bounded turns, with any future model/helper restricted to proposal-only language classification or paraphrase.

Existing authority remains unchanged:

- `CallTask` owns immutable user/operator task authority, including `CallConstraints`, `CallPreferences` and `authorizedFacts`;
- `CallResolvedTarget` is one resolved target but does not grant dialing authority or widen a runtime allowlist;
- `CallWorkflow` owns progress, pending proposals, user-decision state and terminal outcome;
- `CallConfirmationPolicy` evaluates concrete `CallProposal` data;
- `CallCommitmentGate` owns one-shot exact-proposal commitment permission;
- application-owned output approval remains required before TTS/TX.

`CallPlan` is deliberately not another authority store. It references the existing task and resolved target and carries only immutable deterministic dialogue policy.

## Completed Gate C host slices

### Slice 1 — immutable known-fact CallPlan engine

Status: `DONE / HOST_GREEN`.

Durable behavior now includes:

- immutable `CallPlan` referencing the existing `CallTask` and `CallResolvedTarget`;
- immutable `CallPlanRule` containing utterance variants plus an `authorizedFacts` key, never a copied fact value;
- deterministic final-transcript normalization/matching;
- exact `SAY` proposal using the value read from `CallTask.authorizedFacts` at decision time;
- missing referenced fact -> fail-closed `TAKE_OVER`;
- unknown/ambiguous transcript -> fallback, never guessed authority;
- defensive immutable collection copies;
- redacted ordinary rendering of task/target-sensitive data.

TDD evidence:

```text
.agent/results/chatgpt-gate-c-callplan-red-v22-20260921.json
.agent/results/chatgpt-gate-c-callplan-green-v23-20260921.json
```

`v23` passed the targeted `CallPlanTest`, the full `bash scripts/verify_host.sh` host gate, exact seven-file diff validation and `privileged-helper/` unchanged.

### Slice 2 — bounded repeat/escalation policy

Status: `DONE / HOST_GREEN`.

Durable behavior now includes `CallPlanFallbackPolicy` with stateless bounded escalation:

```text
priorUnknownCount < maxRepeats -> ASK_REPEAT
priorUnknownCount >= maxRepeats -> TAKE_OVER
```

The retry count is explicit input to `CallPlanEngine`; the plan/engine keep no hidden mutable conversation counter. Known deterministic rules still win even after earlier unknown turns. Negative counters and invalid limits fail at the API boundary. The old two-argument `decide(plan, transcript)` path remains source-compatible and behaves as `priorUnknownCount = 0`.

TDD evidence:

```text
.agent/results/chatgpt-gate-c-callplan-bounded-fallback-red-v24-20260921.json
.agent/results/chatgpt-gate-c-callplan-bounded-fallback-green-v25-20260921.json
```

`v25` passed both targeted CallPlan test classes, exact four-file diff validation, the full host gate and `privileged-helper/` unchanged.

No S22/device/live-call gate was required for either slice because they are pure host/data-policy behavior.

## Remaining Gate C matrix

Still open:

- completion criteria -> structured `COMPLETE` proposal/outcome while `CallWorkflow.complete(...)` remains the sole terminal-state owner;
- known counterparty offer -> typed `CallProposal` routed to the existing workflow/policy, never auto-accepted by CallPlan;
- explicit regression proof that proposal policy, commitment gate and output approval cannot be bypassed by plan decisions;
- optional bounded language-helper validation that rejects unsupported rule/fact/action suggestions;
- product wiring only after the deterministic policy model is complete and host-green.

## Next exact engineering step

Implement the next host-only TDD slice for **completion criteria**:

1. start from fresh `main` plus fresh Local Agent status/binding;
2. RED first for an immutable completion criterion that matches only a known final transcript and returns a structured `COMPLETE` proposal carrying a `CallOutcome`;
3. prove unknown/ambiguous completion text never fabricates completion;
4. keep completion matching deterministic and immutable; defensive-copy caller collections and redact diagnostics;
5. do **not** call or mutate `CallWorkflow` inside `CallPlanEngine`; `CallWorkflow.complete(CallOutcome)` remains the sole owner of terminal `COMPLETED` state;
6. keep existing known-fact and bounded-fallback behavior source-compatible;
7. do not touch `CallConfirmationPolicy`, `CallCommitmentGate`, media, STT/TTS, diagnostics or `privileged-helper/` unless a failing test proves a concrete gap;
8. run targeted tests and `bash scripts/verify_host.sh` before declaring GREEN.

No S22 gate and no live Orange call are part of this next slice.
