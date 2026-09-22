# Agent workflow

This repository is single-developer and main-first. Durable product code and current documentation live on `main`; `agent-control` exists only for Local Agent task/result traffic when that execution path is used.

## Start of every work session

Read the current sources of truth:

1. `README.md` — current product state;
2. `docs/HANDOFF_NEXT_CHAT.md` — exact continuation checkpoint;
3. `docs/ROADMAP.md` — authoritative execution order;
4. `docs/HANDOFF_PROTOCOL.md` — how sessions are closed/transferred;
5. `docs/ARCHITECTURE.md` and `docs/SECURITY_PRIVACY.md` — boundaries and authority;
6. `docs/PHASE2D_FREEZE_2026-09-18.md` — before touching Samsung media behavior;
7. `service-packs/orange/service_tree.v1.json` and `docs/ORANGE_MAPPING_RUNBOOK.md` only when Orange/ServicePack work is actually resumed.

Fetch fresh `origin/main` before writes. If Local Agent / Local Chat Bridge is used, also fetch fresh `agent-control:.agent/status/daemon.json` and use only the fresh current-session binding.

Do not create a planning/status document for every experiment. Put durable decisions into the authoritative docs above and leave detailed history in Git commits and `.agent/results`.

## Current priority

The active product gate is **Gate D — hybrid multi-turn Task Engine**.

Primary acceptance use case:

```text
BOOK_APPOINTMENT
```

Target flow:

```text
natural user goal
 -> CallTask + constraints/preferences/authorized facts
 -> TaskGraph
 -> CallWorkflow
 -> deterministic PhraseMatrix/parsers first
 -> bounded LLM supervisor only for ambiguity/unknown
 -> existing transition ID + typed slot proposals only
 -> deterministic validation
 -> CallPlan / output approval
 -> proposal / confirmation / commitment
 -> structured completion
```

The deterministic cellular path is already physically proven on S22. Do not spend new work merely reproving RX -> STT -> CallPlan -> TTS -> TX.

### Immediate execution order

Follow `docs/ROADMAP.md`. In summary:

1. specify `TaskGraph v1` with typed states/transitions/slots/guards/recovery/event log;
2. implement `BOOK_APPOINTMENT` on host;
3. build a deterministic simulated receptionist harness;
4. add typed date/time/offer parsing and PhraseMatrix dialogue-act coverage;
5. prove proposal -> confirmation -> commitment -> completion in simulation;
6. add recovery/cancel/takeover/unauthorized-information cases;
7. add the bounded LLM supervisor interface;
8. prove invalid/stale/authority-bearing supervisor output fails closed;
9. integrate into a real product session owner;
10. only then run small, bounded real-world appointment tests;
11. add Skills after the TaskGraph/supervisor boundary is stable.

## Product knowledge layers

Keep these distinct.

### TaskGraph

TaskGraph describes the bounded user task: goal, states, transitions, slots, constraints, proposal/confirmation/commitment and completion.

TaskGraph is application-owned data/code. Runtime models may suggest only existing transition IDs and typed slot values.

### ServicePack

ServicePack describes a particular service/counterparty environment: known prompts, nodes, edges, reviewed responses, barriers, risk and evidence.

Orange is the first persistent evidence-backed IVR ServicePack. Its broad mapping is checkpointed, not discarded. Preserve it for future Orange product use, IVR regression and ServicePack schema/runtime development.

ServicePack knowledge never replaces TaskGraph/workflow/commitment authority.

## Hybrid supervisor rules

The LLM remains useful but does not own execution authority.

Allowed supervisor output is bounded structured data such as:

- existing TaskGraph transition ID;
- typed slot values;
- confidence/diagnostic metadata.

Every result must be revalidated against the current task, graph state, generation/session identity, slot schema, constraints and authority.

The supervisor must not directly own:

- dialing or target widening;
- arbitrary telephony speech release;
- credentials or sensitive facts;
- new transitions/actions/service IDs;
- purchases/bookings/other commitments;
- completion authority.

The existing `serviceintent/` resolver is the design precedent: bounded candidates, structured classification, stale-result rejection, unsafe-metadata rejection, authoritative registry revalidation and separate execution validation.

## Skills

Skills may be reintroduced after `TaskGraph v1` exists.

Preferred role:

```text
User request
 -> build/update bounded CallTask
 -> choose existing TaskGraph/service pack
 -> gather missing pre-call facts/preferences
 -> optionally suggest existing transition/slot
 -> existing application authority validates everything
```

Skills never bypass `CallWorkflow`, `CallPlan`, output approval, `CallConfirmationPolicy` or `CallCommitmentGate`.

## Authority invariants

Keep the existing owners; do not create a second authority store:

- `CallTask` owns immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` is concrete but does not widen a dial allowlist;
- `CallWorkflow` owns progress, proposals, user-decision state and terminal outcome;
- `CallConfirmationPolicy` evaluates one typed proposal;
- `CallCommitmentGate` owns one exact one-shot commitment permit;
- application-owned output approval remains mandatory before speech release/TTS/TX.

Models, matchers, TaskGraph supervisors, ServicePacks and Skills are proposal/classification/knowledge layers only unless an existing application authority owner explicitly validates the effect.

Only `CallPlanAction.SAY` carries speech text. Structured actions without text remain structured.

## TaskGraph discipline

Required properties:

- typed state and transition IDs;
- deterministic guards;
- typed slot schemas;
- explicit retry/recovery bounds;
- proposal/confirmation/commitment states;
- takeover/cancel/failure terminals;
- replayable event/evidence log;
- model suggestions can reference only existing graph transitions/slot fields.

Unknown/ambiguous/rejected model or matcher output fails closed and cannot silently become sensitive action or arbitrary speech.

## Real-world appointment tests

Real calls happen only after the relevant host/simulation slice is green.

Use a small reviewed target set of ordinary public business/reception numbers from current public sources. Do not use emergency, urgent-care, crisis or other critical-service lines for testing.

### Test-only calls

Disclose at the **start** that this is an AI assistant test and ask whether a short non-booking test is acceptable.

If the person declines, thank them and hang up.

A test-only call must not create or hold a real appointment, ticket, order or other commitment. Do not wait until the end to reveal that the interaction was only a test.

### Genuine user-authorized tasks

If the user genuinely wants the appointment, the call may execute the real task using only authorized facts and the normal proposal/confirmation/commitment path.

Do not create a real booking and then retract it merely because the call also served as a product test.

### Per-target budget

Default:

- one meaningful call per organization/reception;
- second call only after an early technical failure or explicit agreement to repeat;
- no repeated probing of the same staff to tune wording;
- no broad unsolicited call campaigns;
- retain target source, test mode, call count and structured outcome.

## Orange side track

Current Orange data is durable in `service-packs/orange/service_tree.v1.json`.

Checkpoint facts:

- 3 physically verified nodes including the activation clarification barrier;
- 19 verified observed root edges;
- 16 service seeds, all `DISCOVERED`;
- no complete service route verified;
- deterministic live path and cleanup physically proven.

Orange is now a persistent regression/evidence ServicePack and future product knowledge source. Resume it only when it exercises a generic product capability or supports a real Orange task.

Public operator documentation is seed/backlog evidence only. It never creates a `VERIFIED` physical route.

## Frozen / deferred boundaries

- Samsung cellular RX/TX path: `DONE / PROVEN_S22 / FROZEN`.
- `privileged-helper/` and physically proven media path: do not change during ordinary Gate D work.
- General-purpose phone-local llama.cpp product direction: frozen; infrastructure may be reused for bounded supervisor experiments.
- Edge Gallery/Gemma path: frozen experiment.
- Interactive ChatGPT relay: developer benchmark infrastructure only.
- Realtime audio models: later, after the hybrid text/task baseline works.

## Architecture discipline

- Behavior changes use TDD: RED -> prove intended failure -> minimal GREEN -> regressions.
- Establish root cause before fixing bugs.
- Prefer small cohesive modules over broad abstractions.
- Do not split frozen/safety-critical state machines merely to reduce line count.
- Diagnostic probes are evidence drivers, not product runtime owners.
- Do not turn `DiagnosticProbeActivity`, Orange runners or `LocalPhoneLlmLiveCallProbe` into the Gate D orchestrator.
- `LocalSpeechTextPipeline` owns speech lifecycle; `TextCallTurnController` owns complete-text approval/generation invalidation; TaskGraph/CallPlan/workflow ownership stays outside them.
- Resumed speech/new generation must invalidate stale model/candidate output.
- Host tests cannot create `PROVEN_S22` evidence.

## Local Agent / Local Chat Bridge

`MichalMatu/local-agent` is an optional execution worker, not the source of truth.

When Local Chat Bridge is active, obey the current chat's binding envelope exactly.

- Work only in the exact bound repository.
- Never infer/substitute another repository or copy an old `agent_binding` from docs/history.
- Every Local Agent task must use the current chat's exact fresh immutable binding.
- Check fresh daemon state before creating a task touching the same worktree/branch.
- Inspect terminal evidence; queued/ACK is not success.
- Do not aggressively poll healthy long-running tasks.
- Use direct GitHub edits for exact reviewable code/docs/data diffs.
- Use Local Agent for developer-machine commands, Gradle/Android tooling, tests, ADB/device state and physical gates.
- `.agent/tasks` and `.agent/results` stay on `agent-control`; never merge them into `main`.
- Never launch local Codex from a Local Agent task.
- Never restart Local Agent merely to bypass unclear evidence/root cause.
- If another repository is genuinely required, use an explicit bridge rebind and wait for the fresh bootstrap; never guess a repository ID.

Full session-transfer rules are in `docs/HANDOFF_PROTOCOL.md`.

## Branch policy

Work directly on `main` unless temporary isolation is genuinely required. Integrate verified work and delete temporary remote branches when no longer needed.

Historical local branch `chat-relay/orange-chatgpt-pump-v1` is intentionally preserved because it contains unique commits not present on remotes; it is not part of the active product path.

## Credentials and privacy

A standard OpenAI API key is host/backend-only. Never put it in Android source, APK/BuildConfig, Intent, ADB argv, phone storage or logs.

Do not retain raw PCM, call recordings, credentials or unrelated phone data by default. Prefer structured events/slots/outcomes and retain only the minimum text needed for explicit development evidence.

Do not invent missing sensitive information to satisfy a counterparty prompt.

## TAKE OVER

Required local-first ordering:

```text
stop accepting/releasing AI output
 -> abort telephony media generation
 -> stop STT/TTS/audio workers
 -> invalidate controller/model/supervisor generation
 -> best-effort cancel remote/local work
```

The first steps cannot wait for model/network acknowledgement.

## Completion gate

Before declaring a Gate D slice complete:

1. run targeted tests for the changed boundary;
2. run `bash scripts/verify_host.sh`;
3. run only the physical gate actually required by changed OEM/hardware/dialogue behavior;
4. verify evidence rather than infer success;
5. update only authoritative docs when gate/continuation status changes;
6. leave `main` clean;
7. for real business/reception tests, enforce disclosure/consent policy for test-only calls and the per-target call budget.

## Handoff / new-chat gate

When work moves to a new chat, follow `docs/HANDOFF_PROTOCOL.md`.

Mandatory outputs are:

- refreshed `docs/HANDOFF_NEXT_CHAT.md`;
- refreshed ready-to-paste `docs/NEXT_CHAT_PROMPT.md`;
- documentation consistent with `docs/ROADMAP.md`;
- no stale Local Agent binding or inherited live-call authorization in either file.
