# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ to a bounded autonomous task engine without external audio hardware.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## CURRENT

The active product direction is **Gate D: hybrid multi-turn Task Engine** with `BOOK_APPOINTMENT` as the first acceptance task.

The cellular/media foundation and deterministic fast path are already proven and remain frozen. Gate D now has a host-only product foundation rather than being only a design target.

Current Gate D implementation on the active work branch includes:

- application-owned `CustomTaskGraphCore` with typed states/events/transitions, pure guards, bounded recovery, effects-as-data and versioned deterministic replay;
- `BOOK_APPOINTMENT` TaskGraph plus deterministic receptionist simulator covering proposal, confirmation, one-shot commitment, recovery, cancellation/takeover and fact-disclosure decisions;
- typed `IdentityFieldId`, per-task `AuthorizedFactSnapshot` and application-owned `FactDisclosurePolicy` (`ALLOW / ASK_USER / DENY`);
- explainable categorical `DialogueFit` contract;
- bounded shadow observation/hypothesis contracts and fail-closed `SupervisorProposalValidator`;
- a read-only Gate D runtime owned by `LocalTextCallSession`;
- Android/LocalPhone readiness composition that can carry `TaskGraphDefinition + AuthorizedFactSnapshot` through coordinator -> prepared call -> session.

The session runtime is intentionally **read-only for Gate D** at this checkpoint. It can create a bounded shadow observation and revalidate a supervisor hypothesis, but it does not call `TaskGraphCore.reduce()`, execute graph effects, release speech, disclose plaintext identity values, authorize a target, approve a proposal or consume commitment authority.

## Immediate next milestone

Activate the already-bound read-only Gate D seam on finalized product turns without changing deterministic behavior:

```text
finalized transcript
 -> existing PhraseMatrix / CallPlan path remains authoritative
 -> session creates bounded shadow observation when Gate D is bound
 -> quarantined observer hypothesis
 -> SupervisorProposalValidator
 -> DialogueFit / comparison evidence
 -> candidate data only
```

The first integration slice must remain shadow-only: **no automatic TaskGraph reduction and no speech/workflow/commitment authority**. Only after that lifecycle/generation boundary is host-green should a later slice introduce an explicit application-owned bridge from validated candidate data to a typed TaskGraph event/reduction.

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
IdentityVault       = persistent encrypted values
CallTask            = per-task authorized fact references/snapshot
DialogueState       = transient facts learned during this call
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
- no model or Skill widens target, disclosure, speech or commitment authority;
- unknown/stale/authority-bearing supervisor output fails closed;
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