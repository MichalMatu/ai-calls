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

The durable owners are:

- `CallTask` — task, constraints, preferences and authorized scope;
- `CallResolvedTarget` — exact target;
- `CallWorkflow` — proposal/user-decision state and structured terminal outcome;
- `CallConfirmationPolicy` — deterministic proposal policy;
- `CallCommitmentGate` — opaque one-shot permit bound to one exact proposal;
- application-owned `FactDisclosurePolicy` — personal-data disclosure;
- application-owned output approval — final speech release.

TaskGraph, CallPlan, PhraseMatrix, shadow/supervisor, ServicePack and IdentityVault can provide bounded data to those owners but do not replace them.

## Product layers

### TaskGraph

The production Gate D core is the application-owned `CustomTaskGraphCore`. It owns typed bounded conversational state, legal transitions, validated non-secret slots, recovery, immutable snapshots, effects-as-data and replay evidence.

`TaskGraphApplyBridge` is the only ordinary candidate-to-reducer apply boundary. Before reducer entry it re-checks graph version, current state, generation, transition/event mapping, transition legality, provenance, slot scope, current slot authorization, required slots, schema and authority-bearing slot IDs.

TaskGraph does not own dialing, target authorization, plaintext identity, speech release, user confirmation, commitment permits or factual business completion.

### ServicePack

ServicePack stores service/counterparty knowledge. It never authorizes execution. Orange remains checkpointed future work, not the active roadmap.

### Identity

Keep storage, authorization and transient dialogue facts separate:

```text
IdentityVault
  durable encrypted values

AuthorizedFactSnapshot
  field IDs available/authorized for this exact task

TaskGraph/dialogue context
  transient validated non-secret facts
```

Plaintext is resolved only after an application-owned disclosure decision permits it:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> current task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

Android persistence uses app-private no-backup `AtomicFile` ciphertext plus Android Keystore AES-256/GCM. This vault boundary is `PROVEN_S22`.

## Shared finalized-text ingress

Normal STT-finalized text and explicit synthetic finalized text converge before deterministic product processing:

```text
live audio -> STT final ----+
                            +-> shared finalized-turn ingress
synthetic finalized text ---+     -> PhraseMatrix / CallPlan
                                  -> deterministic Gate D interpretation
                                  -> optional bounded shadow
                                  -> current authorization re-check
                                  -> TaskGraphApplyBridge
```

Synthetic ingress bypasses pipeline start, PCM/STT, backend generation and TTS/media. It adds no authority and is physically `PROVEN_S22` as a no-call product boundary.

The public Android `LocalTextCallSession.create(...)` path does not automatically bind reviewed Gate D product integration.

## Shadow and supervisor

`LocalTextCallGateDShadowLifecycle` is session-scoped and invalidates stale work on newer turns, cancel and close. `SupervisorProposalValidator` converts quarantined hypotheses only into bounded candidate data after generation/transition/slot/confidence checks.

A deterministic candidate rejection fails closed; it cannot fall through to shadow as a bypass.

## Reviewed BOOK_APPOINTMENT owner composition

### Proposal and user decision

`CallPlanTurnCoordinator` evaluates a proposal through the existing `CallWorkflow` once. Gate D receives the exact already-computed proposal/policy result as data.

The policy-neutral graph path is:

```text
WAITING_OFFER
 -> PROPOSAL
 -> CONFIRMATION when existing policy requires user decision
```

`applyBookAppointmentUserDecision(...)` is separate from counterparty speech. It stages the exact graph transition, re-checks current graph/workflow/proposal/slot authorization, then delegates to `CallWorkflow.approvePendingProposal()` or `rejectPendingProposal()`. The staged graph snapshot becomes current only after the workflow owner succeeds.

CONFIRM stores the exact owner-returned proposal and enters `COMMITMENT`; REJECT clears the candidate and returns to `WAITING_OFFER`.

### Commitment authorization

`authorizeBookAppointmentCommitment()` requires graph `COMMITMENT`, the exact approved proposal, the exact approving workflow still in `ACTIVE_NEGOTIATION`, and no existing owned/gate permit. It then asks `CallCommitmentGate` for one opaque permit.

Cancellation/rejection/close revoke only the exact permit issued by this integration. Foreign/newer permits are not globally cleared.

The owner chain through unconsumed permit issuance is `PROVEN_S22 (no-call)`.

### Consumption evidence

`CallRealtimeCommitmentFunctionHandler` consumes the opaque permit. Only after `CallCommitmentGate.consume(...)` succeeds does it emit `CallCommitmentConsumptionEvidence` containing the exact proposal with redacted diagnostics.

Reviewed composition connects that listener to `recordBookAppointmentCommitmentConsumption(...)`, which requires:

- active integration;
- graph `COMMITMENT`;
- exact approved proposal and exact workflow still active;
- an owned permit was previously issued;
- the gate no longer has an active authorization, proving the listener did not run before consume;
- exact proposal match;
- no previous consumption record.

Recording consumption does not advance TaskGraph and does not call `CallWorkflow.complete(...)`.

### Deferred COMPLETE

`CallPlanCompletionMode` preserves historic default behavior:

```text
APPLY_TO_WORKFLOW       default/public behavior
DEFER_TO_PRODUCT_OWNER explicit reviewed opt-in
```

For the reviewed BOOK_APPOINTMENT product binding, a deterministic COMPLETE turn remains structured data and the workflow stays `ACTIVE_NEGOTIATION` until factual completion is explicitly accepted.

### Factual completion owner

The final architecture keeps three facts separate:

```text
permit issued
 != permit consumed
 != business success confirmed
```

`completeBookAppointment(outcome)` is the only reviewed BOOK_APPOINTMENT path allowed to own `COMMITMENT -> COMPLETE`.

It requires:

- active integration and graph `COMMITMENT`;
- exact approved proposal/workflow;
- recorded exact consumption evidence for that proposal;
- exact workflow still `ACTIVE_NEGOTIATION`;
- `CallOutcomeStatus.SUCCESS`;
- outcome time equal to the approved proposal and graph appointment slot;
- if known in the proposal: matching currency/numeric price, provider and location;
- current slot-authorization re-check;
- an accepted, effect-free staged `commit-complete` transition producing graph `COMPLETE`.

Ordering is deliberate:

```text
validate exact consumed proposal + exact SUCCESS evidence
 -> stage TaskGraph COMMIT_SUCCEEDED via TaskGraphApplyBridge
 -> CallWorkflow.complete(outcome)        existing completion owner
 -> only after workflow success commit staged TaskGraph COMPLETE snapshot
```

A generic deterministic or shadow candidate with transition ID `commit-complete` is blocked in ordinary `applyCandidate()`. This prevents reducer/model/classifier output from gaining factual completion authority.

No generic effect/completion executor exists.

## Verification status

All no-phone code is complete and canonical-green. Final code checkpoint before documentation close-out:

```text
cefe6492c7e714a8124e08cb1f42a68554955832
```

Ready instrumentation contracts:

- `AndroidGateDDeferredCompletionBindingContractTest`;
- `AndroidGateDBookAppointmentCompletionContractTest`;
- existing `AndroidGateDBookAppointmentCommitmentContractTest` regression.

The first two are packaged but `PENDING_PHYSICAL`: the last S22 attempt stopped before Gradle because ADB had no connected device. Do not call them `PROVEN_S22` until terminal on-device evidence exists.

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

No further host feature slice is required before device proof. When the S22 is available, run the two pending focused no-call instrumentation contracts. If green, mark the boundary `PROVEN_S22`, re-check PR #5, merge to `main`, and delete the work branch. A live call remains a separate gate requiring fresh explicit target/task authorization.
