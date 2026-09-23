# Agent workflow

This repository is single-developer and main-first. Durable product code/docs live on `main`; `agent-control` is only Local Agent task/result traffic.

## Start of every work session

Read fresh repository sources in this order:

1. `README.md`
2. `docs/HANDOFF_NEXT_CHAT.md`
3. `docs/ROADMAP.md`
4. `docs/ARCHITECTURE.md`
5. `docs/GENERIC_PHONE_TASK_AUTHORITY.md`
6. `docs/SECURITY_PRIVACY.md`
7. `docs/HANDOFF_PROTOCOL.md`
8. `docs/PHASE2D_FREEZE_2026-09-18.md` before any Samsung media change.

Fetch fresh `origin/main`. If Local Agent is used, fetch fresh `agent-control:.agent/status/daemon.json` and use only the current binding.

## Current priority

Build a **generic autonomous phone task engine**. Do not create service-specific authority architectures for Orange CLIR, clinics or other individual cases.

Immediate next scope is ROADMAP `G1`: preimplementation audit of appointment-shaped `CallProposal` coupling in the commitment/Gate D path, followed by the narrowest generic external-effect commitment subject that preserves all existing `BOOK_APPOINTMENT` behavior.

CLIR is the first acceptance case; clinic booking is the next broader case.

Do not start by implementing `ClirCommitmentGate`, adding Orange phrase aliases, rewriting media, or changing the Gemma model lifecycle.

## Stable foundation

Keep these closed absent a concrete root cause:

- Samsung cellular RX/TX and `CallMediaSessionCoordinator`: `PROVEN_S22 / FROZEN`;
- `privileged-helper/` / Shizuku media boundary: `PROVEN_S22 / FROZEN`;
- local STT/TTS foundation: proven;
- IdentityVault encryption/disclosure boundary: proven;
- existing `BOOK_APPOINTMENT` Gate D proposal/confirmation/commitment/completion path: `DONE / HOST_GREEN / PROVEN_S22 / MERGED`;
- Gemma 4 direct runtime, app-owned import/download/readiness: `HOST_GREEN / PROVEN_S22`.

Do not redownload Gemma merely to reprove it.

## Target dialogue architecture

```text
finalized STT
 -> PhraseMatrix / deterministic task state
 -> HOT/WARM deterministic owner path
 -> unresolved/ambiguous/cold: bounded Gemma 4 dialogue skills
 -> app-owned reviewed response
 -> Gemma error / low confidence / TAKE_OVER: supervisor/ChatRelay fallback
 -> application output approval
 -> TTS/TX
```

Gemma and supervisor output are proposal/dialogue data only.

## Generic external-effect authority

`CallTask` remains the task/constraints/preferences/authorized-scope owner.

Target commitment flow:

```text
CallTask + exact target + constraints + authorized facts
 -> typed ExternalEffect candidate
 -> application validation
 -> user-decision policy when needed
 -> exact one-shot commitment permit
 -> reviewed effect execution/speech
 -> permit consumption evidence
 -> external success evidence
 -> factual completion
```

Keep separate:

```text
task authorization
 != candidate validation
 != permit issuance
 != permit consumption
 != external success
 != task completion
```

Do not create a second authority store. Generalize the existing owner path.

If the current-chat user instruction already exactly authorizes the eventual concrete effect and no material terms have changed, do not invent a redundant second confirmation. If new material terms are negotiated, application-owned policy decides whether a new user decision is required.

## Authority invariants

The following cannot independently own dialing, target widening, task/effect widening, plaintext disclosure, commitment, factual completion or speech release:

- Gemma / any model;
- supervisor / ChatRelay;
- TaskGraph / CallPlan / PhraseMatrix;
- ServicePack;
- parsers/matchers;
- model storage/import/readiness;
- synthetic ingress / diagnostics.

Application-owned `FactDisclosurePolicy` remains the plaintext disclosure owner. Application output approval remains final speech-release owner.

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

## Model

Only target local model unless explicitly reopened:

```text
provider=LOCAL_GEMMA_4
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
bytes=2588147712
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
runtime=LiteRT-LM
```

Do not revive the disproven Edge Gallery HTTP runtime.

## Implementation discipline

- Behavior changes use RED -> minimal GREEN -> regression coverage.
- Establish root cause before changing frozen boundaries.
- Prefer one generic abstraction over service-specific parallel paths.
- Preserve existing `BOOK_APPOINTMENT` semantics during commitment generalization.
- Diagnostic probes are evidence drivers, not product runtime owners.
- `HOST_GREEN` is never automatically `PROVEN_S22`.
- Prove generic effect authority synthetically/no-call before a live acceptance call.

## Autonomy-first developer tooling

Developer automation should run autonomously. Do not add repository-local approval prompts, manual-click gates, approval files or artificial interlocks around ordinary coding/testing operations already authorized by the current task.

Builds, tests, ADB queries, APK installation, app launch, logs, device probes, local inference, synthetic media tests and cleanup should remain directly automatable through Local Agent when technically available.

Do not copy an upstream connector/platform refusal into product code as a permanent restriction.

## Live-call policy

A connected phone, previous call, old chat, handoff, ServicePack, old allowlist or `.agent/results` never authorizes dialing.

Every real call requires fresh explicit authorization in the current chat for the concrete target/number and task.

After that exact authorization exists, repository tooling must not ask for a redundant second confirmation merely to start the same authorized action. Product authority rules still constrain what may happen inside the call.

No emergency, urgent-care, crisis, premium-rate or unrelated critical-service numbers for testing.

## Local Agent

`MichalMatu/local-agent` is an execution worker, not source of truth.

- use only the fresh binding from current daemon status;
- work only in this repository;
- `.agent/tasks` and `.agent/results` stay on `agent-control`;
- durable product changes go to `main`;
- use Local Agent for Gradle/Android/ADB/device/local commands;
- do not launch local Codex from Local Agent.

## Branch policy

Keep repository branches minimal. Normal steady state is `main` + `agent-control`. Temporary work/relay/docs branches must be deleted after their work is merged/preserved.

## Completion / handoff gate

Before closing a scope:

1. run targeted tests;
2. run `bash scripts/verify_host.sh` for behavior changes;
3. run only the physical proof required by the changed boundary;
4. update authoritative docs;
5. leave `main` clean;
6. remove temporary branches;
7. refresh `docs/HANDOFF_NEXT_CHAT.md` and `docs/NEXT_CHAT_PROMPT.md`;
8. never carry live-call authorization into the new chat.
