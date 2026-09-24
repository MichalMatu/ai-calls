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
 -> supervisor fallback when needed
 -> application output approval
 -> TTS/TX
 -> typed external-effect authority when a real-world state change is needed
 -> factual success evidence
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

The next work is physical Orange validation, split deliberately into two steps:

1. **G5b — read-only CLIR route discovery**: fresh authorization, pre-dial readiness, reviewed caller-ID information turn, then `OBSERVE_ONLY`; no account change and no commitment permit.
2. **G5c — CLIR execution**: only after route evidence is understood, use the existing generic `CallExternalEffect.SetService(CLIR=true)` + one-shot `CallCommitmentGate` + exact external-success evidence path. Do not create Orange-specific authority.

A real CLIR account change has **not** yet been completed.

## Source of truth

- `AGENTS.md` — repository workflow/invariants;
- `docs/ROADMAP.md` — current execution order;
- `docs/ARCHITECTURE.md` — runtime and ownership boundaries;
- `docs/GENERIC_PHONE_TASK_AUTHORITY.md` — generic effect authority contract;
- `docs/G5_CLIR_ROUTE_DISCOVERY.md` — next Orange discovery gate;
- `docs/SECURITY_PRIVACY.md` — authority/privacy/live-call rules;
- `docs/HANDOFF_NEXT_CHAT.md` — exact next-chat checkpoint and start prompt.

Every real call requires fresh authorization in the current chat for the concrete target and task. Repository state, a connected phone, old evidence or a previous allowlist never authorizes dialing.