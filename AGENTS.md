# Agent workflow

This repository is single-developer and main-first. Durable product code and current documentation live on `main`; `agent-control` exists only for Local Agent task/result traffic.

## Start of every work session

Read only the current sources of truth:

1. `README.md` for product state;
2. `docs/HANDOFF_NEXT_CHAT.md` for the exact continuation point;
3. `docs/ROADMAP.md` for gate status;
4. `docs/ARCHITECTURE.md` and `docs/SECURITY_PRIVACY.md` when changing boundaries;
5. `docs/PHASE2D_FREEZE_2026-09-18.md` before touching Samsung media behavior.

Fetch fresh `main` and fresh `agent-control:.agent/status/daemon.json` before writes/tasks. Trust the current Bridge envelope plus fresh daemon binding for repository identity; never copy a binding from historical prose.

Do not create a planning/status document for every experiment. Put durable decisions into the files above and leave detailed history in Git commits and `.agent/results`.

## Current priority

The active product gate is **Gate C / CallPlan v1 product wiring**.

Completed and `HOST_GREEN`:

- deterministic `CallPlan` policy core;
- bounded repeat/escalation, typed proposal/completion, authority regressions and bounded helper validation;
- `CallPlanTurnCoordinator`;
- prepared-call CallPlan binding;
- already-determined candidate text through the existing application-owned output approval;
- `CallPlanTextOutputRouter`;
- `CallPlanProductTurnRouter`;
- session-owned consecutive-unknown fallback state;
- neutral `TextCallFinalTurnDispatcher` with `Generate`, exact `Candidate`, and `Consumed` routes; `Consumed` invalidates stale controller/backend work.

The important remaining gap is explicit: `LocalSpeechTextPipeline` still sends final STT directly into the ordinary generative controller path. CallPlan is not yet automatically selected at that final-STT boundary.

### Next execution order

1. add a small host-testable product mapping from one `CallPlanTurnResult` to one `TextCallFinalTurnRoute`:
   - `SAY` -> exact `Candidate(text)`;
   - `ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, `TAKE_OVER` -> `Consumed` plus the exact structured result for the product owner;
   - never invent text and never silently fall through to a model;
2. keep the session-owned consecutive-unknown state as the only product counter owner;
3. after that mapper is `HOST_GREEN`, wire one optional final-turn route selector into `LocalSpeechTextPipeline` while preserving the no-plan default `Generate` path;
4. keep exactly one `TextCallTurnController` / approval / generation-cancellation path;
5. do not move CallPlan workflow authority into `localspeech`;
6. run targeted regressions and `bash scripts/verify_host.sh` before considering the slice complete.

No further Orange live-call authorization is currently available. Do not resume Edge Gallery live-call experiments by default.

## Frozen / deferred boundaries

- Samsung cellular RX/TX path: `DONE / PROVEN_S22 / FROZEN`.
- `privileged-helper/`: do not change during Gate C product wiring.
- General-purpose phone-local llama.cpp path: frozen.
- Edge Gallery / Gemma 4 E2B / official Agent Skills checkpoint: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`.
- Interactive ChatGPT relay: developer benchmark infrastructure only.
- Paid `OPENAI_TEXT` and `OPENAI_REALTIME_AUDIO`: deferred.

## Authority invariants

Keep the existing owners; do not create a second authority store:

- `CallTask` owns immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` is a concrete target but does not widen a dial allowlist;
- `CallWorkflow` owns progress, proposals, user-decision state and terminal outcome;
- `CallConfirmationPolicy` evaluates one typed proposal;
- `CallCommitmentGate` owns one exact one-shot commitment permit;
- application-owned output approval remains mandatory before speech release/TTS/TX.

Models, helpers and Agent Skills are proposal-only. They must not own dialing, DTMF, credentials, sensitive-data authority, payments, purchases, activations, tariff/contract changes or commitments.

Only `CallPlanAction.SAY` carries speech text. Structured actions without text must remain structured.

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
- Use Local Agent for Gradle, lint, host tests, ADB/device work and repository branch cleanup.
- Direct GitHub edits are appropriate for exact reviewable code/docs diffs.
- Never launch local Codex from a Local Agent task.

## Branch policy

Work directly on `main` unless temporary isolation is genuinely required. Integrate verified work and delete temporary branches. Keep only `main` plus infrastructure branches that have an active purpose (`agent-control`). Git history and Local Agent evidence are the bookmarks.

## Controlled live-call policy

Automated dialing/hangup is permitted only for an explicitly operator-defined allowlisted test destination and only when the current task authorizes a physical call. Keep one active cellular call, bounded retries/duration, and fail closed on target/device/media uncertainty. Never allow model/tool output to create or widen the dial allowlist.

No emergency, premium-rate or arbitrary short-code dialing. A runner may hang up the call it created; it must not terminate an unrelated pre-existing call without explicit authorization.

## Credentials and privacy

A standard OpenAI API key is host/backend-only. Never put it in Android source, APK/BuildConfig, Intent, ADB argv, phone storage or logs.

Do not retain raw PCM, call recordings, full transcripts, credentials or unrelated phone data by default. Prefer state, timing, sizes and sanitized failures.

## Completion gate

Before declaring a product slice complete:

1. run targeted tests for the changed boundary;
2. run `bash scripts/verify_host.sh`;
3. run only the physical gate actually required by changed OEM/hardware behavior;
4. verify evidence rather than infer success;
5. update only authoritative docs;
6. leave `main` clean and delete temporary work branches.
