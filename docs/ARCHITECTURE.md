# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to a bounded autonomous task engine while keeping ownership explicit across cellular media, speech conversion, deterministic dialogue routing, task/service knowledge, task/workflow authority, optional bounded supervisor observation/proposals, identity disclosure authority, user confirmation, commitment authorization, completion, takeover and fail-safe cleanup.

Counterparty speech, model output, ServicePack data, parsers, encrypted storage and diagnostic tools never widen authority by themselves.

## Frozen media boundary

`CallMediaSessionCoordinator` and the Samsung implementation under `privileged-helper/` remain `DONE / PROVEN_S22 / FROZEN`.

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching this layer. Gate D work must not redesign media merely to simplify task-engine integration.

## Authority owners

The following remain authoritative:

- `CallTask`, including constraints/preferences and task scope;
- `CallResolvedTarget`;
- `CallWorkflow` for proposal state, explicit user-decision state and structured completion;
- `CallConfirmationPolicy`;
- `CallCommitmentGate` for opaque one-shot proposal-bound commitment permits;
- application-owned output approval;
- application-owned `FactDisclosurePolicy` for personal-data disclosure.

TaskGraph, PhraseMatrix, parsers, ServicePacks, shadow observers, LLMs, encrypted storage and Skills may classify, persist or propose into those owners but do not replace them.

## Product knowledge/data layers

### TaskGraph

TaskGraph owns bounded conversational micro-state: typed state/event/transition IDs, legal state-compatible transitions, pure guards, validated non-secret dialogue slots, bounded recovery, orchestration state, terminal state, effects as returned data and versioned replay evidence.

TaskGraph does **not** own target authorization, telephony execution, arbitrary speech approval, identity plaintext, disclosure permission, user-confirmation authority, commitment permits or factual completion authority.

### ServicePack

ServicePack describes a specific service/counterparty environment: known prompts, nodes/edges, reviewed actions, barriers, evidence and future freshness metadata. Orange remains the first persistent evidence-backed ServicePack. A ServicePack does not authorize a task or commitment.

### IdentityVault and authorized facts

Keep three layers distinct:

```text
IdentityVault
  durable encrypted values

AuthorizedFactSnapshot
  typed field IDs available/authorized for this task

DialogueState / TaskGraph context
  transient validated non-secret facts learned in this call
```

A value existing in the vault is not permission to disclose it. `FactDisclosurePolicy` is application-owned. Plaintext identity values are resolved only after an application-owned disclosure decision allows it and stay outside supervisor context by default.

## TaskGraph engine decision

The production Gate D core is the minimal application-owned custom reducer: `CustomTaskGraphCore`.

KStateMachine was evaluated as a spike candidate and is not carried as a production runtime/dependency. Do not reopen this decision without new concrete capability evidence.

## Core Gate D components

### `TaskGraphCore.kt`

Typed IDs/state kinds, immutable snapshots/context, pure guarded transitions, stale generation/state/version checks, bounded recovery, effects-as-data and deterministic versioned replay.

### `TaskGraphApplyBridge.kt`

Explicit application-owned boundary from already validated candidate data to `TaskGraphCore.reduce()`.

Before constructing a typed event it re-checks graph version/current state, candidate generation, application transition/event mapping, transition legality, provenance policy, slot scope, dynamic slot authorization, required slot presence, schema/constraints and authority-bearing slot IDs.

Rejected candidates do not call the reducer. Accepted reductions expose only the new immutable snapshot, event record and effects as data. The bridge imports no telephony, IdentityVault, generic effect executor or commitment authority.

### `AppointmentInterpreter.kt` / `BookAppointmentSimulator.kt`

The reusable interpreter owns typed appointment extraction for explicit dates, anchored relative dates/weekdays, times/ranges, offers, accept/reject/alternative acts and identity-field request IDs. It has no hidden clock and no execution authority.

The host simulator composes those candidates with existing `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and `FactDisclosurePolicy` owners. Parser output stays candidate-only and follows `extract -> validate -> commit`.

### `PersistentIdentityVault.kt` / `AndroidIdentityVault.kt`

Host persistence core plus production Android adapters:

```text
PersistentIdentityVault
 -> AndroidIdentityVaultBlobStorage
      -> Context.noBackupFilesDir
      -> AtomicFile replacement
 -> AndroidKeystoreIdentityVaultAead
      -> AndroidKeyStore
      -> AES-256/GCM/NoPadding
      -> non-exportable SecretKey
      -> stable algorithm identity + AAD
```

Key creation occurs only when encrypting a new/empty record. Decryption requires an already-existing valid AES Keystore entry; a missing or invalid key fails closed and must not silently replace ciphertext or create a new key. Storage/key adapters expose no disclosure authority and log no plaintext secret values.

This boundary is `PROVEN_S22`: `AndroidIdentityVaultContractTest` was physically executed on `SM-S906B` / Android 16 with terminal marker `IDENTITYVAULT_S22_PROVEN=true`.

### `DialogueFit.kt` / `DialogueFitHysteresis.kt`

Explainable categorical fit signals/results plus bounded shadow observation/hypothesis types. Safety deterioration is immediate; improvement requires consecutive evidence. Neither policy has execution authority.

### `SupervisorProposalValidator.kt`

Fail-closed boundary from quarantined hypothesis to candidate data. It checks generation, transition scope, allowed non-secret slot IDs, authority-bearing slot names and confidence. An accepted `ValidatedSupervisorCandidate` is still not executable.

## Prepared product session and explicit Gate D activation

Base composition:

```text
CallWorkflow + target authorization
 + optional CallPlan
 + optional PhraseMatrix
 + optional TaskGraphDefinition
 + optional AuthorizedFactSnapshot
        |
        v
AndroidTextCallReadiness / LocalPhoneTextCallReadiness
        |
        v
LocalTextCallReadinessCoordinator
        |
        v
PreparedLocalTextCall
        |
        v
LocalTextCallSession
        |
        +-> existing deterministic CallPlan/PhraseMatrix routing
        +-> optional LocalTextCallGateDRuntime
```

`PreparedLocalTextCall` remains a one-shot ownership handoff. `LocalTextCallSession` owns finalized-turn deterministic dialogue context.

The public Android `LocalTextCallSession.create(...)` path intentionally creates neither a shadow observer nor a product apply binding. Gate D activation is explicit/internal and must be deliberately reviewed.

Two explicit internal modes exist:

1. host-only diagnostics shadow lifecycle using a fixed authoritative snapshot;
2. reviewed product integration using `LocalTextCallGateDProductBinding`.

They are mutually exclusive for one session instance.

## Shared finalized-text ingress

`LocalTextCallSession` has one shared finalized-turn processing path. The source may be either the normal speech pipeline after STT finalization or the explicit internal test/diagnostic method `injectSyntheticFinalTranscript(...)`.

```text
live audio
 -> STT final transcript ----+
                             |
synthetic finalized text ----+-> shared finalized-turn ingress
                                  -> PhraseMatrix / CallPlan
                                  -> deterministic Gate D interpretation
                                  -> optional bounded shadow
                                  -> current-state/application authorization re-check
                                  -> TaskGraphApplyBridge
                                  -> inert route/result/effects data
```

Synthetic input deliberately bypasses pipeline start, PCM ingestion, STT, backend generation and TTS/media output. It does not create a parallel state machine or authority store.

This boundary is `PROVEN_S22`: `AndroidGateDProductSyntheticInputContractTest` was physically executed on the target S22 with terminal marker `SYNTHETIC_GATE_D_S22_PROVEN=true`.

Changing the source of finalized text does not grant authority to dial, widen a target, disclose identity plaintext, release speech, approve a proposal or user confirmation, consume commitment authority or claim completion.

## Read-only Gate D runtime boundary

`LocalTextCallGateDRuntime` may bind one `CallTask`, graph and optional authorized-fact snapshot, create bounded shadow observations from authoritative state/context, expose legal transition IDs and authorized fact field IDs, and revalidate a shadow hypothesis through `SupervisorProposalValidator`.

It has no reducer, generic effect executor, telephony mutation, commitment consumption or plaintext IdentityVault API.

## Session-owned shadow lifecycle

`LocalTextCallGateDShadowLifecycle` owns a monotonically increasing session epoch. A newer finalized turn invalidates older queued shadow work. `cancel()` invalidates pending work; `close()` invalidates it and closes the executor. Observer/validator exceptions are contained and cannot change the already-selected deterministic route.

Ordinary diagnostics contain typed IDs, generations, validation status/reject reason and `DialogueFitResult`; they omit transcript text, task/fact plaintext, slot candidate values and model diagnostic values.

## Reviewed product integration

`LocalTextCallGateDProductBinding` is an explicit internal application-owned composition seam. It binds:

- a deterministic candidate interpreter;
- `TaskGraphApplyPolicy`;
- dynamic authorized slot IDs provider;
- apply-result listener;
- optional shadow observer/executor/diagnostics listener;
- optional bounded deterministic follow-up router;
- optional `CallCommitmentGate` for the reviewed BOOK_APPOINTMENT commitment boundary.

`LocalTextCallGateDProductIntegration` owns the current immutable TaskGraph snapshot for that session.

Base order:

```text
finalized turn
 -> application deterministic candidate interpretation
 -> provenance check + current-state/slot-authorization re-check
 -> TaskGraphApplyBridge
 -> accepted snapshot becomes current
 -> optional one-step deterministic follow-up through the same apply bridge

only when deterministic interpreter returns no candidate:
 -> bounded shadow observation from current snapshot
 -> optional quarantined observer
 -> SupervisorProposalValidator
 -> application slot-authorization provider
 -> TaskGraphApplyBridge final re-check
 -> accepted snapshot becomes current
```

A deterministic candidate that is stale/invalid/rejected fails closed; it does **not** fall through to shadow as a bypass. Cancel/close prevent queued shadow work from applying later.

## BOOK_APPOINTMENT owner composition

The production composition deliberately reuses existing owners instead of making TaskGraph an effect executor.

### Proposal

`CallPlanTurnCoordinator` evaluates a proposal through the existing `CallWorkflow` once. The already-computed exact proposal plus policy decision are passed as data into the Gate D finalized turn.

The TaskGraph `PROPOSE_APPOINTMENT` transition is policy-neutral: it stores the validated non-secret appointment candidate and enters `PROPOSAL`. The obsolete `BOOK_APPOINTMENT_EVALUATE_PROPOSAL` effect/bridge was removed so graph processing cannot independently call `workflow.evaluateProposal()` again.

When the existing policy decision requires a user decision, one bounded deterministic follow-up uses the same apply bridge to move `PROPOSAL -> CONFIRMATION`.

### Explicit user decision

`applyBookAppointmentUserDecision(...)` is an application-owned entry point separate from counterparty speech.

Before owner mutation it requires:

- active integration;
- TaskGraph state `CONFIRMATION`;
- `CallWorkflow` state `NEEDS_USER_DECISION`;
- exact pending workflow proposal;
- matching graph appointment candidate;
- current slot authorization re-check;
- an accepted, effect-free exact graph transition staged through `TaskGraphApplyBridge`.

Only then does the existing `CallWorkflow.approvePendingProposal()` or `rejectPendingProposal()` consume the user decision. The staged graph snapshot becomes current only after the owner mutation succeeds.

CONFIRM moves TaskGraph to `COMMITMENT` and remembers the exact proposal returned by the workflow owner. REJECT returns TaskGraph to `WAITING_OFFER` and clears the candidate. No commitment permit is issued by the user-decision method itself.

### Commitment authorization

`authorizeBookAppointmentCommitment()` is a second explicit application-owned boundary. It requires:

- active integration;
- a bound `CallCommitmentGate`;
- current TaskGraph state `COMMITMENT`;
- the exact proposal returned by the preceding workflow approval;
- the exact `CallWorkflow` owner that approved it still in `ACTIVE_NEGOTIATION` immediately before authorization;
- no permit previously issued by this integration and no already-active gate permit.

It then asks the existing `CallCommitmentGate` owner to issue one opaque authorization for that exact proposal. The integration stores the exact authorization it issued.

`CallCommitmentGate.revoke(authorization)` is token-scoped. Cancel/close/reject may revoke only the exact permit issued by this integration; a newer or foreign permit is not globally cleared by this boundary.

Permit issuance does **not** consume the permit, execute an external commitment, advance TaskGraph beyond `COMMITMENT` or call `CallWorkflow.complete(...)`.

This complete no-call owner chain is `PROVEN_S22`: `AndroidGateDBookAppointmentCommitmentContractTest` ran physically on `SM-S906B` / Android 16 at commit `90a161c760c8267bd5625cba37373e6af9f9b07e` with terminal marker `BOOK_APPOINTMENT_COMMITMENT_S22_PROVEN=true`. The test also proves stale workflow rejection and foreign-permit preservation on device while speech pipeline/PCM/backend remain untouched.

## Commitment consumption is not completion

The architecture keeps three facts separate:

```text
commitment authorization issued
 != commitment authorization consumed
 != counterparty/business success confirmed
```

`CallRealtimeCommitmentFunctionHandler` currently consumes a valid opaque authorization and responds with `{"commitment":"authorized"}`. It does not prove the appointment was booked and must not by itself cause `COMMIT_SUCCEEDED`.

Separately, `CallPlanTurnCoordinator` currently handles `CallPlanAction.COMPLETE` by calling `CallWorkflow.complete(...)` before Gate D product integration processes the finalized turn. That ordering is acceptable for the existing default deterministic path but is not yet a sufficient reviewed BOOK_APPOINTMENT completion boundary.

Before TaskGraph may transition `COMMITMENT -> COMPLETE` in the reviewed product path, implementation must define and TDD:

1. redacted one-shot evidence that the exact commitment authorization was consumed;
2. exact counterparty/business success evidence distinct from authorization consumption;
3. product-bound completion ordering that preserves `CallWorkflow` as completion owner and prevents completion before those checks;
4. unchanged public/default behavior unless explicit reviewed product wiring opts in.

No generic completion/effect executor should be introduced.

## Hard authority invariant

Neither the shadow lifecycle, product binding, TaskGraph reducer, synthetic finalized-text ingress nor Android vault may automatically:

- dial or widen a target;
- execute arbitrary graph effects;
- release model speech/TTS;
- resolve/disclose plaintext facts;
- approve a proposal outside existing policy owners;
- approve user confirmation;
- consume commitment authority;
- infer business success from permit consumption;
- claim completion authority.

The public Android session path also does not automatically opt into the reviewed product binding.

## Next implementation focus

1. design/TDD exact commitment-consumption evidence without treating consumption as success;
2. design/TDD reviewed BOOK_APPOINTMENT completion ordering around exact success evidence while preserving `CallWorkflow` as completion owner;
3. run targeted/canonical regressions and the minimal relevant Android/S22 no-call proof;
4. wire late plaintext disclosure only through `AuthorizedFactSnapshot -> FactDisclosurePolicy -> current task/target/state/generation -> optional user approval` if acceptance-task flow requires it;
5. stop before live dialing. A live call requires fresh explicit authorization for the concrete target and task in the current session.

## TAKE OVER and failure invariant

Failure moves toward deterministic fallback, local recovery, takeover or safe stop. Local cancellation must not wait on model/network acknowledgement before stopping AI output/media generation and invalidating pending inference generations.

## Evidence rule

- `HOST_GREEN` is host/CI evidence only;
- compiled/packaged instrumentation tests are not physical device proof;
- `PROVEN_S22` requires explicit reproduction on the target phone;
- current ADB reachability alone is not product proof;
- no-call S22 proof does not justify a live call;
- live-call authorization is session-scoped and must be freshly granted.
