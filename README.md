# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ to deterministic service-pack logic and selectable AI classifiers without external audio hardware.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## CURRENT

The product direction is Gate C: evidence-backed deterministic service packs. Orange at exact target `510100100` is the first pack under active mapping.

Current foundation:

- cellular RX/TX + fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned output approval: `DONE / PROVEN_S22`;
- deterministic `CallPlan + PhraseMatrix` path: `HOST_GREEN`;
- controlled deterministic Orange RX -> STT -> CallPlan -> approved TTS -> TX: `PROVEN_S22`;
- generic bounded service-intent resolver contract: `HOST_GREEN`.

Durable Orange data is in `service-packs/orange/service_tree.v1.json`.

Checkpoint state after the 2026-09-22 mapping session:

- verified physical nodes: `orange.root`, `orange.root.reprompt`, `orange.activation.clarification_barrier`;
- 19 verified observed root edges;
- 16 service seeds, all still `DISCOVERED`;
- no complete service route has `service_route_verified=true`;
- the manual-network-selection wording reached the activation clarification barrier and is closed;
- all other currently closed reviewed diagnostic/informational wordings returned a known root reprompt;
- latest physical evidence: `chatgpt-orange-caller-id-restriction-info-live-v264-20260922`;
- latest reviewed speech: `Jak działa zastrzeganie numeru?`;
- `backend_generate_calls=0`, follow-up `OBSERVE_ONLY`, known root reprompt, cleanup to phone state `IDLE`.

A verified observed edge records what physically happened. It does **not** imply that the intended service route is verified.

## Service intent resolver

Generic product code lives in `app/src/main/kotlin/pl/michalmatu/aicallbridge/serviceintent/`.

```text
natural user request
 -> UserIntentClassificationModel over a bounded service catalog
 -> existing service_id or null + confidence
 -> ServiceRegistry revalidation
 -> ServiceIntentExecutionValidator
 -> only a VERIFIED route may become eligible for existing authority owners
```

The resolver has no Orange dependency and no dial/TTS/telephony authority. It rejects unknown IDs, cross-pack IDs, low confidence, stale generations and model metadata attempting to carry speech/action/target authority. A `DISCOVERED` service may classify successfully but execution remains `ROUTE_NOT_VERIFIED`.

Authority remains owned by `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and application-owned output approval.

## FROZEN

The Samsung media implementation and invariants are frozen at the Phase 2D foundation. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching that path. `HOST_GREEN` never upgrades a hardware claim to `PROVEN_S22`.

The general-purpose phone-local llama.cpp model sweep and Edge Gallery/Gemma experiments are frozen as product directions. Interactive ChatGPT relay branches are historical/developer evidence, not the product orchestrator.

## DEFERRED

- broader provider adapters for the service-intent classifier;
- a data-driven reviewed Orange action catalog, only when the current enum/repeated wiring becomes a demonstrated maintenance bottleneck;
- generalized multi-provider service packs after Orange establishes the pattern;
- local realtime-audio model experiments after the deterministic service-pack baseline.

## Repository workflow

Durable product code/docs live on `main`. Local Agent control/evidence stays on `agent-control`. The intended normal branch set is only these two branches unless another branch has an explicitly documented active purpose.

Always fetch the fresh daemon binding before creating a Local Agent task.

Canonical host gate:

```bash
bash scripts/verify_host.sh
```

Operational continuation is documented in:

- `docs/HANDOFF_NEXT_CHAT.md` — exact checkpoint and continuation state;
- `docs/ROADMAP.md` — authoritative execution order and priorities;
- `docs/ORANGE_MAPPING_RUNBOOK.md` — durable Orange evidence/mapping procedure.

There is intentionally no paste-ready overnight prompt. Live-call authorization is session-scoped and must never be inferred from repository documentation.
