# Agent workflow

This repository is single-developer and main-first. Durable product code and current documentation live on `main`; `agent-control` exists only for Local Agent task/result traffic.

## Start of every work session

Read the current sources of truth in this order:

1. `README.md` — product/checkpoint state;
2. `docs/HANDOFF_NEXT_CHAT.md` — exact continuation point;
3. `docs/ROADMAP.md` — authoritative execution order and gate status;
4. `docs/ORANGE_MAPPING_RUNBOOK.md` — Orange evidence/mapping procedure;
5. `service-packs/orange/service_tree.v1.json` — current Orange physical/service evidence;
6. `docs/ARCHITECTURE.md` and `docs/SECURITY_PRIVACY.md` when changing boundaries;
7. `docs/PHASE2D_FREEZE_2026-09-18.md` before touching Samsung media behavior.

Fetch fresh `main` and fresh `agent-control:.agent/status/daemon.json` before writes/tasks. Trust the current Bridge envelope plus fresh daemon binding for repository identity; never copy a binding from historical prose.

Do not create a planning/status document for every experiment. Put durable decisions into the authoritative files above and leave detailed history in Git commits and `.agent/results`.

Repository documentation is not physical-call authorization. A live call requires explicit authorization in the current operator session.

## Current priority

The active product gate is **Gate C / deterministic fast path**, focused on the first evidence-backed **Orange Service Pack Explorer** plus the generic bounded `serviceintent/` resolver.

The first deterministic Orange cellular RX -> local STT -> PhraseMatrix -> CallPlan -> application-owned approval -> local TTS -> cellular TX proof is complete and physically proven. Do not repeat work whose only purpose is proving that base path again.

Current durable Orange state is always read from `service-packs/orange/service_tree.v1.json`; current checkpoint/continuation details are in `docs/HANDOFF_NEXT_CHAT.md`.

### Orange execution order

Use `docs/ORANGE_MAPPING_RUNBOOK.md` for the complete procedure. At a high level:

1. inspect the fresh handoff, tree, daemon/binding and latest terminal result;
2. choose one new low-risk unclosed capability seed;
3. define one exact reviewed non-committing utterance/action with RED tests;
4. implement minimal GREEN through existing `CallPlan + PhraseMatrix` authority;
5. run targeted regressions and `bash scripts/verify_host.sh`;
6. only with explicit current-session authorization, install the exact GREEN SHA and execute one bounded call to the exact allowlisted target followed by `OBSERVE_ONLY`;
7. persist only physically observed evidence into the service tree;
8. stop/record auth, credentials, payment, purchase, activation/deactivation, tariff/contract/package, ticket or other commitment barriers;
9. never repeat a closed reviewed utterance merely to collect duplicate evidence.

Public Orange documentation is a seed backlog only. It can create `DISCOVERED` candidates, never physical `VERIFIED` route evidence.

## Evidence semantics

Keep these claims separate:

- `HOST_GREEN` — deterministic host tests/build/lint passed;
- `PROVEN_S22` — the required behavior was physically reproduced on the target S22+;
- `DISCOVERED` service seed — capability known/touched but full route not proven;
- `VERIFIED` node/observed edge — exact physical node/transition was observed;
- `service_route_verified=true` — the intended complete deterministic route itself was physically reached.

A root reprompt can verify an observed edge while the service remains `DISCOVERED`.

## Frozen / deferred boundaries

- Samsung cellular RX/TX path: `DONE / PROVEN_S22 / FROZEN`.
- `privileged-helper/` and frozen Samsung media path: do not change during ordinary Gate C service-pack work.
- General-purpose phone-local llama.cpp path: frozen.
- Edge Gallery / Gemma / Agent Skills experiment: frozen / not product-ready.
- Interactive ChatGPT relay: developer benchmark infrastructure only.
- Paid/network provider work: deferred unless a later roadmap slice explicitly reopens it.

Do not refactor physically proven media for line count, naming or style cleanup.

## Authority invariants

Keep the existing owners; do not create a second authority store:

- `CallTask` owns immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` is concrete but does not widen a dial allowlist;
- `CallWorkflow` owns progress, proposals, user-decision state and terminal outcome;
- `CallConfirmationPolicy` evaluates typed proposals;
- `CallCommitmentGate` owns an exact one-shot commitment permit;
- application-owned output approval remains mandatory before speech release/TTS/TX.

Models, helpers, matchers, service-pack discovery tools and Agent Skills are proposal/classification-only. They must not own dialing, DTMF, credentials, sensitive-data authority, payments, purchases, activations, tariff/contract changes or commitments.

Only `CallPlanAction.SAY` carries speech text. Structured actions without text remain structured.

PhraseMatrix-specific invariants:

- PhraseMatrix requires a bound CallPlan in readiness/prepared product state;
- raw matcher ids never become authority;
- only validated CallPlan decision ids may become previous-turn context;
- unknown/ambiguous/rejected matches fail closed and cannot silently become sensitive action/model speech;
- do not broaden fuzzy matching merely to make live Orange prompts pass.

Orange-specific invariants:

- exact operator-defined allowlisted target only;
- named reviewed actions only;
- no model-generated runtime IVR speech;
- deterministic route tests require `backend_generate_calls=0`;
- root-acquisition exceptions are exact evidence-backed diagnostics, not semantic shortcuts;
- `OBSERVE_ONLY` cannot release speech;
- a physically observed edge may be `VERIFIED` while the corresponding service seed remains `DISCOVERED`.

## Architecture discipline

- Behavior changes use TDD: RED -> prove intended failure -> minimal GREEN -> regressions.
- Establish root cause before fixing bugs.
- Prefer small cohesive modules over broad abstractions.
- Do not split frozen/safety-critical state machines merely to reduce line count.
- Diagnostic probes/runners are evidence drivers, not product runtime orchestrators.
- `LocalSpeechTextPipeline` owns speech lifecycle; `TextCallTurnController` owns complete-text approval/generation invalidation; product CallPlan/workflow ownership stays outside them.
- Resumed speech/new generation must invalidate stale model/candidate output.
- Host tests cannot create `PROVEN_S22` evidence.
- The repeated Orange reviewed-action wiring may become data-driven only when it is a demonstrated maintenance bottleneck and all typed-ID/reviewed-speech/CallPlan/output-approval/allowlist/fail-closed invariants are preserved.

## Local Agent

`MichalMatu/local-agent` is an execution worker, not the source of truth.

- `.agent/tasks` and `.agent/results` stay on `agent-control`; never merge them into `main`.
- Every task must use the current chat's exact immutable `agent_binding`.
- Check fresh daemon state before creating a task that touches the same worktree.
- Inspect terminal evidence; queued/ACK is not success.
- Every task declares explicit `resources`; physical S22 work uses `device:rfct70l7e8j`.
- Use Local Agent for Gradle, lint, host tests, ADB/device work and local repository checks.
- Direct GitHub edits are appropriate for exact reviewable code/docs/data diffs.
- Never launch local Codex from a Local Agent task.

## Branch policy

Work directly on `main` unless temporary isolation is genuinely required. Integrate verified work and delete temporary branches when they no longer have an explicit purpose.

Normal steady state is:

```text
main
agent-control
```

Do not preserve dead experiment branches merely as history; Git commits and `.agent/results` are the history.

## Controlled live-call policy

Automated dialing/hangup is permitted only for an explicitly operator-defined allowlisted test destination and only when the **current chat/task** authorizes physical calls. Authorization from an older chat, documentation or Git history must not be assumed.

For the Orange Explorer the known test target is `510100100`, but repository documentation does not authorize calling it. When a current operator session does authorize it, keep one active cellular call, bounded retries/duration, deterministic reviewed speech and fail closed on target/device/media uncertainty.

No emergency, premium-rate or arbitrary short-code dialing. A runner may hang up the call it created; it must not terminate an unrelated pre-existing call without explicit authorization.

## Credentials and privacy

Do not invent or retain credentials to get through IVR barriers. Never fabricate PESEL, customer/contract identifiers, SMS codes, payment data or similar secrets.

A standard OpenAI API key is host/backend-only if paid work is ever resumed. Never put it in Android source, APK/BuildConfig, Intent, ADB argv, phone storage or logs.

Do not retain raw PCM, call recordings, credentials or unrelated phone data by default. For controlled service discovery, retain only the minimum transcript/evidence needed to identify deterministic IVR nodes and transitions.

## Completion gate

Before declaring a product/service-pack behavior slice complete:

1. run targeted tests for the changed boundary;
2. run `bash scripts/verify_host.sh`;
3. run only the physical gate actually required by changed OEM/hardware/IVR behavior and only with current-session authorization;
4. verify evidence rather than infer success;
5. persist durable Orange evidence in `service_tree.v1.json`;
6. update only authoritative docs when gate/continuation status changes;
7. leave `main` clean and remove temporary branches without active purpose;
8. if physical calls were made, leave the phone in `IDLE`.
