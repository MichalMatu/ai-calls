# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ to a bounded autonomous task engine without external audio hardware.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## CURRENT

The active product direction is **Gate D: hybrid multi-turn Task Engine** with `BOOK_APPOINTMENT` as the first acceptance task.

The cellular/media foundation and deterministic fast path are already proven and remain frozen. Gate D now has a host-green TaskGraph foundation, Android IdentityVault production adapter, finalized-turn shadow lifecycle, explicit reviewed product shadow/apply integration seam and a no-call synthetic finalized-text ingress for exercising the same product turn path without STT/audio/media.

Current Gate D implementation on the active work branch includes:

- application-owned `CustomTaskGraphCore` with typed states/events/transitions, pure guards, bounded recovery, effects-as-data and versioned deterministic replay;
- `BOOK_APPOINTMENT` TaskGraph plus deterministic receptionist simulator covering proposal, confirmation, one-shot commitment, recovery, cancellation/takeover and fact-disclosure decisions;
- typed `IdentityFieldId`, per-task `AuthorizedFactSnapshot` and application-owned `FactDisclosurePolicy` (`ALLOW / ASK_USER / DENY`);
- reusable generic `AppointmentInterpreter` preserving `extract -> validate -> commit`;
- explainable categorical `DialogueFit` plus evidence-backed hysteresis;
- bounded `ShadowDialogueObservation` / `ShadowDialogueHypothesis` contracts and fail-closed `SupervisorProposalValidator`;
- read-only `LocalTextCallGateDRuntime` owned by `LocalTextCallSession`;
- Android/LocalPhone readiness composition carrying `TaskGraphDefinition + AuthorizedFactSnapshot` through coordinator -> prepared call -> session;
- session-owned `LocalTextCallGateDShadowLifecycle` with stale/cancel/close invalidation and redacted diagnostics;
- explicit application-owned `TaskGraphApplyBridge` that re-checks graph version/state/generation, transition-to-event mapping, provenance, slot scope, authorization and schema before constructing an event and calling the reducer;
- host `PersistentIdentityVault` plus Android production adapters using app-private no-backup atomic ciphertext storage and Android Keystore AES-256/GCM;
- explicit internal `LocalTextCallGateDProductBinding` / product integration seam implementing deterministic-first interpretation, optional bounded shadow, `SupervisorProposalValidator`, current-state/slot-authorization re-check and `TaskGraphApplyBridge`;
- explicit `LocalTextCallSession.injectSyntheticFinalTranscript(...)` test/diagnostic ingress: an already-finalized text turn enters the same PhraseMatrix/CallPlan + Gate D finalized-turn processing as STT, without starting the speech pipeline, feeding PCM, invoking backend generation or releasing TTS/media;
- deterministic Gate D sequence corpus for ambiguity, recovery exhaustion, alternate offers, user rejection, unauthorized/high-sensitivity facts, stale supervisor, cancel/takeover and clean recovery.

The public Android `LocalTextCallSession.create(...)` path still does **not** automatically bind a shadow provider or product apply binding. Reviewed internal composition must opt in explicitly. The synthetic ingress is internal test/diagnostic plumbing, not a second authority path and not a public dialing API.

For the reviewed product seam, one finalized turn follows:

```text
STT-finalized text OR explicit synthetic finalized text
 -> shared finalized-turn ingress
 -> deterministic interpretation first
 -> optional bounded shadow proposal
 -> SupervisorProposalValidator
 -> application-owned current-state / slot-authorization re-check
 -> TaskGraphApplyBridge
 -> effects as inert data
 -> existing workflow / proposal / confirmation / commitment / output owners
```

A deterministic candidate rejection fails closed instead of falling through to shadow. Accepted graph effects are returned only as data; there is no generic effect executor and no new workflow, dialing, speech/TTS, plaintext disclosure, proposal approval or commitment authority.

## Immediate next milestone

Run Android/S22 integration proof for the boundaries that now exist but are only `HOST_GREEN`:

1. execute the Android IdentityVault instrumentation contract on the target S22 and prove Android Keystore key creation/reuse, non-exportability, no-backup storage, AES/GCM/AAD and fail-closed corruption/missing-key behavior;
2. exercise the reviewed product binding through Android/session integration without making a cellular call, using the synthetic finalized-text ingress where useful to bypass STT/media while still traversing the exact shared finalized-turn product path;
3. keep the public Android session path non-automatic unless a separately reviewed product composition intentionally supplies the binding.

Only after those device/integration checks should Gate D wire the bounded `BOOK_APPOINTMENT` effects to the existing proposal/confirmation/one-shot-commitment/disclosure owners and advance toward one bounded real-world task. Any live call requires fresh explicit authorization in that chat/session.

## Product layers

Keep these responsibilities separate:

```text
TaskGraph
  bounded task micro-state, legal transitions, validated non-secret slots,
  recovery, proposal/confirmation/commitment orchestration, replay evidence

ServicePack
  service/counterparty-specific prompts, routes, reviewed actions, barriers,
  physical evidence and future freshness metadata

IdentityVault
  durable encrypted personal/contact values; never task authority by itself
```

Existing authority owners remain authoritative: `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate`, `FactDisclosurePolicy` and application-owned output approval.

Identity values follow:

```text
IdentityVault            = persistent encrypted values
AuthorizedFactSnapshot   = per-task authorized field IDs/scope
DialogueState            = transient validated non-secret facts learned in this call
```

A vault value existing does not authorize disclosure. Plaintext high-sensitivity values stay outside model context by default and should be resolved only after the application-owned disclosure boundary allows it.

## Frozen foundation

- cellular RX/TX + fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- deterministic `CallPlan + PhraseMatrix` path: `HOST_GREEN / LIVE PATH PROVEN_S22`;
- Orange deterministic RX -> STT -> CallPlan -> approved TTS -> TX: `PROVEN_S22`;
- Orange IVR knowledge: persistent checkpointed ServicePack, not the current main roadmap.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching Samsung media or `privileged-helper/`.

## Safety and execution invariants

- model/shadow/parser output is candidate data only;
- `extract -> validate -> commit` for dialogue-derived facts/slots;
- only the explicit application-owned apply bridge may turn an already validated candidate into a typed TaskGraph event;
- graph effects remain data until an existing application owner consumes them;
- synthetic finalized-text input changes only the source of finalized transcript text; it grants no dialing, disclosure, speech, confirmation, commitment or completion authority;
- no model, Skill, parser, storage adapter or reducer widens target, disclosure, speech or commitment authority;
- unknown/stale/authority-bearing supervisor output fails closed;
- ordinary diagnostics contain typed IDs/status, not transcript/identity/candidate plaintext;
- test-only real calls require disclosure/consent at the start;
- genuine tasks require fresh user authorization;
- no live-call authorization is inherited from documentation or a previous chat.

## Verification and workflow

Canonical host/Android CI gate:

```bash
bash scripts/verify_host.sh
```

The gate compiles/packages Android instrumentation tests but does not itself execute them on the S22. Device execution is separate evidence and is required before calling a new Android boundary `PROVEN_S22`.

Operational sources of truth:

- `docs/ROADMAP.md` — authoritative execution order;
- `docs/ARCHITECTURE.md` — current component/authority boundaries;
- `docs/SECURITY_PRIVACY.md` — privacy and live-call safety invariants;
- `docs/HANDOFF_NEXT_CHAT.md` — exact current checkpoint;
- `docs/NEXT_CHAT_PROMPT.md` — ready-to-paste continuation prompt;
- `docs/HANDOFF_PROTOCOL.md` — session close/transfer rules;
- `docs/GATE_D_TASKGRAPH_V1_AUDIT_2026-09-22.md` — Gate D audit + implementation decision record;
- `docs/ORANGE_MAPPING_RUNBOOK.md` — Orange-only work when intentionally resumed.

Local Agent / Local Chat Bridge bindings and live-call authorization are session-scoped. Never copy either from old handoff text.