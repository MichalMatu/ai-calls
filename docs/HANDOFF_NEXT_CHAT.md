# Handoff — Gate D BOOK_APPOINTMENT owner chain proven on S22, completion boundary next

Date: 2026-09-23

Repository: `MichalMatu/android-ai-call-bridge`

Durable base branch: `main`

Active work branch: `gate-d-taskgraph-core`

Pull request: `#5` — `Gate D TaskGraph v1 core` (draft)

This handoff is a state snapshot. It is **not** live-call authorization and contains no reusable Local Chat Bridge binding. Always fetch fresh branch/PR state before work; documentation commits may make the live HEAD newer than the code checkpoints below.

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

Orange remains checkpointed and is not the active target.

## Current product goal

Active milestone remains:

```text
Gate D — hybrid multi-turn Task Engine
```

Primary acceptance task remains:

```text
BOOK_APPOINTMENT
```

The current boundary is no longer Android proof or user confirmation. Those are complete. The next work is to separate commitment authorization consumption from factual business completion, then add exact success-evidence ordering without changing the public/default path.

## Physical S22 proof status

All three boundaries below are now physically reproduced on the target Samsung S22+ (`SM-S906B`, Android 16), without a cellular call:

```text
Android IdentityVault                         PROVEN_S22
synthetic reviewed Gate D product ingress     PROVEN_S22
BOOK_APPOINTMENT owner chain through permit   PROVEN_S22 (no-call)
```

### Android IdentityVault

Production checkpoint:

```text
3b79d42012d80c7cc6956bac77f590bfae20dc72
Android CI #494: success
```

Physical proof:

```text
Local Agent result:
chatgpt-gated-s22-identity-vault-proof-v004-20260923
IDENTITYVAULT_S22_PROVEN=true
```

This covers app-private `noBackupFilesDir` ciphertext storage, `AtomicFile` replacement, Android Keystore AES-256/GCM key create/reuse, non-exportability, algorithm/AAD binding, fail-closed corruption/unsupported/missing-or-invalid-key behavior and absence of plaintext secret in ordinary durable bytes/diagnostics.

### Reviewed synthetic Gate D product ingress

Core product checkpoint:

```text
a355f604484a78d7a99d7594455f3344ca081e04
Android CI #497: success
```

Synthetic ingress checkpoint:

```text
79e6dabd62cb325b41bc37615252165fe563c3e4
Android CI #500: success
```

Android contract checkpoint:

```text
313c6bb43251bc2cdd06e5e783a22154ac378f49
Android CI #505: success
```

Physical proof:

```text
Local Agent result:
chatgpt-gated-s22-synthetic-gated-product-proof-v005-20260923
SYNTHETIC_GATE_D_S22_PROVEN=true
```

The shared finalized-turn path remains:

```text
STT-finalized text OR explicit synthetic finalized text
 -> shared finalized-turn ingress
 -> deterministic interpretation first
 -> optional bounded shadow proposal
 -> SupervisorProposalValidator
 -> application-owned current-state / slot-authorization re-check
 -> TaskGraphApplyBridge
 -> existing application owners
```

Synthetic input bypasses speech-pipeline start, PCM/STT, backend generation and TTS/media. It adds no dialing, disclosure, confirmation, commitment or completion authority.

## BOOK_APPOINTMENT owner composition completed so far

The production composition deliberately reuses existing owners rather than introducing a generic effect executor.

### Proposal owner reuse

Checkpoint:

```text
656951e6e9603a8af9e4ff12ec4f0f355817b388
Android CI #518: success
```

The exact proposal and already-computed `CallWorkflow` policy decision are reused by Gate D. The proposal is not evaluated a second time.

### Policy-neutral proposal graph

Checkpoint:

```text
6d77d385170ca85feb40485800526e74f0bfaaa4
```

`PROPOSE_APPOINTMENT` stores validated non-secret proposal data and enters proposal state. TaskGraph does not own proposal-policy authority.

### Explicit user CONFIRM / REJECT

Checkpoint:

```text
2ffaf7dc696f5e21e7d77944e8f0af8d7105f039
Android CI #522: success
```

The app-owned user-decision boundary re-checks graph state/generation, slot authorization and the exact `CallWorkflow` pending proposal before owner mutation. Finalized counterparty/model text cannot approve the user decision.

### Commitment authorization

Checkpoint:

```text
061ed5071b127c9fb687e9532f7d1621f92fe1b5
Android CI #524: success
```

After explicit confirmation, the reviewed boundary may issue at most one opaque `CallCommitmentGate` permit for exactly the proposal returned by `CallWorkflow.approvePendingProposal()`. Issuing the permit does not consume it and does not claim completion.

### Commitment ownership hardening

Checkpoint:

```text
c5266423ad4a3cb3cbd8b245c7774e1378d9265e
Android CI #526: success
```

Hardening adds:

- immediate `CallWorkflowState.ACTIVE_NEGOTIATION` re-check before issuance;
- exact approving-workflow ownership;
- token-scoped revocation;
- protection against clearing a foreign/newer permit on reject/cancel/close;
- stale workflow fails closed.

### Physical S22 BOOK_APPOINTMENT owner-chain proof

Android instrumentation checkpoint:

```text
90a161c760c8267bd5625cba37373e6af9f9b07e
```

Physical proof:

```text
Local Agent result:
chatgpt-gated-s22-book-appointment-commitment-proof-v029-20260923
BUILD SUCCESSFUL
BOOK_APPOINTMENT_COMMITMENT_S22_PROVEN=true
```

The test physically proves proposal -> confirmation -> explicit user confirmation -> opaque one-shot permit issuance on `SM-S906B` / Android 16 while pipeline start, PCM, backend generation and media remain unused.

It deliberately stops at TaskGraph `COMMITMENT` with an unconsumed permit. It does **not** claim that a booking was executed or completed.

## Current authority stop line

Keep these facts separate:

```text
permit issued != permit consumed != business success confirmed
```

Neither synthetic ingress, model, parser, shadow, storage, TaskGraph reducer nor generic data flow may independently:

- dial or widen a target;
- resolve/disclose plaintext identity facts;
- release speech/TTS;
- approve a proposal;
- approve the user's confirmation;
- create or consume commitment authority outside the existing owner;
- claim factual business completion.

No generic effect executor exists or should be introduced.

Plaintext IdentityVault values remain late-bound through:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> current task / target / state / generation
 -> optional user approval
 -> plaintext resolution only after ALLOW
```

## Exact next order — still before any live call

### 1. Commitment-consumption evidence boundary

`CallRealtimeCommitmentFunctionHandler` currently consumes a valid one-shot permit and returns:

```json
{"commitment":"authorized"}
```

That means authorization was consumed. It does **not** mean the external business action succeeded.

Audit then TDD a narrow application-owned, redacted, proposal-bound, one-shot, stale-safe signal/evidence seam for exact BOOK_APPOINTMENT permit consumption. Do not make consumption drive `COMMIT_SUCCEEDED` or `CallWorkflow.complete(...)`.

### 2. Product-bound completion ordering

Audit before behavior change. `CallPlanTurnCoordinator` currently handles `CallPlanAction.COMPLETE` by calling `CallWorkflow.complete(...)` before Gate D observes that finalized turn.

For the reviewed BOOK_APPOINTMENT path, factual completion must require exact success evidence. Preserve `CallWorkflow` as completion owner. Keep the public/default coordinator behavior unchanged unless an explicit reviewed product composition opts into a safer completion path.

Only after exact success evidence may the TaskGraph move `COMMITMENT -> COMPLETE` and the workflow record the matching structured outcome.

### 3. Verification

For every consumption/completion slice:

1. RED contract;
2. prove the expected failure;
3. minimal GREEN;
4. targeted regressions;
5. `bash scripts/verify_host.sh`;
6. Android CI;
7. minimal relevant no-call S22 instrumentation proof if the Android/product boundary changed.

### 4. Late disclosure wiring only if required

Do not widen disclosure scope just to complete Gate D. Use the existing `AuthorizedFactSnapshot -> FactDisclosurePolicy` boundary and resolve plaintext only after ALLOW.

### 5. STOP before live call

Do not dial as part of preparation. A real call requires fresh explicit authorization for the concrete target and task in the current chat/session. Old docs, old Local Agent results, a connected phone or prior calls are not authorization.

## Frozen boundaries

Do not casually change:

- `privileged-helper/`;
- Samsung cellular/media path;
- physically proven `CallMediaSessionCoordinator`.

If `CallRealtimeMediaSessionTest.pumpFailureTriggersWholeGenerationCleanupBeforeTransportClose` reappears, inspect test ordering/synchronization/test pollution first. The known prior root cause was a test milestone race and the correction was test-only.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Local Agent / Local Chat Bridge

For a later session:

- trust only the fresh binding envelope injected into that chat;
- never copy an old `agent_binding` from this file, history, Git or old tasks;
- inspect fresh daemon/current-task evidence before local work;
- work only in the exact bound repository;
- use GitHub for bounded reviewable source/docs/data diffs;
- use Local Agent for local Gradle/Android tooling and physical device evidence;
- queue/ACK is not success — require terminal task result;
- never launch local Codex from a Local Agent task.

No binding is persisted here intentionally.

## New-chat bootstrap

Use `docs/NEXT_CHAT_PROMPT.md`.
