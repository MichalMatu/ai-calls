# Handoff — Gate D TaskGraph v1 foundation

Date: 2026-09-22

Repository: `MichalMatu/android-ai-call-bridge`

Durable base branch: `main`

Active work branch: `gate-d-taskgraph-core`

Pull request: `#5` — `Gate D TaskGraph v1 core` (draft)

Code checkpoint before this docs-only closeout:

```text
a8e6130c7dd85dd011ed420ddd7be1294b2322a2
Bind Gate D context through Android readiness
```

This handoff is a state snapshot. It is **not** live-call authorization and contains no reusable Local Chat Bridge binding.

Use fresh repository state in the next chat. `docs/ROADMAP.md` is the authoritative execution order; `docs/ARCHITECTURE.md` owns component boundaries; `docs/SECURITY_PRIVACY.md` owns privacy/live-call rules; `docs/NEXT_CHAT_PROMPT.md` is the ready-to-paste bootstrap prompt.

## Read first

1. `AGENTS.md`
2. `README.md`
3. `docs/HANDOFF_NEXT_CHAT.md`
4. `docs/ROADMAP.md`
5. `docs/ARCHITECTURE.md`
6. `docs/GATE_D_TASKGRAPH_V1_AUDIT_2026-09-22.md`
7. `docs/SECURITY_PRIVACY.md`
8. `docs/HANDOFF_PROTOCOL.md`
9. `docs/PHASE2D_FREEZE_2026-09-18.md` before touching Samsung media

Orange files are relevant only when that side track is intentionally resumed: `service-packs/orange/service_tree.v1.json` and `docs/ORANGE_MAPPING_RUNBOOK.md`.

## Current product goal

Active milestone:

```text
Gate D — hybrid multi-turn Task Engine
```

First acceptance task:

```text
BOOK_APPOINTMENT
```

The goal is a bounded real multi-turn appointment task where deterministic interpretation stays primary, the model may observe/propose within a quarantined boundary, and all target/disclosure/speech/proposal/commitment authority remains application-owned.

## What is complete on the work branch

### TaskGraph core

Implemented `CustomTaskGraphCore` with:

- typed state/event/transition/slot/effect IDs;
- pure guards and state compatibility;
- stale generation/version/state rejection;
- bounded recovery;
- proposal/confirmation/commitment/terminal state kinds;
- effects as data;
- versioned event evidence;
- deterministic replay and fail-closed mismatch handling.

Engine decision is closed for v1: keep the minimal custom reducer. No KStateMachine runtime/dependency is part of the production branch.

### Identity/fact-disclosure contracts

Implemented host contracts for:

- `IdentityFieldId`;
- sensitivity metadata;
- per-task `AuthorizedFactSnapshot`;
- application-owned `FactDisclosurePolicy` returning `ALLOW / ASK_USER / DENY`.

Android encrypted IdentityVault persistence is **not** implemented yet.

### BOOK_APPOINTMENT host simulator

Implemented a deterministic receptionist simulator that composes TaskGraph with existing `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and disclosure policy.

Covered semantics include proposal, user confirmation/rejection, one-shot commitment, no availability, clarification/harmless-question recovery, identity requests, cancel/takeover and replay evidence.

### DialogueFit + bounded supervisor contracts

Implemented:

- categorical explainable `DialogueFit` (`HIGH / UNCERTAIN / LOW / BROKEN`);
- bounded `ShadowDialogueObservation`;
- quarantined `ShadowDialogueHypothesis`;
- `SupervisorProposalValidator` with generation, allowed-transition, slot-scope, authority-bearing-slot and confidence checks.

Accepted supervisor output remains candidate data only.

### Product/session binding

Implemented optional Gate D context flow:

```text
AndroidTextCallReadiness / LocalPhoneTextCallReadiness
 -> LocalTextCallReadinessCoordinator
 -> PreparedLocalTextCall
 -> LocalTextCallSession
 -> LocalTextCallGateDRuntime
```

The session-owned runtime can create a bounded observation and revalidate a hypothesis from a real bound task/graph/fact scope.

## Hard stop line at this checkpoint

The current Gate D runtime is deliberately **read-only**.

It does not:

- call `TaskGraphCore.reduce()`;
- execute graph effects;
- mutate `CallWorkflow`;
- release TTS/telephony speech;
- dial or widen a target;
- read plaintext IdentityVault values;
- approve proposals;
- consume commitment authority.

Do not erase this boundary in the first continuation slice.

## Exact next slice

Start with the real finalized-turn product path, host-only.

Write RED contracts first for:

1. a Gate-D-bound `LocalTextCallSession` creating exactly one bounded shadow observation for a finalized turn from current authoritative snapshot/context;
2. existing PhraseMatrix/CallPlan deterministic result remaining unchanged;
3. observer/hypothesis lifecycle being session-owned and stale-generation/cancel/close safe;
4. no plaintext identity values in observation/ordinary diagnostics;
5. hypothesis revalidation plus `DialogueFit` yielding only diagnostics/candidate data;
6. **no automatic TaskGraph reduction in this slice**.

Then minimal GREEN. Do not broad-refactor provider/media/session code to achieve it.

Only after that observation/lifecycle slice is host-green should a later separate slice add an explicit application-owned:

```text
validated candidate
 -> typed TaskGraph event
 -> CustomTaskGraphCore.reduce()
 -> effects as data
 -> existing workflow/proposal/confirmation/commitment owners
```

## Known open work after the next slice

- reusable typed appointment parsers/normalizers outside the simulator;
- PhraseMatrix dialogue-act coverage for appointment conversation;
- deterministic-to-shadow comparison mapping and DialogueFit eval/hysteresis calibration;
- candidate -> TaskGraph event/reducer bridge;
- Android IdentityVault encrypted persistence;
- product-level Gate D integration tests;
- only later, a small reviewed physical reception call with fresh user authorization.

## Frozen boundaries

Do not casually touch:

- `privileged-helper/`;
- frozen Samsung media path;
- physically proven `CallMediaSessionCoordinator` behavior;
- general-purpose phone-local llama.cpp product direction;
- Edge Gallery/Gemma experiment path;
- diagnostic runners/probes as product orchestrators;
- realtime audio-model redesign before Gate D hybrid text/task baseline is mature.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Verification state

Latest full GitHub Actions verification for the code checkpoint:

```text
commit: a8e6130c7dd85dd011ed420ddd7be1294b2322a2
workflow: Android CI
run id: 35748536552
run number: 468
conclusion: success
```

The docs-only closeout commit should be verified from fresh branch state before a new code slice starts.

### Known media-test signal

Earlier full runs around `59b5f75` repeatedly failed only the frozen-area test:

```text
CallRealtimeMediaSessionTest.pumpFailure...
```

The Gate D tests were green and no media code was changed. Latest full CI on `a8e6130` passed, so this is not a current Gate D blocker, but 3/3 earlier repetition means it should not be casually dismissed as random.

If it reappears, first audit test order/pollution by running the media test separately and alongside Gate D tests. Do **not** patch frozen media as part of Gate D without a separate root-cause result and explicit scope decision.

## Branch / PR state

PR #5 is open and draft, targeting `main` from `gate-d-taskgraph-core`.

Before this docs closeout it was:

```text
base: 7b6519238808599a5084f3f1c72d103ea43abd9b
head: a8e6130c7dd85dd011ed420ddd7be1294b2322a2
27 commits ahead / 0 behind
```

The final handoff commit is documentation-only and should be treated as a successor to that code checkpoint.

This GitHub-only closeout did not verify local-only worktrees/branches on the developer machine. Do not delete historical local branches based only on this document.

## Local Agent / Local Chat Bridge

- trust only the fresh binding envelope injected into the new chat;
- never copy an old `agent_binding` from documentation/history;
- work only in the exact bound repository;
- inspect fresh daemon/current-task evidence before queueing local work;
- use Local Agent for Gradle/ADB/device/local-machine execution;
- direct GitHub edits are fine for small exact reviewable docs/code changes;
- `.agent/tasks` and `.agent/results` remain control/evidence data and must not become product architecture;
- never restart Local Agent merely to hide an unclear task/root cause.

## Live-call rule

No physical call was required for this closeout, and this handoff authorizes **no future call**.

Every future real call requires fresh user/operator authorization in that new session and must follow `docs/ROADMAP.md` + `docs/SECURITY_PRIVACY.md`.

## Ready prompt

Use `docs/NEXT_CHAT_PROMPT.md` or paste the prompt provided at the end of the closing chat response.