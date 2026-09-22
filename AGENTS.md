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
 -> IdentityVault availability + per-task fact authorization
 -> CallTask + constraints/preferences/AuthorizedFactSnapshot
 -> TaskGraph
 -> CallWorkflow
 -> deterministic PhraseMatrix/parsers first
 -> shadow supervisor observes finalized turns when enabled
 -> DialogueFit / escalation policy
 -> bounded LLM proposal only when needed
 -> existing transition ID + typed non-secret slots only
 -> deterministic validation
 -> FactDisclosurePolicy for identity data
 -> CallPlan / output approval
 -> proposal / confirmation / commitment
 -> structured completion
```

The deterministic cellular path is already physically proven on S22. Do not spend new work merely reproving RX -> STT -> CallPlan -> TTS -> TX.

### Immediate execution order

Follow `docs/ROADMAP.md`. In summary:

1. audit existing `CallTask`, `CallWorkflow`, CallPlan/coordinator and prepared-session ownership;
2. define TaskGraph contract tests first;
3. run the host-only custom reducer vs KStateMachine decision spike against those same tests and choose one core;
4. define `IdentityVault` / `IdentityFieldId` / `AuthorizedFactSnapshot` / `FactDisclosurePolicy` host contracts;
5. implement the chosen minimal TaskGraph core and `BOOK_APPOINTMENT`;
6. build deterministic simulated receptionist scenarios;
7. add date/time/offer/identity-request parsing with `extract -> validate -> commit` semantics;
8. prove disclosure + proposal -> confirmation -> commitment -> completion in simulation;
9. add recovery/cancel/takeover/unauthorized/high-sensitivity fact cases;
10. add shadow supervisor context tracking with zero execution authority;
11. implement/calibrate explainable `DialogueFit`;
12. add the bounded active supervisor proposal interface and fail-closed tests;
13. implement Android encrypted IdentityVault before a real call needs personal data;
14. integrate into a real product session owner;
15. only then run small, bounded real-world appointment tests;
16. add Skills after TaskGraph/supervisor/fact-disclosure boundaries are stable.

## Product knowledge/data layers

Keep these distinct.

### TaskGraph

TaskGraph describes the bounded user task: goal, states, transitions, slots, constraints, proposal/confirmation/commitment and completion.

TaskGraph is application-owned data/code. Runtime models may suggest only existing transition IDs and typed slot candidates.

### ServicePack

ServicePack describes a particular service/counterparty environment: known prompts, nodes, edges, reviewed responses, barriers, risk and evidence.

Orange is the first persistent evidence-backed IVR ServicePack. Its broad mapping is checkpointed, not discarded. Preserve it for future Orange product use, IVR regression and ServicePack schema/runtime development.

ServicePack knowledge never replaces TaskGraph/workflow/commitment authority.

### IdentityVault / authorized facts

IdentityVault stores durable encrypted identity/contact values. It is not model memory and not execution authority.

Keep separate:

```text
IdentityVault       = persistent encrypted values
CallTask            = per-task authorized fact references/snapshot
DialogueState       = transient facts learned during this call
```

A value existing in the vault does not authorize disclosure. `FactDisclosurePolicy` must make a typed `ALLOW / ASK_USER / DENY` decision. Plaintext identity values stay out of LLM context by default and should be resolved as late as practical.

High-sensitivity fields such as PESEL require explicit per-task authority and may require user/device authentication before disclosure.

## Hybrid supervisor rules

The LLM remains useful but does not own execution authority.

When enabled, it may observe finalized turns from the beginning in quarantined **shadow mode** so escalation is not cold-started. Shadow output cannot mutate authoritative state or release speech.

`DialogueFit` is application-owned and explainable. Do not implement it as raw text similarity or one opaque model-confidence value. Combine deterministic signals such as matcher result, parser completeness, state compatibility, contradiction/negation, repeated unknowns and deterministic-vs-shadow disagreement.

Allowed active supervisor output is bounded structured data such as:

- existing TaskGraph transition ID;
- typed non-secret slot candidates;
- confidence/diagnostic metadata.

Every result must be revalidated against the current task, graph state, generation/session identity, slot schema, constraints and authority.

The supervisor must not directly own:

- dialing or target widening;
- arbitrary telephony speech release;
- credentials, plaintext identity facts or disclosure authority;
- new transitions/actions/service IDs;
- purchases/bookings/other commitments;
- completion authority.

The existing `serviceintent/` resolver is the design precedent: bounded candidates, structured classification, stale-result rejection, unsafe-metadata rejection, authoritative registry revalidation and separate execution validation.

## Slot/fact extraction discipline

For parsed dialogue data use two-phase semantics:

```text
extract candidate
 -> validate type/state/constraints/provenance/authorization
 -> commit to authoritative TaskState only after validation
```

A parser, NLU system, LLM or matcher output is never authoritative merely because it is confident.

## TaskGraph engine dependency rule

KStateMachine is a valid Kotlin/Android candidate, not a preselected dependency.

Before adoption, compare a minimal custom reducer and KStateMachine against the same host contract tests. Choose one implementation only.

Adopt KStateMachine only if it materially reduces complexity while preserving:

- pure guards;
- explicit typed events/transitions;
- side effects outside transition evaluation;
- application-owned authority;
- explicit versioned event/evidence log;
- deterministic replay/testability.

Do not let framework runtime state become the only source of truth.

## Skills

Skills may be reintroduced after `TaskGraph v1` exists.

Preferred role:

```text
User request
 -> build/update bounded CallTask
 -> choose existing TaskGraph/ServicePack
 -> gather missing pre-call facts/preferences
 -> request authorization for specific IdentityFieldIds
 -> optionally suggest existing transition/slot
 -> existing application authority validates everything
```

Skills never bypass `CallWorkflow`, `CallPlan`, output approval, `FactDisclosurePolicy`, `CallConfirmationPolicy` or `CallCommitmentGate`, and never read/export the whole IdentityVault.

## Authority invariants

Keep the existing owners; do not create a second authority store:

- `CallTask` owns immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` is concrete but does not widen a dial allowlist;
- `CallWorkflow` owns progress, proposals, user-decision state and terminal outcome;
- `CallConfirmationPolicy` evaluates one typed proposal;
- `CallCommitmentGate` owns one exact one-shot commitment permit;
- application-owned output approval remains mandatory before speech release/TTS/TX;
- fact disclosure is a separate application-owned decision composing with `CallTask.authorizedFacts`.

Models, matchers, TaskGraph supervisors, ServicePacks and Skills are proposal/classification/knowledge layers only unless an existing application authority owner explicitly validates the effect.

Only `CallPlanAction.SAY` carries speech text. Structured actions without text remain structured.

## TaskGraph discipline

Required properties:

- typed state/event/transition IDs;
- deterministic pure guards;
- typed slot schemas;
- explicit retry/recovery bounds;
- proposal/confirmation/commitment states;
- takeover/cancel/failure terminals;
- versioned replayable event/evidence log;
- side effects separated from transition evaluation;
- model suggestions can reference only existing graph transitions/slot fields.

Unknown/ambiguous/rejected model or matcher output fails closed and cannot silently become sensitive action or arbitrary speech.

## Real-world appointment tests

Real calls happen only after the relevant host/simulation slice is green **and** the data-disclosure behavior required by that scenario is implemented.

Use a small reviewed target set of ordinary public business/reception numbers from current public sources. Do not use emergency, urgent-care, crisis or other critical-service lines for testing.

### Test-only calls

Disclose at the **start** that this is an AI assistant test and ask whether a short non-booking test is acceptable.

If the person declines, thank them and hang up.

A test-only call must not create or hold a real appointment, ticket, order or other commitment. Do not wait until the end to reveal that the interaction was only a test.

### Genuine user-authorized tasks

If the user genuinely wants the appointment, the call may execute the real task using only authorized facts and the normal disclosure/proposal/confirmation/commitment path.

Do not create a real booking and then retract it merely because the call also served as a product test.

### Per-target budget

Default:

- one meaningful call per organization/reception;
- second call only after an early technical failure or explicit agreement to repeat;
- no repeated probing of the same staff to tune wording;
- no broad unsolicited call campaigns;
- retain target source, test mode, call count and structured/redacted outcome.

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

Do not retain raw PCM, call recordings, credentials, plaintext identity values or unrelated phone data by default. Prefer structured events/slots/outcomes and retain only the minimum redacted text needed for explicit development evidence.

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
7. for real business/reception tests, enforce disclosure/consent policy, data minimization and the per-target call budget.

## Handoff / new-chat gate

When work moves to a new chat, follow `docs/HANDOFF_PROTOCOL.md`.

Mandatory outputs are:

- refreshed `docs/HANDOFF_NEXT_CHAT.md`;
- refreshed ready-to-paste `docs/NEXT_CHAT_PROMPT.md`;
- documentation consistent with `docs/ROADMAP.md`;
- no stale Local Agent binding or inherited live-call authorization in either file.
