# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ to a bounded autonomous task engine without external audio hardware.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## CURRENT

The active product direction is **Gate D: hybrid multi-turn Task Engine** with `BOOK_APPOINTMENT` as the first acceptance task.

The cellular/media foundation and deterministic fast path are already proven and remain frozen. Gate D now has a host-only product foundation, a host-green finalized-turn shadow lifecycle and a host-green explicit TaskGraph apply boundary.

Current Gate D implementation on the active work branch includes:

- application-owned `CustomTaskGraphCore` with typed states/events/transitions, pure guards, bounded recovery, effects-as-data and versioned deterministic replay;
- `BOOK_APPOINTMENT` TaskGraph plus deterministic receptionist simulator covering proposal, confirmation, one-shot commitment, recovery, cancellation/takeover and fact-disclosure decisions;
- typed `IdentityFieldId`, per-task `AuthorizedFactSnapshot` and application-owned `FactDisclosurePolicy` (`ALLOW / ASK_USER / DENY`);
- explainable categorical `DialogueFit` contract;
- bounded `ShadowDialogueObservation` / `ShadowDialogueHypothesis` contracts and fail-closed `SupervisorProposalValidator`;
- read-only `LocalTextCallGateDRuntime` owned by `LocalTextCallSession`;
- Android/LocalPhone readiness composition carrying `TaskGraphDefinition + AuthorizedFactSnapshot` through coordinator -> prepared call -> session;
- host-only `LocalTextCallGateDShadowLifecycle` attached to the real finalized-turn selector through an explicit test/host observer seam;
- explicit application-owned `TaskGraphApplyBridge` that re-checks graph version/state/generation, transition-to-event mapping, provenance, slot scope, authorization and schema before constructing an event and calling the reducer.

For an explicitly bound host shadow observer, the session computes the existing PhraseMatrix/CallPlan result first, then creates exactly one bounded observation, runs quarantined observer work, revalidates the hypothesis and emits redacted candidate/`DialogueFit` diagnostics. Session epochs invalidate stale queued turns; `cancel()` and `close()` invalidate pending work. Observer failure cannot change the deterministic route.

The apply bridge is deliberately separate from the observer/session lifecycle. Rejected candidates never reach `TaskGraphCore.reduce()`. Accepted reductions return the new snapshot, event record and graph effects as inert data only; the bridge has no workflow, speech/TTS, dialing/target, plaintext identity or commitment API. The public Android session path still does not bind a production shadow provider and does not automatically apply shadow output.

## Immediate next milestone

Finish **generic appointment interpretation** by extracting reusable typed parsers/normalizers from the host simulator for dates/relative dates/weekdays, times/time ranges, offered appointment candidates, accept/reject/alternative semantics and common identity-field requests. Add deterministic PhraseMatrix dialogue-act coverage where appropriate.

The interpretation path must continue to preserve:

```text
extract candidate
 -> validate type/state/constraints/provenance/authorization
 -> commit only through the explicit application-owned apply boundary
```

Parser, matcher or model output never becomes authoritative merely because it parsed successfully.

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

Existing authority owners remain authoritative: `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and application-owned output approval.

Identity values follow:

```text
IdentityVault            = persistent encrypted values
AuthorizedFactSnapshot   = per-task authorized field IDs/scope
DialogueState            = transient validated non-secret facts learned in this call
```

A vault value existing does not authorize disclosure. Plaintext high-sensitivity values stay outside model context by default.

## Frozen foundation

- cellular RX/TX + fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- deterministic `CallPlan + PhraseMatrix` path: `HOST_GREEN / LIVE PATH PROVEN_S22`;
- Orange deterministic RX -> STT -> CallPlan -> approved TTS -> TX: `PROVEN_S22`;
- Orange IVR knowledge: persistent checkpointed ServicePack, not the current main roadmap.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching Samsung media or `privileged-helper/`.

## Safety and execution invariants

- model/shadow output is candidate data only;
- `extract -> validate -> commit` for dialogue-derived facts/slots;
- only the explicit application-owned apply bridge may turn an already validated candidate into a typed TaskGraph event;
- graph effects remain data until an existing application owner consumes them;
- no model, Skill or reducer widens target, disclosure, speech or commitment authority;
- unknown/stale/authority-bearing supervisor output fails closed;
- ordinary diagnostics contain typed IDs/status, not transcript/identity/candidate plaintext;
- test-only real calls require disclosure/consent at the start;
- genuine tasks require fresh user authorization;
- no live-call authorization is inherited from documentation or a previous chat.

## Verification and workflow

Canonical host gate:

```bash
bash scripts/verify_host.sh
```

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
