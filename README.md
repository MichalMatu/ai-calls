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

Durable Orange data is in `service-packs/orange/service_tree.v1.json`. Verified physical root nodes are `orange.root` and `orange.root.reprompt`. Verified observed root edges currently include `list_capabilities`, `invoice_status`, `invoice_topic`, `internet_problem` and `roaming_info`. These edges record what physically happened and do **not** imply that the desired service route is verified.

Current service seeds include `orange.invoice.status`, `orange.internet.problem` and `orange.roaming.info`. They remain `DISCOVERED`, because the tested utterances reprompted at the root instead of reaching a verified service route.

Latest bounded physical roaming evidence: `.agent/results/chatgpt-orange-roaming-live-v151-20260922.json`. The reviewed speech was exactly `Roaming.`, `backend_generate_calls=0`, the next turn was `OBSERVE_ONLY`, Max returned the known root reprompt, and cleanup ended with phone state `IDLE`.

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

Host demo covered by tests:

```text
Mam problem z internetem w Orange
 -> orange.internet.problem
 -> ROUTE_NOT_VERIFIED
 -> no call / no speech / no commitment
```

Authority remains owned by `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and application-owned output approval.

## FROZEN

The Samsung media implementation and invariants are frozen at the Phase 2D foundation. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching that path. `HOST_GREEN` never upgrades a hardware claim to `PROVEN_S22`.

The general-purpose phone-local llama.cpp model sweep and Edge Gallery/Gemma experiments are frozen as product directions. Interactive ChatGPT relay branches are historical/developer evidence, not the product orchestrator.

## DEFERRED

- broader provider adapters for the service-intent classifier;
- a data-driven reviewed Orange action catalog, only when the current small enum becomes a real bottleneck;
- generalized multi-provider service packs after Orange establishes the pattern;
- local realtime-audio model experiments after the deterministic service-pack baseline.

## Repository workflow

Durable product code/docs live on `main`. Local Agent control/evidence stays on `agent-control`. Always fetch the fresh daemon binding before creating a Local Agent task.

Canonical host gate:

```bash
bash scripts/verify_host.sh
```

Current operational continuation is `docs/HANDOFF_NEXT_CHAT.md`. The prepared but intentionally **not started** maximum-10-hour Orange mapping prompt is `docs/ORANGE_OVERNIGHT_MAPPING_PROMPT.md`.
