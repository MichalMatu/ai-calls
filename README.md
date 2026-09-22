# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ to a hybrid deterministic + AI task engine without external audio hardware.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## CURRENT

The product direction is now **Gate D: hybrid multi-turn Task Engine**.

Orange mapping is checkpointed as a proven evidence/test pack. It is no longer the main product goal.

Current foundation:

- cellular RX/TX + fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned output approval: `DONE / PROVEN_S22`;
- deterministic `CallPlan + PhraseMatrix` path: `HOST_GREEN / LIVE PATH PROVEN_S22`;
- generic bounded service-intent resolver contract: `HOST_GREEN`;
- controlled deterministic Orange RX -> STT -> CallPlan -> approved TTS -> TX: `PROVEN_S22`;
- Orange service-pack mapping checkpoint: `DONE AS TEST/EVIDENCE SLICE`.

The next main milestone is a generic data-driven `TaskGraph v1` with `BOOK_APPOINTMENT` as the first end-to-end task.

Target hybrid flow:

```text
natural user goal
 -> CallTask + constraints/preferences/authorized facts
 -> TaskGraph
 -> deterministic PhraseMatrix/parsers first
 -> bounded LLM supervisor only for ambiguity/unknown
 -> suggested existing transition + typed slots
 -> deterministic validation
 -> CallWorkflow / CallPlan
 -> output approval
 -> proposal / confirmation / commitment
 -> structured completion
```

The model remains useful, but it is a **supervisor/classifier**, not the authority owner. It may suggest existing transitions or structured slot values; it must not directly own dialing, arbitrary speech release, credentials, target widening or commitments.

Primary acceptance use case:

```text
Umów mnie do dentysty w przyszłym tygodniu, najlepiej po 16.
```

The system should be able to conduct a bounded real multi-turn call, parse offered appointment slots, reject unsuitable offers, create a typed proposal, obtain required confirmation and release exactly one authorized commitment.

## Orange checkpoint

Durable Orange data remains in `service-packs/orange/service_tree.v1.json`.

Checkpoint state after the 2026-09-22 mapping session:

- verified physical nodes: `orange.root`, `orange.root.reprompt`, `orange.activation.clarification_barrier`;
- 19 verified observed root edges;
- 16 service seeds, all still `DISCOVERED`;
- no complete service route has `service_route_verified=true`;
- latest physical evidence: `chatgpt-orange-caller-id-restriction-info-live-v264-20260922`;
- `backend_generate_calls=0`, follow-up `OBSERVE_ONLY`, cleanup to phone state `IDLE`.

A verified observed edge records what physically happened. It does **not** imply that the intended service route is verified.

Continue Orange only when it directly exercises a new generic product capability or a specific Orange route becomes a real product use case.

## Hybrid supervisor boundary

Generic classifier infrastructure currently lives in `app/src/main/kotlin/pl/michalmatu/aicallbridge/serviceintent/` and provides the precedent for the next supervisor boundary.

```text
bounded candidates
 -> model classification
 -> existing ID only
 -> generation/confidence checks
 -> authoritative registry revalidation
 -> separate execution validator
```

The same philosophy will be applied to TaskGraph supervision: structured proposals only, no direct execution authority.

Authority remains owned by `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and application-owned output approval.

## Real-world testing

After `BOOK_APPOINTMENT` is host-green against a simulated receptionist, physical tests may use a small reviewed set of ordinary public reception/business numbers found from current public sources.

For **test-only** calls, disclose at the start that this is an AI assistant test and ask whether a short non-booking test is acceptable. Do not wait until the end to say that the call should be ignored.

For a **genuine user-authorized appointment**, execute the real task through proposal/confirmation/commitment policy; do not create a booking and then retract it merely because the call also served as a product test.

Default live-test budget is one meaningful call per organization. A second call is reserved for an early technical failure or explicit agreement to repeat. Do not use emergency/urgent/crisis lines or broad unsolicited calling campaigns.

See `docs/ROADMAP.md` for the full Gate D execution order and test policy.

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
- `docs/ROADMAP.md` — authoritative execution order and priorities;
- `docs/ORANGE_MAPPING_RUNBOOK.md` — Orange-only evidence/mapping procedure when that side track is resumed.
