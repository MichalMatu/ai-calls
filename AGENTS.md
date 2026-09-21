# Agent workflow

This repository is single-developer and main-first. Durable product code and current documentation live on `main`; `agent-control` exists only for Local Agent task/result traffic.

## Start of every work session

Read only the current sources of truth:

1. `README.md` for product state;
2. `docs/HANDOFF_NEXT_CHAT.md` for the exact continuation point;
3. `docs/ROADMAP.md` for gate status;
4. `service-packs/orange/service_tree.v1.json` during Orange Explorer work;
5. `docs/ARCHITECTURE.md` and `docs/SECURITY_PRIVACY.md` when changing boundaries;
6. `docs/PHASE2D_FREEZE_2026-09-18.md` before touching Samsung media behavior.

Fetch fresh `main` and fresh `agent-control:.agent/status/daemon.json` before writes/tasks. Trust the current Bridge envelope plus fresh daemon binding for repository identity; never copy a binding from historical prose.

Do not create a planning/status document for every experiment. Put durable decisions into the files above and leave detailed history in Git commits and `.agent/results`.

## Current priority

The active product gate is **Gate C / deterministic fast path**, now focused on the first evidence-backed **Orange Service Pack Explorer**.

The first controlled live proof is complete. `v115` physically proved the full Orange cellular RX -> local STT -> PhraseMatrix -> CallPlan -> application-owned approval -> local TTS -> cellular TX chain with `backend_generate_calls=0`, followed by an `OBSERVE_ONLY` turn. `v123` physically proved the same chain for the reviewed `invoice_status` utterance and observed that Max returned the same root reprompt instead of entering a verified invoice route.

Current durable Orange data is `service-packs/orange/service_tree.v1.json`.

Latest final host checkpoint for the current code/data slice:

```text
.agent/results/chatgpt-orange-service-tree-final-green-v125-20260921.json
```

It passed Orange tree/runner tests plus the full `bash scripts/verify_host.sh` gate on checkpoint `5b0f599b98aefcef2279490cb14824f70e63ea17`.

### Orange Explorer facts

- exact live target: `510100100`;
- Max is a voice intent router at the root, not a fixed DTMF menu;
- verified nodes: `orange.root`, `orange.root.reprompt`;
- verified observed edge `orange.root.list_capabilities -> orange.root.reprompt` from `v115`;
- verified observed edge `orange.root.invoice_status -> orange.root.reprompt` from `v123`;
- `orange.invoice.status` is `DISCOVERED`, not a verified service route;
- `OBSERVE_ONLY` has no speech-producing rules;
- the sentinel backend must remain at `backend_generate_calls=0`.

Known exact physically observed root-acquisition pre-roll fragments are:

```text
orange
jakości orange
5g jakości orange
```

Ignore them only in the bounded root-acquisition phase. Do not turn them into broad fuzzy rules. A bounded retry for `SpeechRecognizer ERROR_NO_MATCH(7)` is allowed only under the strict tested root-acquisition conditions. `OBSERVE_ONLY` must never use those retries.

### Next execution order

1. inspect the latest terminal Local Agent result and current Orange tree;
2. choose one unverified Orange capability seed;
3. define one exact reviewed non-committing utterance with RED tests;
4. minimal GREEN through the existing `CallPlan + PhraseMatrix` authority path;
5. run targeted regressions and `bash scripts/verify_host.sh`;
6. only with explicit current-session operator authorization, execute one bounded call to exact allowlisted target `510100100` and follow it with `OBSERVE_ONLY`;
7. persist the observed node/edge/result into `service_tree.v1.json`;
8. if a branch reaches authentication, customer data, payment, purchase, activation, tariff/contract change or another commitment barrier, record the barrier and continue another branch rather than finalizing it;
9. repeat until the service catalog is broad enough for a final natural-intent -> `service_id` -> verified deterministic route acceptance test.

Public Orange documentation is a seed backlog only. Never mark a route `VERIFIED` from web/docs evidence alone.

Do not use arbitrary free-text speech to explore. Each speech-producing action must map to one reviewed constant/product datum. Consider making the reviewed explorer catalog data-driven only after the current pattern is proven across several seeds; do not bypass CallPlan/output approval to make iteration faster.

## Frozen / deferred boundaries

- Samsung cellular RX/TX path: `DONE / PROVEN_S22 / FROZEN`.
- `privileged-helper/`: do not change during Gate C product/service-pack work.
- General-purpose phone-local llama.cpp path: frozen.
- Edge Gallery / Gemma 4 E2B / official Agent Skills: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`.
- Interactive ChatGPT relay: developer benchmark infrastructure only.
- Paid `OPENAI_TEXT` and `OPENAI_REALTIME_AUDIO`: deferred.

## Authority invariants

Keep the existing owners; do not create a second authority store:

- `CallTask` owns immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` is concrete but does not widen a dial allowlist;
- `CallWorkflow` owns progress, proposals, user-decision state and terminal outcome;
- `CallConfirmationPolicy` evaluates one typed proposal;
- `CallCommitmentGate` owns one exact one-shot commitment permit;
- application-owned output approval remains mandatory before speech release/TTS/TX.

Models, helpers, matchers, service-pack discovery tools and Agent Skills are proposal/classification-only. They must not own dialing, DTMF, credentials, sensitive-data authority, payments, purchases, activations, tariff/contract changes or commitments.

Only `CallPlanAction.SAY` carries speech text. Structured actions without text remain structured.

PhraseMatrix-specific invariants:

- PhraseMatrix requires a bound CallPlan in readiness/prepared product state;
- raw matcher ids never become authority;
- only validated CallPlan decision ids may become previous-turn context;
- unknown/ambiguous/rejected matches fail closed and cannot silently become sensitive action/model speech.

Orange Explorer-specific invariants:

- exact allowlisted target only;
- named reviewed actions only;
- no model generation on the deterministic fast path;
- root-acquisition exceptions are exact evidence-backed diagnostics, not semantic shortcuts;
- `TAKE_OVER` retains its semantic meaning; runner-level retry policy must be explicit and bounded;
- `OBSERVE_ONLY` cannot release speech;
- a physically observed edge may be `VERIFIED` while the corresponding service seed remains only `DISCOVERED` if the desired service route was not reached.

## Architecture discipline

- Behavior changes use TDD: RED -> prove intended failure -> minimal GREEN -> regressions.
- Establish root cause before fixing bugs.
- Prefer small cohesive modules over broad abstractions.
- Do not split frozen/safety-critical state machines merely to reduce line count.
- Diagnostic probes are evidence drivers, not product runtime owners.
- `LocalSpeechTextPipeline` owns speech lifecycle; `TextCallTurnController` owns complete-text approval/generation invalidation; product CallPlan/workflow ownership stays outside them.
- Resumed speech/new generation must invalidate stale model/candidate output.
- Host tests cannot create `PROVEN_S22` evidence.

## Local Agent

`MichalMatu/local-agent` is an execution worker, not the source of truth.

- `.agent/tasks` and `.agent/results` stay on `agent-control`; never merge them into `main`.
- Every task must use the current chat's exact immutable `agent_binding`.
- Check fresh daemon state before creating a task that touches the same worktree.
- Inspect terminal evidence; queued/ACK is not success.
- Current daemon contract requires explicit `resources` on tasks. For physical S22 work use the canonical lowercase resource `device:rfct70l7e8j`.
- Use Local Agent for Gradle, lint, host tests, ADB/device work and repository branch cleanup.
- Direct GitHub edits are appropriate for exact reviewable code/docs/data diffs.
- Never launch local Codex from a Local Agent task.

## Branch policy

Work directly on `main` unless temporary isolation is genuinely required. Integrate verified work and delete temporary branches. Keep only `main` plus infrastructure branches that have an active purpose (`agent-control`).

## Controlled live-call policy

Automated dialing/hangup is permitted only for an explicitly operator-defined allowlisted test destination and only when the **current chat/task** authorizes physical calls. Authorization from an older chat must not be assumed.

For Orange Explorer the only current live target is `510100100`. Keep one active cellular call, bounded retries/duration, and fail closed on target/device/media uncertainty. Never allow model/tool output to create or widen the dial allowlist.

No emergency, premium-rate or arbitrary short-code dialing. A runner may hang up the call it created; it must not terminate an unrelated pre-existing call without explicit authorization.

## Credentials and privacy

A standard OpenAI API key is host/backend-only. Never put it in Android source, APK/BuildConfig, Intent, ADB argv, phone storage or logs.

Do not retain raw PCM, call recordings, credentials or unrelated phone data by default. For controlled service discovery, retain only the minimum transcript/evidence needed to identify deterministic IVR nodes and transitions.

## Completion gate

Before declaring a product/service-pack behavior slice complete:

1. run targeted tests for the changed boundary;
2. run `bash scripts/verify_host.sh`;
3. run only the physical gate actually required by changed OEM/hardware/IVR behavior;
4. verify evidence rather than infer success;
5. update `service_tree.v1.json` for durable Orange evidence;
6. update only authoritative docs when gate/continuation status changes;
7. leave `main` clean and delete temporary work branches.
