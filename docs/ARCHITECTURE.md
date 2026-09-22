# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to a bounded autonomous task engine while keeping ownership explicit across cellular media, speech conversion, deterministic dialogue routing, task/service knowledge, task/workflow authority, optional bounded supervisor observation/proposals, identity disclosure authority, takeover and fail-safe cleanup.

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
- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- application-owned output approval;
- application-owned `FactDisclosurePolicy` for personal-data disclosure.

TaskGraph, PhraseMatrix, parsers, ServicePacks, shadow observers, LLMs, encrypted storage and Skills may classify, persist or propose into those owners but do not replace them.

## Product knowledge/data layers

### TaskGraph

TaskGraph owns bounded conversational micro-state: typed state/event/transition IDs, legal state-compatible transitions, pure guards, validated non-secret dialogue slots, bounded recovery, orchestration state, terminal state, effects as returned data and versioned replay evidence.

TaskGraph does **not** own target authorization, telephony execution, arbitrary speech approval, identity plaintext, disclosure permission, user-confirmation authority or commitment permits.

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

A value existing in the vault is not permission to disclose it. `FactDisclosurePolicy` is application-owned. Plaintext identity values should be resolved as late as practical and stay outside supervisor context by default.

## TaskGraph engine decision

The production Gate D core is the minimal application-owned custom reducer: `CustomTaskGraphCore`.

KStateMachine was evaluated as a spike candidate and is not carried as a production runtime/dependency. Do not reopen this decision without new concrete capability evidence.

## Current Gate D components

### `TaskGraphCore.kt`

Typed IDs/state kinds, immutable snapshots/context, pure guarded transitions, stale generation/state/version checks, bounded recovery, effects-as-data and deterministic versioned replay.

### `TaskGraphApplyBridge.kt`

Explicit application-owned boundary from already validated candidate data to `TaskGraphCore.reduce()`.

Before constructing a typed event it re-checks graph version/current state, candidate generation, application transition/event mapping, transition legality, provenance policy, slot scope, dynamic slot authorization, required slot presence, schema/constraints and authority-bearing slot IDs.

Rejected candidates do not call the reducer. Accepted reductions expose only the new immutable snapshot, event record and effects as data. The bridge imports no workflow, output, telephony, IdentityVault or commitment owner and performs none of those side effects.

### `AppointmentInterpreter.kt` / `BookAppointmentSimulator.kt`

The reusable interpreter owns typed appointment extraction for explicit dates, anchored relative dates/weekdays, times/ranges, offers, accept/reject/alternative acts and identity-field request IDs. It has no hidden clock and no execution authority.

The host simulator composes those candidates with existing `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and `FactDisclosurePolicy` owners. Parser output stays candidate-only and follows `extract -> validate -> commit`.

### `PersistentIdentityVault.kt`

Host persistence core for durable identity values. It owns a versioned encrypted envelope/payload around explicit `IdentityVaultBlobStorage` and `IdentityVaultAead` ports, typed/redacted `IdentitySecretValue`, associated-data binding, bounds/defensive copies, fail-closed decode/decrypt and explicit `DEVICE_BOUND_NO_BACKUP` semantics.

### `AndroidIdentityVault.kt`

Production Android implementation of the host vault ports:

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

Canonical CI compiles/packages the Android instrumentation contract for this adapter. Physical S22 execution is still separate evidence; this boundary is `HOST_GREEN`, not yet `PROVEN_S22`.

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

## Read-only Gate D runtime boundary

`LocalTextCallGateDRuntime` may bind one `CallTask`, graph and optional authorized-fact snapshot, create bounded shadow observations from authoritative state/context, expose legal transition IDs and authorized fact field IDs, and revalidate a shadow hypothesis through `SupervisorProposalValidator`.

It has no reducer, effect executor, workflow mutation, speech/TTS, target/dial, commitment or plaintext IdentityVault API.

## Session-owned shadow lifecycle

`LocalTextCallGateDShadowLifecycle` owns a monotonically increasing session epoch. A newer finalized turn invalidates older queued shadow work. `cancel()` invalidates pending work; `close()` invalidates it and closes the executor. Observer/validator exceptions are contained and cannot change the already-selected deterministic route.

The lifecycle now accepts either the original fixed snapshot seam or an application-owned current-snapshot provider. In reviewed product mode it may return an already `SupervisorProposalValidator`-accepted candidate to the application integration seam, but it still does not apply a graph event itself.

Ordinary `GateDShadowTurnDiagnostics` contains typed IDs, generations, validation status/reject reason and `DialogueFitResult`; it omits transcript text, task/fact plaintext, slot candidate values and model diagnostic values.

## Reviewed product shadow/apply integration

`LocalTextCallGateDProductBinding` is an explicit internal application-owned composition seam. It binds:

- a deterministic candidate interpreter;
- `TaskGraphApplyPolicy`;
- dynamic authorized slot IDs provider;
- apply-result listener;
- optional shadow observer/executor/diagnostics listener.

`LocalTextCallGateDProductIntegration` owns the current immutable TaskGraph snapshot for that session and follows this fixed order:

```text
finalized turn
 -> application deterministic candidate interpretation
 -> if candidate exists: provenance check + current-state/slot-authorization re-check
 -> TaskGraphApplyBridge
 -> accepted snapshot becomes current
 -> effects remain inert TaskGraphApplyResult data

only when deterministic interpreter returns no candidate:
 -> bounded shadow observation from current snapshot
 -> optional quarantined observer
 -> SupervisorProposalValidator
 -> application slot-authorization provider
 -> TaskGraphApplyBridge performs the final current-state/generation/slot re-check
 -> accepted snapshot becomes current
 -> effects remain inert data
```

A deterministic candidate that is stale/invalid/rejected fails closed; it does **not** fall through to shadow as a bypass. Cancel/close prevent queued shadow work from applying later.

The result listener is not an effect executor. Existing workflow/proposal/confirmation/commitment/output owners must consume any allowed effect through their own reviewed boundaries.

## Hard authority invariant

Neither the shadow lifecycle, product binding, TaskGraph reducer nor Android vault may automatically:

- dial or widen a target;
- execute graph effects;
- mutate `CallWorkflow` from arbitrary reducer output;
- release model speech/TTS;
- resolve/disclose plaintext facts;
- approve a proposal;
- approve user confirmation;
- consume commitment authority;
- claim completion authority.

The public Android session path also does not automatically opt into the reviewed product binding.

## Next implementation focus — Android/S22 proof and bounded owner wiring

The next evidence step is physical Android proof without a call:

1. execute the IdentityVault instrumentation contract on the S22;
2. exercise reviewed product binding/session behavior on Android/S22;
3. keep the public session path non-automatic;
4. only then wire the specific existing owners required for bounded `BOOK_APPOINTMENT`, one reviewed effect mapping at a time.

Do not introduce a generic effect executor.

## TAKE OVER and failure invariant

Failure moves toward deterministic fallback, local recovery, takeover or safe stop. Local cancellation must not wait on model/network acknowledgement before stopping AI output/media generation and invalidating pending inference generations.

## Evidence rule

- `HOST_GREEN` is host/CI evidence only;
- compiled/packaged instrumentation tests are not physical device proof;
- `PROVEN_S22` requires explicit reproduction on the target phone;
- current ADB reachability alone is not product proof;
- host-only Gate D contracts do not justify a live call;
- live-call authorization is session-scoped and must be freshly granted.
