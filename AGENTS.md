# Agent workflow

This repository is single-developer and main-first. Durable product code/docs live on `main`; `agent-control` is Local Agent task/result transport only.

## Start of every work session

Read fresh repository sources in this order:

1. `README.md`
2. `docs/HANDOFF_NEXT_CHAT.md`
3. `docs/AUTONOMOUS_OPERATION_MODE.md`
4. `docs/ROADMAP.md`
5. active domain runbook (`docs/G5_CLIR_ROUTE_DISCOVERY.md` for the current gate)
6. `docs/ARCHITECTURE.md`
7. `docs/GENERIC_PHONE_TASK_AUTHORITY.md`
8. `docs/SECURITY_PRIVACY.md`
9. `docs/PHASE2D_FREEZE_2026-09-18.md` only before Samsung media changes.

Fetch fresh `origin/main`. If Local Agent is used, read fresh daemon status and use only the binding supplied to the current chat.

`docs/AUTONOMOUS_OPERATION_MODE.md` is normative for active physical acceptance work. Do not make the operator act as a terminal/log relay when Local Agent, ADB or the transient relay can perform the step directly.

## Current priority

The product is a **generic autonomous phone task engine**. Do not create service-specific authority architectures.

Current physical acceptance sequence:

1. **G5b** — one fresh-authorized, read-only Orange CLIR route-discovery call using the existing reviewed `caller_id_restriction_info` turn followed by `OBSERVE_ONLY`.
2. **G5c** — only after route evidence is understood and authorization covers the account-changing effect, attempt `SET_SERVICE(CLIR=true)` through the existing generic authority lifecycle.

Do not return to completed G1–G4/G6 work unless a concrete regression/root cause requires it. Do not start a broad cleanup/refactor before the physical acceptance gate.

## Stable foundation

Keep closed absent a concrete root cause:

- Samsung cellular RX/TX + `CallMediaSessionCoordinator`: `PROVEN_S22 / FROZEN`;
- `privileged-helper/` / Shizuku media boundary: `PROVEN_S22 / FROZEN`;
- local STT/TTS: proven;
- IdentityVault disclosure boundary: proven;
- Gemma 4 LiteRT-LM runtime/model lifecycle: proven;
- `BOOK_APPOINTMENT` Gate D: proven;
- generic `CallExternalEffect` + single shared `CallCommitmentGate`: host/no-call proven;
- `SET_SERVICE(CLIR=true)` validator, permit lifecycle and separate external-success evidence: host/no-call proven;
- full synthetic/no-call product chain on S22: proven.

Do not redownload Gemma merely to reprove it.

## Dialogue architecture

```text
finalized STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma 4 dialogue skill
 -> supervisor fallback when unresolved
 -> application output approval
 -> TTS/TX
```

Gemma/supervisor output is proposal/dialogue data only.

During active physical acceptance, supervisor fallback should continue the same live call when possible; recurrent supervisor interventions should be moved back into deterministic script/PhraseMatrix or bounded Gemma skills.

## External-effect authority

The generic migration is already implemented:

```text
CallTask + exact target + constraints + authorized facts
 -> typed CallExternalEffect
 -> deterministic validation
 -> user-decision policy when needed
 -> one-shot CallCommitmentGate permit
 -> reviewed execution/speech
 -> exact permit consumption evidence
 -> separate external success evidence
 -> factual effect completion
 -> workflow completion
```

Keep separate:

```text
task authorization
 != effect validation
 != permit issuance
 != permit consumption
 != external success
 != task completion
```

Never create a second authority store such as `ClirCommitmentGate`.

If the current-chat instruction already exactly authorizes the same concrete effect and no material term changed, do not invent a redundant second confirmation. If new material terms appear, application-owned policy decides whether new confirmation is required.

## Authority invariants

Gemma, supervisor/ChatRelay, TaskGraph, CallPlan, PhraseMatrix, ServicePack, parsers/matchers, storage and diagnostics cannot independently own dialing, target/task/effect widening, plaintext disclosure, commitment, factual completion or speech release.

Application-owned `FactDisclosurePolicy` owns plaintext disclosure. Application output approval owns final speech release.

## Identity

Plaintext stays late-bound:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

Do not put plaintext identity in TaskGraph definitions, ServicePacks, ordinary logs/evidence, Git or model/supervisor context by default.

## Implementation discipline

- behavior changes: RED -> minimal GREEN -> regressions;
- establish root cause before touching frozen boundaries;
- prefer generic adapters over service-specific parallel paths;
- diagnostic probes are evidence drivers, not authority owners;
- `HOST_GREEN` never automatically means `PROVEN_S22`;
- route discovery is not CLIR success evidence;
- permit consumption is not external success.

For the active CLIR physical campaign, follow `docs/AUTONOMOUS_OPERATION_MODE.md`: physical calls are the acceptance loop and synthetic/unit suites must not replace physical iteration unless the operator explicitly requests them.

## Live-call policy

A connected phone, previous call, old chat, handoff, ServicePack, allowlist or `.agent/results` never authorizes dialing.

Every real call requires fresh explicit authorization in the current chat for the concrete target and task.

For current G5b:

- require live-call readiness before dial;
- require phone `IDLE`;
- read-only reviewed caller-ID information turn + `OBSERVE_ONLY` only;
- no commitment permit and no account state change;
- stop at authentication/customer-data/payment/commitment barriers;
- clean the owned call back to `IDLE`.

G5c requires authorization covering the concrete `SET_SERVICE(CLIR=true)` effect.

## Local Agent

- use only the fresh current-chat binding;
- work only in `MichalMatu/ai-calls` under this repository binding;
- inspect daemon/active-task evidence before queueing the same branch;
- direct GitHub edits for small reviewable repository changes;
- Local Agent for Gradle/Android/ADB/device/local commands;
- do not ask the operator to execute local shell commands when Local Agent can execute them;
- every task JSON must contain exactly the current binding;
- every task JSON must declare `resources` explicitly;
- never launch local Codex from Local Agent;
- `.agent/tasks` and `.agent/results` stay on `agent-control`;
- durable changes go to `main`.

## Branch policy

Normal steady state is exactly `main` + `agent-control`. Delete temporary branches after their work is merged/preserved.

## Completion / handoff gate

Before closing a scope:

1. obtain factual physical evidence appropriate to the changed boundary;
2. run independent external-state verification when technically available;
3. update authoritative docs;
4. leave `main` clean and branches minimal;
5. refresh `docs/HANDOFF_NEXT_CHAT.md`;
6. preserve the autonomous-operation contract and do not regress to operator-driven terminal work.

Do not declare a physical CLIR task complete from synthetic/no-call results, permit consumption, a model statement or call termination alone.
