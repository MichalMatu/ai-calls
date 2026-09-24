# Roadmap

## Status

- `DONE` — implementation complete for stated scope.
- `HOST_GREEN` — canonical host verification passed.
- `PROVEN_S22` — physically executed successfully on the target phone.
- `FROZEN` — do not modify without a concrete root cause.

## Stable foundation

- Samsung cellular RX/TX + `CallMediaSessionCoordinator` — `PROVEN_S22 / FROZEN`.
- Shizuku / privileged media boundary — `PROVEN_S22 / FROZEN`.
- local Polish STT/TTS — proven.
- IdentityVault disclosure boundary — proven.
- Gemma 4 LiteRT-LM runtime + model lifecycle — proven.
- `BOOK_APPOINTMENT` Gate D — `DONE / HOST_GREEN / PROVEN_S22`.
- generic typed `CallExternalEffect` commitment subject — `DONE / HOST_GREEN`.
- `SET_SERVICE(CLIR=true)` validator + one-shot permit + separate external-success evidence — `DONE / HOST_GREEN`.
- full synthetic/no-call acceptance chain on S22 — `PROVEN_S22`.
- negotiated clinic booking proof on the same generic commitment store — `HOST_GREEN`.

## Autonomous operation direction

`docs/AUTONOMOUS_OPERATION_MODE.md` is normative for active physical acceptance work.

The product/development target is:

```text
Local Agent
 -> real physical task
 -> deterministic script/PhraseMatrix
 -> bounded Gemma skill
 -> live supervisor takeover only when unresolved
 -> application-owned commitment/evidence
 -> independent state verification
 -> minimal patch from physical evidence
 -> next physical iteration
```

The operator should not be used as a terminal/log relay when the system can perform the step directly. Recurrent supervisor interventions should be moved into script/PhraseMatrix or bounded Gemma skills until the physical task completes without supervisor help.

A durable, scoped, revocable campaign authorization owned by application policy is a product requirement. It should remove redundant per-retry prompts inside an unchanged authorized scope without weakening exact target/task/effect validation or external platform controls.

## Current Orange CLIR gate

### G5 prerequisite — DONE

PR #14 added fail-closed live-call readiness for `RECORD_AUDIO` + Shizuku and blocked the legacy commit-capable diagnostic path. The generic `CallExternalEffect` path remains the only valid CLIR commitment route.

### G5a / G5b discovery — COMPLETE AS HISTORICAL STEPS

The first physical read-only discovery on 2026-09-24 produced a generic Orange clarification/reprompt, not a CLIR-specific route. That result remains valid historical evidence, but it is no longer the active development loop.

### G5c physical execution — ACTIVE / NOT YET SUCCESSFUL

The live CLIR execution path is now exercised physically on the S22.

Observed real behavior and implemented responses:

- use Orange on-net `*100` for the active campaign;
- deterministic first CLIR phrase;
- deterministic second clarification phrase for Orange's generic uncertainty response;
- retry no-speech windows rather than failing immediately;
- multi-turn `script/PhraseMatrix -> Gemma -> live supervisor` fallback;
- live supervisor takeover while the cellular call remains active;
- handling of Orange's `podaj dowolny numer twojej usługi lub wprowadź go na klawiaturze` prompt with late-bound identity data;
- contextual CLIR commit recognition after route context exists;
- contextual success recognition after commitment;
- one shared `CallCommitmentGate` only;
- sanitized live report preservation before cleanup.

The most recent independent network interrogation returned:

```text
Caller ID defaults to not restricted. Next call: Not restricted
```

Therefore current factual state is:

```text
CLIR enabled = false
physical task complete = false
```

The next iteration is another real Orange call driven by the autonomous physical loop, followed by independent network-state verification.

## Acceptance condition

CLIR enable is accepted only when all of the following are true:

```text
exact authorized task/target/effect
 -> one-shot commitment permit consumed exactly once
 -> factual Orange success evidence
 -> independent network-state check confirms caller-ID restriction active
 -> owned call/session cleaned to IDLE
```

Call termination, a model statement, permit consumption or host/synthetic results are insufficient by themselves.

After successful enable acceptance, the next physical acceptance task is the inverse operation (`SET_SERVICE(CLIR=false)`) and the same loop repeats. The goal is to reduce supervisor intervention until script + Gemma complete both directions without supervisor help.

## Physical-first development rule

During the active CLIR campaign, real physical iterations are the acceptance loop. Do not substitute unit/synthetic suites for physical progress unless the operator explicitly asks for them. Build/compile/install steps needed to deploy a patch are allowed.

## Authority direction

Do not add `ClirCommitmentGate` or service-specific authority stores.

The long-term authority path remains:

```text
durable scoped campaign grant / accepted authorization context
 -> exact CallTask + target
 -> typed CallExternalEffect
 -> deterministic validation
 -> one shared CallCommitmentGate permit immediately before commitment
 -> separate factual external-success evidence
 -> independent state verification when practical
 -> workflow completion
```

A material widening of target, task, effect or account scope remains fail-closed. External platform/tool controls are not bypassed by repository documentation.

## After CLIR acceptance

Return to generic product development. Representative next cases are multi-turn negotiated tasks such as appointment availability/booking, modification and cancellation. Extend shared TaskGraph/effect adapters rather than service-specific bots.
