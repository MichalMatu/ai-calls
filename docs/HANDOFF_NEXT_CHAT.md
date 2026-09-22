# Handoff — Gate D hybrid Task Engine

Date: 2026-09-22

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Local Agent control/evidence branch: `agent-control`

This file is the current session checkpoint. It is not live-call authorization and does not contain a reusable Local Agent binding.

The authoritative execution order is `docs/ROADMAP.md`. Session-transfer rules are `docs/HANDOFF_PROTOCOL.md`. A ready-to-paste prompt for the next chat is `docs/NEXT_CHAT_PROMPT.md`.

## Start here in the next chat

Read fresh, in this order:

1. `AGENTS.md`;
2. `README.md`;
3. this file;
4. `docs/ROADMAP.md`;
5. `docs/HANDOFF_PROTOCOL.md`;
6. `docs/ARCHITECTURE.md`;
7. `docs/SECURITY_PRIVACY.md`;
8. `docs/PHASE2D_FREEZE_2026-09-18.md` before touching Samsung media;
9. Orange files only if ServicePack/Orange work becomes relevant: `service-packs/orange/service_tree.v1.json` and `docs/ORANGE_MAPPING_RUNBOOK.md`.

Always fetch fresh `origin/main` before acting. Do not assume an SHA embedded in historical evidence is still HEAD.

## Active product goal

The project has intentionally pivoted from broad Orange IVR mapping to:

```text
Gate D — hybrid multi-turn Task Engine
```

Primary acceptance task:

```text
BOOK_APPOINTMENT
```

Example product goal:

```text
Umów mnie do dentysty w przyszłym tygodniu, najlepiej po 16.
```

The next session should start building the generic task engine, not resume broad Orange mapping.

## What is already proven

### Cellular/media foundation

Status:

```text
DONE / PROVEN_S22 / FROZEN
```

Physically proven Samsung S22+ path:

```text
VOICE_DOWNLINK
 -> app/local STT
 -> deterministic routing
 -> application-owned approval
 -> local TTS
 -> CALL_ASSISTANT / TELEPHONY_TX
```

Do not redesign the frozen media path for Gate D.

### Deterministic fast path

Status:

```text
HOST_GREEN / LIVE PATH PROVEN_S22
```

Orange physical evidence proved:

```text
cellular RX
 -> local STT
 -> PhraseMatrix
 -> CallPlan rule validation
 -> application-owned output approval
 -> local TTS
 -> cellular TX
 -> bounded next-turn handling
 -> cleanup to IDLE
```

The purpose of Gate D is not to re-prove this path; reuse it.

### Existing authority owners

Keep these as the only execution authority owners:

- `CallTask` / constraints / preferences / `authorizedFacts`;
- `CallResolvedTarget`;
- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- application-owned output approval.

Do not create a second authority store in TaskGraph, a ServicePack, LLM supervisor, Skill, matcher or diagnostic runner.

### Generic bounded classifier precedent

`app/src/main/kotlin/pl/michalmatu/aicallbridge/serviceintent/` is host-green and demonstrates the desired fail-closed model boundary:

```text
bounded candidate set
 -> structured model classification
 -> generation/confidence checks
 -> unsafe metadata rejection
 -> authoritative registry revalidation
 -> separate execution validator
```

Gate D's LLM supervisor should reuse this philosophy.

## Key architectural decision: TaskGraph + ServicePack

The product now has two complementary durable knowledge layers.

### TaskGraph

Describes **what the user wants and how the bounded task progresses**:

- typed states/transitions;
- required/optional slots;
- user constraints/preferences;
- authorized facts;
- recovery/retry;
- proposal;
- confirmation;
- commitment;
- completion/failure/takeover;
- replayable event/evidence log.

TaskGraph is generic where possible and independent of Orange.

### ServicePack

Describes **how a specific service/counterparty behaves**:

- known IVR prompts and variants;
- observed nodes/edges;
- reviewed responses/actions;
- service IDs;
- barriers/risk;
- evidence and future freshness metadata.

A ServicePack does not authorize a task or commitment.

## Orange is preserved, not abandoned

Orange is the project's first persistent evidence-backed IVR ServicePack.

Durable source:

```text
service-packs/orange/service_tree.v1.json
```

Current checkpoint:

- 3 physically verified nodes including the activation clarification barrier;
- 19 physically verified observed root edges;
- 16 service seeds, all `DISCOVERED`;
- no complete service route with `service_route_verified=true`;
- deterministic route calls proved `backend_generate_calls=0`, bounded `OBSERVE_ONLY` and cleanup to `IDLE`.

Orange broad mapping is **checkpointed**, because further root-edge accumulation is currently lower value than building the task engine.

Do not delete, flatten or treat this as disposable test data. Preserve it for:

- future real Orange user tasks;
- deterministic IVR navigation;
- IVR regression;
- ServicePack schema/runtime evolution;
- route freshness/staleness work;
- future operator/service catalogs.

Resume Orange only when a real Orange task or generic ServicePack feature justifies it. Follow `docs/ORANGE_MAPPING_RUNBOOK.md` when resumed.

## Gate D target architecture

```text
User goal
  -> Skill / bounded intent resolver
  -> CallTask + authorized facts + constraints + preferences
  -> TaskGraph
  -> CallWorkflow
  -> final STT
       -> deterministic PhraseMatrix / typed parsers first
       -> bounded LLM supervisor only on ambiguity/unknown
  -> existing transition ID + typed slot proposals only
  -> deterministic validation
  -> CallPlan / typed decision
  -> output approval
  -> TTS / telephony
  -> typed proposal
  -> user confirmation when required
  -> CallCommitmentGate
  -> completion
```

Hybrid rule:

- use deterministic logic for known/common turns;
- use LLM only where natural-language interpretation adds value;
- LLM output is structured proposal/classification only;
- every result is revalidated;
- LLM/Skill never directly gains dialing, arbitrary speech, credential, commitment or completion authority.

## Exact next implementation order

Do not invent a different sequence unless evidence requires it. Follow `docs/ROADMAP.md`.

The next chat should begin with **TaskGraph v1**, host-only first:

1. audit existing domain types (`CallTask`, `CallWorkflow`, CallPlan/coordinator, proposal/confirmation/commitment APIs) before adding new abstractions;
2. define the smallest TaskGraph v1 model that composes those owners rather than duplicating them;
3. write RED tests for typed state IDs, transitions, guards, slot schemas, bounded recovery and replayable event log;
4. minimal GREEN TaskGraph core;
5. implement the `BOOK_APPOINTMENT` graph on host;
6. add deterministic simulated receptionist scenarios;
7. add typed date/time/offer parsers and PhraseMatrix dialogue acts;
8. prove proposal -> confirmation -> commitment -> completion in simulation;
9. add ambiguity/recovery/unauthorized-data/takeover/cancel cases;
10. only then introduce the bounded LLM supervisor interface;
11. prove malicious/unknown/stale/authority-bearing supervisor output fails closed;
12. integrate with a real product session owner;
13. only after host/simulation is strong, move to small real-world reception tests;
14. add Skills after the TaskGraph/supervisor boundary is stable.

The first coding slice should therefore be **preimplementation audit + RED tests for TaskGraph v1**, not a live call.

## BOOK_APPOINTMENT minimum task flow

```text
START
 -> REQUEST_APPOINTMENT
 -> identify/confirm requested service
 -> collect/express date constraints
 -> collect/express time constraints
 -> receive offered slot
 -> parse candidate date/time
 -> validate constraints
    -> reject/request alternative
    -> or create typed proposal
 -> confirmation policy
 -> commitment gate
 -> COMMIT_APPOINTMENT
 -> COMPLETE
```

Required recovery cases include ambiguous date/time, unavailable slot, alternative offer, unexpected harmless question, STT uncertainty, request for unauthorized information, cancel/takeover, no suitable slot and explicit refusal.

## Real-world call policy for the next phase

Real calls are **not the first Gate D step**. First prove host simulation.

Later, use a small reviewed set of ordinary public reception/business numbers from current public web sources.

Two modes:

### `TEST_ONLY_CONSENTED`

Disclose the AI/test purpose at the start and ask whether a short non-booking test is acceptable.

If they decline: thank them and hang up.

Never create/hold a real appointment in test-only mode. Do not reveal only at the end that it was a test.

### `GENUINE_TASK`

If the user genuinely wants the appointment, execute the real task using only authorized facts and the normal proposal/confirmation/commitment path.

Do not retract a genuine booking merely because the call also produced development evidence.

Default per-target budget:

- one meaningful call per organization/reception;
- second call only after early technical failure or explicit agreement to repeat;
- no repeated probing of the same staff to tune wording;
- no broad unsolicited campaigns;
- never use emergency/urgent-care/crisis/critical-service lines for tests.

Live-call authorization remains session-scoped. This handoff authorizes **no future call**.

## Skills direction

Do not discard Skills; introduce them at the correct layer after TaskGraph v1.

Preferred Skill role:

```text
User request
 -> build/update bounded CallTask
 -> choose existing TaskGraph/ServicePack
 -> gather missing pre-call facts/preferences
 -> optionally suggest existing transition/slot
 -> application authority validates everything
```

Skills do not directly dial, widen allowlists, invent credentials, release arbitrary telephony speech or bypass commitment authority.

## Frozen/deferred areas

Do not casually touch:

- `privileged-helper/` / frozen Samsung media;
- physically proven `CallMediaSessionCoordinator` behavior;
- general-purpose phone-local llama.cpp product direction;
- Edge Gallery/Gemma experiment path;
- ChatGPT relay as product orchestration;
- realtime audio-model redesign before Gate D hybrid text/task baseline works.

Diagnostic probes/runners are evidence drivers only. Do not turn them into the Gate D orchestrator.

## Verification state at handoff

Last full stabilization seal before the Gate D documentation pivot:

```text
.agent/results/chatgpt-stabilization-checkpoint-v267-20260922.json
```

It proved:

```text
46 Orange Python tests: OK
bash scripts/verify_host.sh: GREEN
frozen media guard: GREEN
serviceintent neutrality guard: GREEN
FINAL_CALL_STATE=0
```

Final handoff sanity evidence:

```text
.agent/results/chatgpt-stabilization-handoff-final-v269-20260922.json
```

It proved:

```text
remote branches: main + agent-control
one active worktree
historical local chat-relay/orange-chatgpt-pump-v1 branch preserved with 21 unique commits
FINAL_CALL_STATE=0
```

All changes after the stabilized runtime/code checkpoint have been documentation/plan changes only. No Gate D runtime implementation has started yet.

Use fresh `origin/main` in the next chat and run verification appropriate to the first code slice before claiming new evidence.

## Repository/branch notes

Normal remote branch set:

```text
main
agent-control
```

Historical local branch:

```text
chat-relay/orange-chatgpt-pump-v1
```

It is intentionally preserved because it contains 21 unique commits not present on remotes. It has no active role in Gate D. Do not delete it casually until intentionally archived or judged disposable.

## Local Agent / Local Chat Bridge rules

Full rules are in `docs/HANDOFF_PROTOCOL.md` and `AGENTS.md`.

Critical rules for the next chat:

- if Local Chat Bridge is used, trust only the **fresh binding envelope injected into that new chat**;
- work only on the exact bound repository;
- never copy the current/old `agent_binding` from chat history, docs or old task JSON;
- every Local Agent task must contain exactly the new chat's current binding;
- inspect fresh daemon/current-task evidence before queueing work or writing the same worktree;
- terminal result evidence, not queued/ACK state, determines success;
- direct GitHub edits are preferred for exact reviewable diffs;
- Local Agent is for local builds/tests/Gradle/ADB/device actions;
- `.agent/tasks` and `.agent/results` stay on `agent-control`;
- never launch local Codex from Local Agent;
- never restart Local Agent merely to bypass an unclear failure;
- another repository requires explicit bridge rebind + fresh bootstrap; never infer/guess a repository ID.

No Local Agent binding is stored in this handoff on purpose.

## Handoff discipline going forward

This repository now has a permanent handoff process:

```text
docs/HANDOFF_PROTOCOL.md
```

Every major new-chat transfer should refresh:

```text
docs/HANDOFF_NEXT_CHAT.md
docs/NEXT_CHAT_PROMPT.md
```

The prompt is bootstrap text only; repository docs remain the source of truth.

## New-chat entry point

Paste the current contents of:

```text
docs/NEXT_CHAT_PROMPT.md
```

into the new chat. If Local Chat Bridge is used, allow it to provide the fresh binding envelope there before any Local Agent task is created.
