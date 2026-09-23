# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to a bounded autonomous task engine while keeping authority explicit across media, dialogue state, identity disclosure, proposal policy, user confirmation, commitment and factual completion.

Counterparty text, model output, model storage/import/readiness, parsers, ServicePacks, encrypted storage and TaskGraph state never widen authority by themselves.

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

TaskGraph, CallPlan, PhraseMatrix, model/Skills, model storage/import/readiness, shadow/supervisor, ServicePack and IdentityVault provide bounded data to those owners but do not replace them.

## Dialogue resilience

Finalized STT uses two bounded tracks:

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM: deterministic existing owner path
 -> unresolved/ambiguous/cold: Gemma 4 bounded skill classifier
 -> app-owned exact reviewed response
 -> error / low confidence / TAKE_OVER: injected-response / ChatRelay fallback
 -> application output approval
 -> TTS
```

Gemma may return only typed `skill/confidence/reason`; it does not own arbitrary speech or any call/business authority. ChatRelay remains developer/injected-response fallback infrastructure.

## Gemma 4 runtime and model lifecycle

The local provider is `LOCAL_GEMMA_4`; target model is `Gemma 4 E2B IT`, file `gemma-4-E2B-it.litertlm`, through direct in-process LiteRT-LM. A legacy stored provider value `EDGE_GALLERY` is migration input only. The obsolete Edge Gallery HTTP backend has been removed.

Runtime path:

```text
<app external files>/models/gemma-4-E2B-it.litertlm
 -> Gemma4LiteRtTextBackend
 -> LiteRtGemma4Runtime
 -> bounded dialogue skill policy
```

Application-owned import boundary:

```text
Android SAF source URI
 -> AndroidGemma4ModelImporter
 -> Gemma4ModelInstaller
 -> sibling app-owned staging file
 -> streaming SHA-256 against pinned reviewed identity
 -> flush + fsync
 -> atomic same-filesystem replacement
 -> active runtime model path
```

Pinned SHA-256:

```text
181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Activation is fail-closed. Empty/wrong/unreadable input, write failure or atomic-move failure cannot replace the previously active model. No non-atomic fallback is silently used. Edge Gallery and ADB may be development sources for bytes but are not runtime owners or production dependencies.

The lifecycle is **PROVEN_S22**. Physical proof exercised the app UI + Android SAF, exact SHA verification, app-owned destination replacement and post-import direct LiteRT inference. The filesystem accepted the required atomic replacement; staging was absent after success.

## Gemma model readiness

The product preparation boundary reuses one application-owned readiness result:

```text
app-owned active model + Gemma4ModelCatalog expected metadata
 -> MISSING / INVALID / READY
 -> LOCAL_GEMMA_4 product preparation
 -> only READY may continue to speech preflight/backend construction
```

This is intentionally cheaper than import-time identity verification: full SHA-256 is verified before activation, while ordinary readiness checks existence/readability/expected size. UI and product preparation use the same result. Backend object construction does not itself initialize LiteRT; native engine initialization remains lazy at generation time.

The readiness boundary is `HOST_GREEN / PROVEN_S22`. Developer/diagnostic backend constructors are evidence tooling, not alternate product readiness owners.

## Gemma reviewed acquisition transport

Network acquisition is a candidate-byte transport, not a new model owner:

```text
Gemma4ModelAcquisitionCatalog
  immutable HF repository revision + file + declared license/auth
 -> Gemma4ModelDownloader
  anonymous HTTPS streaming + HTTP/known-length checks
 -> Gemma4ModelInstaller
  app-owned staging + full expected bytes + SHA-256 + fsync + atomic activation
 -> active runtime model
```

The reviewed source currently resolves through Hugging Face to its CDN and supports byte ranges. A one-byte proof confirmed the pinned artifact total is 2,588,147,712 bytes. Mutable `main`, redirect metadata, `Content-Length` and the transport itself cannot override the pinned application model identity.

This source/downloader contract is `HOST_GREEN`; a full Android network transfer was not performed and no normal product download UI is exposed yet.

## TaskGraph and apply boundary

`CustomTaskGraphCore` owns typed bounded conversational state, legal transitions, validated non-secret slots, recovery, immutable snapshots, effects-as-data and replay evidence.

`TaskGraphApplyBridge` is the ordinary candidate-to-reducer boundary and re-checks graph version, current state, generation, transition/event mapping, legality, provenance, slot scope, current authorization, required slots, schema and authority-bearing slot IDs.

TaskGraph does not own dialing, target authorization, plaintext identity, speech release, user confirmation, commitment permits or factual business completion.

## Identity

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

## Finalized-text ingress and shadow

Normal STT-finalized text and explicit synthetic finalized text converge before deterministic product processing. Synthetic ingress bypasses pipeline start, PCM/STT, backend generation and TTS/media, adds no authority, and is `PROVEN_S22`.

The public Android `LocalTextCallSession.create(...)` path does not automatically bind reviewed Gate D product integration.

`LocalTextCallGateDShadowLifecycle` invalidates stale work on newer turns, cancel and close. `SupervisorProposalValidator` converts quarantined hypotheses only into bounded candidate data after generation/transition/slot/confidence checks. Deterministic rejection fails closed and cannot fall through to shadow as a bypass.

## Reviewed BOOK_APPOINTMENT owner composition

### Proposal and user decision

`CallPlanTurnCoordinator` evaluates a proposal through the existing `CallWorkflow` once. Gate D receives the exact already-computed proposal/policy result as data.

`applyBookAppointmentUserDecision(...)` stages the exact graph transition, re-checks graph/workflow/proposal/slot authorization, then delegates to `CallWorkflow.approvePendingProposal()` or `rejectPendingProposal()`. The staged graph snapshot becomes current only after the workflow owner succeeds.

### Commitment authorization

`authorizeBookAppointmentCommitment()` requires graph `COMMITMENT`, the exact approved proposal, the exact approving workflow still in `ACTIVE_NEGOTIATION`, and no existing owned/gate permit. It then asks `CallCommitmentGate` for one opaque permit.

Cancellation/rejection/close revoke only the exact permit issued by this integration. Foreign/newer permits are not globally cleared.

### Consumption evidence

`CallRealtimeCommitmentFunctionHandler` emits `CallCommitmentConsumptionEvidence` only after `CallCommitmentGate.consume(...)` succeeds. Recording exact proposal-bound redacted consumption evidence does not advance TaskGraph and does not call `CallWorkflow.complete(...)`.

### Deferred COMPLETE and factual completion

```text
APPLY_TO_WORKFLOW       default/public behavior
DEFER_TO_PRODUCT_OWNER explicit reviewed opt-in
```

For reviewed BOOK_APPOINTMENT wiring, deterministic COMPLETE remains structured data until factual completion is explicitly accepted.

```text
permit issued
 != permit consumed
 != business success confirmed
```

`completeBookAppointment(outcome)` is the only reviewed BOOK_APPOINTMENT path allowed to own `COMMITMENT -> COMPLETE`.

Ordering:

```text
validate exact consumed proposal + exact SUCCESS evidence
 -> stage TaskGraph COMMIT_SUCCEEDED via TaskGraphApplyBridge
 -> CallWorkflow.complete(outcome)
 -> only after workflow success commit staged TaskGraph COMPLETE snapshot
```

Generic deterministic or shadow `commit-complete` candidates are blocked in ordinary `applyCandidate()`. No generic effect/completion executor exists.

## Verification status

Gate D is `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.

Gemma direct no-call inference and the application-owned SAF import/verified atomic activation boundary are `HOST_GREEN / PROVEN_S22`. Physical bounded output after fresh import and again after provider cleanup was:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

## Hard authority invariant

Neither TaskGraph, model/runtime/model import/readiness, shadow/supervisor, CallPlan/PhraseMatrix, parser, storage, synthetic ingress nor IdentityVault may independently:

- dial or widen a target;
- release speech/TTS;
- resolve/disclose plaintext identity;
- approve a user decision;
- issue or consume commitment authority outside the existing gate;
- infer business success from permit consumption;
- complete the workflow/task.

## Next gate

The next generic engineering gate is explicit model-download lifecycle UX: user initiation, progress, cancellation and a reviewed retry/resume policy. Network acquisition must continue to terminate at `Gemma4ModelInstaller`; partial/download state cannot become runtime identity or activation authority. The full 2.59 GB transfer should be exercised physically only when that explicit product flow exists and is intentionally started.

A live acceptance call is a separate authority gate and requires fresh explicit authorization for one concrete target/number and one concrete task in the current session before any dialing action.
