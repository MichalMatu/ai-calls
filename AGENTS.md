# Agent workflow

This repository is single-developer and main-first. Durable product code and current documentation live on `main`; `agent-control` is only Local Agent task/result traffic.

## Start of every work session

Read fresh repository sources in this order:

1. `README.md`
2. `docs/HANDOFF_NEXT_CHAT.md`
3. `docs/ROADMAP.md`
4. `docs/ARCHITECTURE.md`
5. `docs/SECURITY_PRIVACY.md`
6. `docs/HANDOFF_PROTOCOL.md`
7. `docs/PHASE2D_FREEZE_2026-09-18.md` before any Samsung media change
8. Orange ServicePack/runbook only if Orange-specific work is explicitly resumed.

Fetch fresh `origin/main` before writes. If Local Agent / Local Chat Bridge is used, fetch fresh `agent-control:.agent/status/daemon.json` and use only the current chat's fresh immutable binding.

Do not create a status document for every experiment. Keep durable decisions in the authoritative docs and detailed evidence in Git / `.agent/results`.

## Current priority

Gate D `BOOK_APPOINTMENT` is finished: `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.

The active work is **dialogue resilience with direct Gemma 4 local inference**, not more Orange exact-phrase scripting and not another Gate D implementation pass.

Target dialogue flow:

```text
finalized STT turn
 -> PhraseMatrix deterministic interpretation
 -> response temperature
 -> HOT/WARM: existing deterministic CallPlan/TaskGraph owner path
 -> unresolved/ambiguous/cold: bounded Gemma 4 dialogue skill classifier
 -> app-owned skill policy selects reviewed response
 -> local model failure / low confidence / TAKE_OVER: existing injected-response / ChatRelay fallback
 -> application output approval
 -> TTS/TX
```

The deterministic cellular/media path is already physically proven on S22. Do not reprove or rewrite RX/STT/TTS/TX merely because dialogue interpretation changes.

## Response temperature

`PhraseMatrix` may tolerate natural wording drift only through the bounded `PhraseResponseTemperature` contract:

```text
HOT / WARM / UNCERTAIN / COLD / AMBIGUOUS
```

- HOT = existing exact/alias/fuzzy match.
- WARM = one clear rule above threshold and margin; may route deterministically.
- UNCERTAIN = possible rule but insufficient confidence for deterministic ownership.
- AMBIGUOUS = competing near-equal candidates; fail closed.
- COLD = no useful deterministic interpretation.

Do not turn tolerance into silent guessing.

## Gemma 4 decision

For the current task, Gemma 4 is the only target local model. Do not spend work comparing/tuning Qwen unless the user explicitly reopens that scope.

Target:

```text
Gemma 4 E2B IT
gemma-4-E2B-it.litertlm
LiteRT-LM
```

The old assumption that Google AI Edge Gallery exposes an OpenAI-compatible server on `127.0.0.1:8080` was physically disproven. Do not revive that path.

Current product direction is direct in-process LiteRT-LM through `Gemma4LiteRtTextBackend` with an app-owned model file. Edge Gallery may be used only as a development source for an already downloaded model file, never as runtime authority or a required production sandbox dependency.

## Skills

Dialogue Skills are bounded model output, not authority.

The model may return structured data such as:

```text
skill_id
confidence
reason
```

The application owns the allowed skill set and exact reviewed speech for each skill. Skills never bypass `CallWorkflow`, `CallPlan`, output approval, `FactDisclosurePolicy`, `CallConfirmationPolicy`, `CallCommitmentGate` or factual completion ownership.

## Product knowledge/data layers

Keep these distinct:

- `TaskGraph` = bounded task state/transitions/slots;
- `ServicePack` = counterparty/service knowledge and evidence;
- `IdentityVault` = encrypted durable personal values;
- `DialogueState` = transient validated facts learned in the current call.

ServicePack/model/matcher confidence never creates authority.

Plaintext identity remains late-bound:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

## Authority invariants

Keep the existing owners; do not create a second authority store.

- `CallTask` owns task/constraints/preferences/authorized scope.
- `CallResolvedTarget` is concrete but cannot widen a dial allowlist.
- `CallWorkflow` owns proposal/user-decision state and terminal outcome.
- `CallConfirmationPolicy` evaluates one typed proposal.
- `CallCommitmentGate` owns one exact one-shot commitment permit.
- `FactDisclosurePolicy` owns personal-data disclosure decisions.
- application output approval owns final speech release.

Gemma, matchers, TaskGraph supervisors, ServicePacks, Skills, parsers, storage and injected-response tooling do not independently own dialing, target widening, plaintext disclosure, user confirmation, commitment, completion or speech release.

Only reviewed application paths may turn bounded candidate data into an effect.

## Frozen boundaries

- Samsung cellular RX/TX and `CallMediaSessionCoordinator`: `DONE / PROVEN_S22 / FROZEN`.
- `privileged-helper/` and proven media path: do not modify without a separate media root cause.
- Gate D proposal/confirmation/commitment/completion ownership: finished; reopen only for a proven regression.
- IdentityVault Android encryption boundary: proven; do not bypass it.
- Orange exact-phrase mappings are diagnostic fixtures, not a stable IVR API.
- Interactive ChatRelay is developer/injected-response fallback infrastructure, not product runtime authority.

## Implementation discipline

- Behavior changes use RED -> prove intended failure -> minimal GREEN -> regressions.
- Establish root cause before fixing.
- Prefer small cohesive modules over broad abstractions.
- Diagnostic probes are evidence drivers, not product runtime owners.
- Resumed speech/newer generations must invalidate stale model/candidate output.
- Host tests cannot create `PROVEN_S22` evidence.

For the current Gemma slice, first prove no-call runtime/model behavior on S22 before another live call.

## Live-call policy

A connected phone, old chat, handoff, ServicePack, previous allowlist or `.agent/results` never authorizes dialing.

Every real call requires fresh explicit authorization in the current session for one concrete target/number and one concrete task.

For test-only public business/reception calls, disclose AI/test purpose at the start and obtain consent; stop if declined. Genuine user-authorized tasks must stay within authorized facts and the normal workflow/commitment/completion owners.

Do not use emergency, crisis, urgent-care, premium-rate or unrelated critical-service numbers for testing.

## Local Agent / Local Chat Bridge

`MichalMatu/local-agent` is an execution worker, not source of truth.

- Work only in the exact bound repository.
- Never infer another repository or copy an old `agent_binding`.
- Every Local Agent task must contain the fresh current binding exactly.
- Check fresh daemon/current-task evidence before queueing or writing the same worktree.
- Do not aggressively poll healthy long tasks; require terminal evidence.
- Use direct GitHub edits for exact reviewable diffs.
- Use Local Agent for Gradle/Android/ADB/device/local commands.
- `.agent/tasks` and `.agent/results` stay on `agent-control`.
- Never launch local Codex from Local Agent.

## Branch policy

Work directly on `main` unless temporary isolation is genuinely required. Preserve only branches with intentional unique work.

## Completion / handoff gate

Before declaring a slice complete:

1. run targeted tests;
2. run `bash scripts/verify_host.sh` for code behavior;
3. run only the physical gate required by the changed device/model/dialogue boundary;
4. distinguish `HOST_GREEN` from `PROVEN_S22`;
5. update authoritative docs when the gate/continuation meaning changes;
6. leave `main` clean;
7. never carry live-call authorization into a new chat.

For a new chat, refresh `docs/HANDOFF_NEXT_CHAT.md` and `docs/NEXT_CHAT_PROMPT.md` according to `docs/HANDOFF_PROTOCOL.md`.
