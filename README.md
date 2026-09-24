# AI Calls

Android prototype for completing bounded real-world tasks over ordinary cellular calls on a stock Samsung Galaxy S22+ (`SM-S906B`).

## Product

AI Calls is a **generic autonomous phone task engine**, not an Orange bot and not an appointment-only bot.

```text
user task
 -> exact target + constraints + authorized facts
 -> cellular call
 -> STT
 -> deterministic task state / PhraseMatrix
 -> Gemma 4 bounded dialogue skills
 -> live supervisor fallback when needed
 -> application output approval
 -> TTS/TX
 -> typed external-effect authority when a real-world state change is needed
 -> factual success evidence
 -> independent state verification when available
 -> workflow completion
```

Examples: enable a carrier service, ask for availability, book or change an appointment, cancel a reservation, resolve a bounded service request, or make a read-only information call.

## Proven foundation

Keep these closed unless a concrete root cause requires reopening them:

- Samsung cellular RX/TX + `CallMediaSessionCoordinator` — `PROVEN_S22 / FROZEN`;
- `privileged-helper/` / Shizuku media boundary — `PROVEN_S22 / FROZEN`;
- local Polish STT/TTS — proven on S22;
- IdentityVault disclosure boundary — proven;
- Gemma 4 LiteRT-LM runtime and app-owned model lifecycle — proven;
- `BOOK_APPOINTMENT` Gate D flow — proven;
- generic `CallExternalEffect` authority with one shared `CallCommitmentGate` — host/no-call proven;
- `SET_SERVICE(CLIR=true)` validation, one-shot permit lifecycle and separate external-success evidence — host/no-call proven;
- full synthetic/no-call product chain on S22 — proven.

Current local model:

```text
provider=LOCAL_GEMMA_4
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
runtime=LiteRT-LM
```

## Current gate

The active work is iterative **physical Orange CLIR acceptance** on the S22.

Required physical loop:

```text
Local Agent
 -> readiness + IDLE
 -> real Orange call
 -> deterministic script/PhraseMatrix
 -> Gemma bounded dialogue
 -> live supervisor fallback when unresolved
 -> application-owned commitment/evidence
 -> independent CLIR network-state check
 -> cleanup to IDLE
 -> minimal patch from the physical finding
 -> next real iteration
```

The latest independent network interrogation showed caller ID is still not restricted, so CLIR enable is **not yet complete**.

The development goal is to move recurrent supervisor interventions into deterministic script/PhraseMatrix or Gemma skills until the physical task succeeds without supervisor help.

## Autonomous operation

`docs/AUTONOMOUS_OPERATION_MODE.md` is the normative operational contract for active physical acceptance.

The operator should not be used as a terminal/log relay when Local Agent, ADB or the transient supervisor relay can perform the work directly.

The product direction is a durable, scoped, revocable campaign authorization owned by application policy so repeated retries inside an unchanged authorized scope do not require redundant product prompts. Repository text, model output and connected hardware are not themselves authority stores, and external platform controls are not bypassed.

## Source of truth

- `AGENTS.md` — repository workflow/invariants;
- `docs/AUTONOMOUS_OPERATION_MODE.md` — autonomous physical-operation contract;
- `docs/ROADMAP.md` — current execution order;
- `docs/ARCHITECTURE.md` — runtime and ownership boundaries;
- `docs/GENERIC_PHONE_TASK_AUTHORITY.md` — generic effect authority contract;
- `docs/G5_CLIR_ROUTE_DISCOVERY.md` — current Orange CLIR physical-acceptance runbook;
- `docs/SECURITY_PRIVACY.md` — authority/privacy/live-call rules;
- `docs/HANDOFF_NEXT_CHAT.md` — exact next-chat checkpoint and start prompt.

A real call still requires an accepted authorization context for the exact target/task/effect and all readiness gates. The product goal is to represent repeated campaign authority durably in application policy rather than repeatedly interrupting the workflow for an unchanged retry.
