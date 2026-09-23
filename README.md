# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ to a bounded autonomous task engine without external audio hardware.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## CURRENT

The active product direction is **Gate D: hybrid multi-turn Task Engine** with `BOOK_APPOINTMENT` as the first acceptance task.

The cellular/media foundation and deterministic fast path are already proven and remain frozen. Gate D now has a host-green TaskGraph foundation, physically proven Android IdentityVault and synthetic product boundaries, and a bounded `BOOK_APPOINTMENT` owner chain proven on the target S22 without making a cellular call.

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
- bounded `BOOK_APPOINTMENT` product composition that reuses the already-computed `CallWorkflow` proposal/policy decision, keeps `PROPOSE_APPOINTMENT` policy-neutral, stages explicit user confirm/reject through the same apply bridge, and issues an opaque one-shot `CallCommitmentGate` authorization only for the exact user-approved proposal;
- commitment hardening that re-checks the exact approving `CallWorkflow` immediately before permit issuance and revokes only the exact permit issued by this integration, so stale workflows fail closed and foreign/newer permits are not cleared accidentally;
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

For the reviewed `BOOK_APPOINTMENT` owner chain:

```text
CallPlan proposal
 -> existing CallWorkflow evaluates proposal once
 -> TaskGraph PROPOSAL (policy-neutral)
 -> bounded deterministic follow-up
 -> TaskGraph CONFIRMATION when user decision is required
 -> explicit app-owned user CONFIRM / REJECT boundary
 -> exact CallWorkflow pending proposal is consumed
 -> TaskGraph COMMITMENT only after CONFIRM
 -> exact active workflow re-check
 -> one opaque CallCommitmentGate permit for that exact proposal
```

A deterministic candidate rejection fails closed instead of falling through to shadow. Accepted graph effects are returned only as data; there is no generic effect executor and no new dialing, speech/TTS, plaintext disclosure, proposal-policy or completion authority.

## Physical no-call proof status

The following boundaries have been physically reproduced on the target Samsung S22+ (`SM-S906B`, Android 16) without making a cellular call:

- Android IdentityVault contract: `PROVEN_S22` — app-private no-backup ciphertext, AtomicFile replacement, Android Keystore AES-256/GCM, key create/reuse/non-exportability, AAD/algorithm identity and fail-closed corrupt/missing/invalid-key behavior;
- synthetic reviewed Gate D product ingress: `PROVEN_S22` — the same post-STT finalized-turn product path executes while speech pipeline start, PCM/STT, backend generation and TTS/media stay untouched;
- bounded `BOOK_APPOINTMENT` proposal -> confirmation -> explicit user confirmation -> commitment-authorization chain: `PROVEN_S22` at commit `90a161c760c8267bd5625cba37373e6af9f9b07e`; the device contract stops in TaskGraph `COMMITMENT` with workflow outcome still unset and the permit unconsumed.

These proofs do **not** authorize or imply a live call.

## Immediate next milestone

The remaining Gate D gap is the commitment/completion boundary. Keep these three facts distinct:

```text
1. commitment permit issued
2. commitment permit consumed
3. counterparty/business success actually confirmed
```

`CallRealtimeCommitmentFunctionHandler` currently consumes the opaque permit and reports only that commitment is authorized. That is not evidence that a booking succeeded. Likewise, `CallPlanTurnCoordinator` currently owns `CallPlanAction.COMPLETE` by calling `CallWorkflow.complete(...)`, so product-bound completion ordering must be reviewed before TaskGraph may transition `COMMITMENT -> COMPLETE`.

Next work must therefore be fail-closed and TDD-driven:

1. define bounded evidence that the exact commitment authorization was consumed, without treating consumption as success;
2. define a reviewed product-bound completion boundary that requires the exact successful completion evidence while preserving `CallWorkflow` as the completion owner;
3. keep public/default behavior unchanged unless explicit reviewed product wiring opts in;
4. run targeted/canonical regressions and no-call Android/S22 proof for any new boundary;
5. stop before dialing. Any live call requires fresh explicit authorization for the concrete target and task in the current chat/session.

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
- commitment authorization is one-shot, proposal-bound, workflow-rechecked and ownership-scoped;
- permit consumption is not business-success evidence;
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
