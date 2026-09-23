# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to a bounded autonomous task engine while keeping authority explicit across media, dialogue state, identity disclosure, proposal policy, user confirmation, commitment and factual completion.

Counterparty text, model output, parsers, ServicePacks, encrypted storage and TaskGraph state never widen authority by themselves.

## Frozen media boundary

`CallMediaSessionCoordinator` and the Samsung implementation under `privileged-helper/` remain `DONE / PROVEN_S22 / FROZEN`.

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching this layer.

## Authority owners

- `CallTask` — task, constraints, preferences and authorized scope;
- `CallResolvedTarget` — exact target;
- `CallWorkflow` — proposal/user-decision state and structured terminal outcome;
- `CallConfirmationPolicy` — deterministic proposal policy;
- `CallCommitmentGate` — opaque one-shot permit bound to one exact proposal;
- application-owned `FactDisclosurePolicy` — personal-data disclosure;
- application-owned output approval — final speech release.

TaskGraph, CallPlan, PhraseMatrix, shadow/supervisor, ServicePack and IdentityVault provide bounded data to those owners but do not replace them.

## Product layers

### TaskGraph

`CustomTaskGraphCore` owns typed bounded conversational state, legal transitions, validated non-secret slots, recovery, immutable snapshots, effects-as-data and replay evidence.

`TaskGraphApplyBridge` is the ordinary candidate-to-reducer boundary and re-checks graph version, current state, generation, transition/event mapping, legality, provenance, slot scope, current authorization, required slots, schema and authority-bearing slot IDs.

TaskGraph does not own dialing, target authorization, plaintext identity, speech release, user confirmation, commitment permits or factual business completion.

### Identity

```text
IdentityVault
  durable encrypted values

AuthorizedFactSnapshot
  field IDs available/authorized for this exact task

TaskGraph/dialogue context
  transient validated non-secret facts
```

Plaintext is resolved only after:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> current task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

Android persistence uses app-private no-backup `AtomicFile` ciphertext plus Android Keystore AES-256/GCM and is `PROVEN_S22`.

## Shared finalized-text ingress

Normal STT-finalized text and explicit synthetic finalized text converge before deterministic product processing. Synthetic ingress bypasses pipeline start, PCM/STT, backend generation and TTS/media, adds no authority, and is `PROVEN_S22`.

The public Android `LocalTextCallSession.create(...)` path does not automatically bind reviewed Gate D product integration.

## Shadow and supervisor

`LocalTextCallGateDShadowLifecycle` invalidates stale work on newer turns, cancel and close. `SupervisorProposalValidator` converts quarantined hypotheses only into bounded candidate data after generation/transition/slot/confidence checks.

A deterministic candidate rejection fails closed and cannot fall through to shadow as a bypass.

## Reviewed BOOK_APPOINTMENT owner composition

### Proposal and user decision

`CallPlanTurnCoordinator` evaluates a proposal through the existing `CallWorkflow` once. Gate D receives the exact already-computed proposal/policy result as data.

`applyBookAppointmentUserDecision(...)` stages the exact graph transition, re-checks graph/workflow/proposal/slot authorization, then delegates to `CallWorkflow.approvePendingProposal()` or `rejectPendingProposal()`. The staged graph snapshot becomes current only after the workflow owner succeeds.

### Commitment authorization

`authorizeBookAppointmentCommitment()` requires graph `COMMITMENT`, the exact approved proposal, the exact approving workflow still in `ACTIVE_NEGOTIATION`, and no existing owned/gate permit. It then asks `CallCommitmentGate` for one opaque permit.

Cancellation/rejection/close revoke only the exact permit issued by this integration. Foreign/newer permits are not globally cleared.

### Consumption evidence

`CallRealtimeCommitmentFunctionHandler` emits `CallCommitmentConsumptionEvidence` only after `CallCommitmentGate.consume(...)` succeeds. The reviewed product binding records exact proposal-bound redacted consumption evidence; recording consumption does not advance TaskGraph and does not call `CallWorkflow.complete(...)`.

### Deferred COMPLETE

```text
APPLY_TO_WORKFLOW       default/public behavior
DEFER_TO_PRODUCT_OWNER explicit reviewed opt-in
```

For reviewed BOOK_APPOINTMENT wiring, deterministic COMPLETE remains structured data and the workflow stays `ACTIVE_NEGOTIATION` until factual completion is explicitly accepted.

### Factual completion owner

```text
permit issued
 != permit consumed
 != business success confirmed
```

`completeBookAppointment(outcome)` is the only reviewed BOOK_APPOINTMENT path allowed to own `COMMITMENT -> COMPLETE`.

It requires exact approved proposal/workflow, recorded exact consumption evidence, workflow still active, `CallOutcomeStatus.SUCCESS`, matching scheduled time/graph slot, matching known price/provider/location fields, current slot authorization, and an accepted effect-free staged `commit-complete` transition.

Ordering:

```text
validate exact consumed proposal + exact SUCCESS evidence
 -> stage TaskGraph COMMIT_SUCCEEDED via TaskGraphApplyBridge
 -> CallWorkflow.complete(outcome)
 -> only after workflow success commit staged TaskGraph COMPLETE snapshot
```

Generic deterministic or shadow `commit-complete` candidates are blocked in ordinary `applyCandidate()`. No generic effect/completion executor exists.

## Verification status

Host/canonical implementation is complete. The newest owner boundaries are now physically `PROVEN_S22` on `SM-S906B`, Android 16, without a cellular call.

Evidence:

```text
chatgpt-gated-s22-final-owner-proofs-v049-20260923
DEFERRED_COMPLETION_BINDING_S22_PROVEN=true
BOOK_APPOINTMENT_COMPLETION_S22_PROVEN=true
FINAL_GATE_D_S22_OWNER_PROOFS_GREEN=true

chatgpt-gated-s22-commitment-regression-v050-20260923
BOOK_APPOINTMENT_COMMITMENT_REGRESSION_S22_GREEN=true
```

This extends the previously proven IdentityVault, synthetic product ingress and BOOK_APPOINTMENT permit-issuance boundaries.

## Hard authority invariant

Neither TaskGraph, shadow/supervisor, CallPlan/PhraseMatrix, parser, storage, synthetic ingress nor IdentityVault may independently:

- dial or widen a target;
- release speech/TTS;
- resolve/disclose plaintext identity;
- approve a user decision;
- issue or consume commitment authority outside the existing gate;
- infer business success from permit consumption;
- complete the workflow/task.

## Next gate

Re-check and merge PR #5 to `main`, then delete `gate-d-taskgraph-core`. A live acceptance call remains a separate gate requiring fresh explicit authorization for one concrete target and task.
