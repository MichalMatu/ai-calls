# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ to a hybrid deterministic + AI task engine without external audio hardware.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## CURRENT

The product direction is **Gate D: hybrid multi-turn Task Engine**.

The deterministic phone-call transport/fast path is already proven. The next milestone is not broader IVR mapping; it is a generic bounded task engine capable of completing real multi-turn jobs such as booking an appointment.

Current foundation:

- cellular RX/TX + fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned output approval: `DONE / PROVEN_S22`;
- deterministic `CallPlan + PhraseMatrix` path: `HOST_GREEN / LIVE PATH PROVEN_S22`;
- generic bounded service-intent resolver contract: `HOST_GREEN`;
- Orange deterministic RX -> STT -> CallPlan -> approved TTS -> TX: `PROVEN_S22`;
- Orange IVR knowledge: persistent checkpointed ServicePack, no longer the main roadmap.

The next main milestone is a generic data-driven `TaskGraph v1` with `BOOK_APPOINTMENT` as the first end-to-end task.

Target hybrid flow:

```text
natural user goal
 -> IdentityVault availability + per-task fact authorization
 -> CallTask + constraints/preferences/AuthorizedFactSnapshot
 -> TaskGraph
 -> deterministic PhraseMatrix/parsers first
 -> shadow LLM observer from the beginning when enabled
 -> explainable DialogueFit / escalation policy
 -> bounded LLM supervisor only when needed
 -> existing transition ID + typed non-secret slots
 -> deterministic validation
 -> FactDisclosurePolicy for identity data
 -> CallWorkflow / CallPlan / output approval
 -> proposal / confirmation / commitment
 -> structured completion
```

The model remains useful, but as a **bounded supervisor/classifier**, not the authority owner. It may track finalized dialogue in quarantined shadow mode and later suggest existing transitions or structured slot candidates. It must not directly own dialing, arbitrary speech release, plaintext identity facts, target widening, fact disclosure or commitments.

Primary acceptance use case:

```text
Umów mnie do dentysty w przyszłym tygodniu, najlepiej po 16.
```

The system should conduct a bounded real multi-turn call, parse offered appointment slots, reject unsuitable offers, request/disclose only authorized identity data, create a typed proposal, obtain required confirmation and release exactly one authorized commitment.

## Product layers

The product keeps three durable knowledge/data layers separate.

### TaskGraph

Describes **what the user wants to achieve** and how the bounded task progresses:

- typed states/events/transitions;
- slots and constraints/preferences;
- recovery;
- proposal/confirmation/commitment;
- completion/failure/takeover;
- replayable event/evidence semantics.

### ServicePack

Describes **how a specific service/counterparty behaves**:

- known prompts/nodes/edges;
- reviewed responses/actions;
- service IDs;
- barriers and risk;
- physical evidence and future freshness metadata.

Orange is the first persistent evidence-backed IVR ServicePack. Its mapping is preserved for future real Orange support, IVR regression and ServicePack standardization. It is not discarded as a one-off test.

### IdentityVault

Stores durable encrypted personal/contact data such as name, phone, email, address, date of birth or PESEL.

IdentityVault is not model memory and does not itself authorize disclosure. A task receives only per-task authorized field references/snapshot, and a dedicated `FactDisclosurePolicy` decides `ALLOW / ASK_USER / DENY` for a concrete request. Plaintext high-sensitivity values stay outside LLM context by default.

Keep separate:

```text
IdentityVault       = persistent encrypted values
CallTask            = per-task authorized fact references/snapshot
DialogueState       = transient facts learned during this call
```

A future Orange task can combine:

```text
CallTask
 + generic TaskGraph
 + Orange ServicePack
 + authorized fact references
 + deterministic matcher/parsers
 + bounded supervisor on ambiguity
 + existing authority owners
```

Knowing the route or having a value in the vault never authorizes disclosure or commitment by itself.

## Hybrid supervisor and DialogueFit

The LLM may observe every **finalized** counterparty turn from the beginning in shadow mode so escalation does not cold-start.

Shadow mode is non-authoritative. It may maintain a bounded interpretation/summary but cannot mutate TaskGraph, workflow, output speech, authorized facts or commitment state.

A separate application-owned `DialogueFit` policy decides when deterministic understanding is sufficient. It should combine explainable signals such as matcher confidence, parser completeness, current-state compatibility, negation/contradiction, repeated unknowns and deterministic-vs-shadow disagreement — not raw text similarity or one opaque LLM score.

Initial outcome classes:

```text
HIGH       -> deterministic path
UNCERTAIN  -> clarification / optional supervisor check
LOW        -> supervisor proposal required
BROKEN     -> recovery / TAKE_OVER / safe stop
```

Supervisor output remains bounded structured data: an existing transition ID, typed non-secret slot candidates and diagnostics/confidence. Everything is revalidated before authoritative state changes.

## TaskGraph engine decision

Before committing the runtime to a custom state-machine implementation, Gate D performs a small host-only comparison:

```text
minimal custom reducer
vs
KStateMachine
```

Both must pass the same contract tests for typed events/states, pure guards, recovery, proposal/confirmation/commitment, replayable evidence and side-effect separation. KStateMachine is adopted only if it materially reduces complexity without taking ownership of authority/evidence/persistence semantics. Only one core should survive the spike.

## Slot/fact extraction rule

Dialogue data follows two-phase semantics:

```text
extract candidate
 -> validate type/state/constraints/provenance/authorization
 -> commit to authoritative TaskState only after validation
```

A parser/LLM/NLU result never becomes authoritative merely because it is confident.

## Orange checkpoint

Durable Orange data remains in `service-packs/orange/service_tree.v1.json`.

Checkpoint state after the 2026-09-22 mapping session:

- verified physical nodes: `orange.root`, `orange.root.reprompt`, `orange.activation.clarification_barrier`;
- 19 verified observed root edges;
- 16 service seeds, all still `DISCOVERED`;
- no complete service route has `service_route_verified=true`;
- latest physical evidence: `chatgpt-orange-caller-id-restriction-info-live-v264-20260922`;
- deterministic route calls used `backend_generate_calls=0`, bounded `OBSERVE_ONLY` follow-up and cleanup to `IDLE`.

A verified observed edge records what physically happened. It does **not** imply that the intended service route is verified.

Continue Orange only when it supports a real Orange product task, validates a new generic ServicePack capability, or intentionally checks route freshness/regression.

## Real-world testing

After `BOOK_APPOINTMENT` is host-green against a simulated receptionist **and** required identity/fact-disclosure behavior is implemented, physical tests may use a small reviewed set of ordinary public reception/business numbers from current public sources.

For **test-only** calls, disclose at the start that this is an AI assistant test and ask whether a short non-booking test is acceptable. Do not wait until the end to say that the call should be ignored.

For a **genuine user-authorized appointment**, execute the real task through disclosure/proposal/confirmation/commitment policy; do not create a booking and then retract it merely because the call also served as a product test.

Default live-test budget is one meaningful call per organization. A second call is reserved for an early technical failure or explicit agreement to repeat. Do not use emergency/urgent/crisis lines or broad unsolicited calling campaigns.

See `docs/ROADMAP.md` and `docs/SECURITY_PRIVACY.md` for the full policy.

## FROZEN

The Samsung media implementation and invariants are frozen at the Phase 2D foundation. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching that path. `HOST_GREEN` never upgrades a hardware claim to `PROVEN_S22`.

The general-purpose phone-local llama.cpp model sweep and Edge Gallery/Gemma experiments are frozen as product directions. Interactive ChatGPT relay branches are historical/developer evidence, not the product orchestrator.

## Repository workflow

Durable product code/docs live on `main`. Local Agent control/evidence stays on `agent-control` when that execution path is used.

Canonical host gate:

```bash
bash scripts/verify_host.sh
```

Operational continuation is documented in:

- `docs/HANDOFF_NEXT_CHAT.md` — exact checkpoint and continuation state;
- `docs/NEXT_CHAT_PROMPT.md` — ready-to-paste bootstrap for a fresh chat;
- `docs/HANDOFF_PROTOCOL.md` — mandatory session close/transfer rules;
- `docs/ROADMAP.md` — authoritative execution order and priorities;
- `docs/ARCHITECTURE.md` — TaskGraph/ServicePack/IdentityVault/supervisor boundaries;
- `docs/ORANGE_MAPPING_RUNBOOK.md` — Orange-only evidence/mapping procedure when that side track is resumed.

Live-call authorization and Local Chat Bridge bindings are session-scoped and must never be inferred from repository documentation.
